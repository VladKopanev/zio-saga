package com.vladkopanev.cats.saga

import cats._
import cats.effect.Concurrent
import cats.implicits._
import com.vladkopanev.cats.saga.Saga.{ FlatMap, Par, Step, Suceeded }

import scala.util.control.NonFatal

sealed abstract class Saga[F[_], A] {

  def flatMap[B](f: A => Saga[F, B]): Saga[F, B] =
    Saga.FlatMap(this, a => f(a))

  def map[B](f: A => B): Saga[F, B] =
    flatMap(a => Saga.Suceeded(f(a)))

  def flatten[B](implicit ev: A <:< Saga[F, B]): Saga[F, B] =
    flatMap(ev)

  def transact(implicit F: Concurrent[F]): F[A] = {
    def interpret[X](saga: Saga[F, X]): F[(X, F[Unit])] = saga match {
      case Suceeded(value) => F.pure((value, F.unit))
      case Step(action, compensator) =>
        action.map(x => (x, compensator(Right(x)))).onError {
          case NonFatal(ex) => compensator(Left(ex))
        }
      case FlatMap(chained: Saga[F, Any], continuation: (Any => Saga[F, X])) =>
        interpret(chained).flatMap {
          case (v, prevStepCompensator) =>
            interpret(continuation(v)).onError {
              case NonFatal(ex) => prevStepCompensator
            }
        }
      case Par(left: Saga[F, Any], right: Saga[F, Any], combine: ((Any, Any) => X)) =>
        F.racePair(interpret(left), interpret(right)).flatMap {
          case Left(((a, aComp), fiberB))  => fiberB.join.map { case (b, bComp) => (combine(a, b), bComp *> aComp) }
          case Right((fiberA, (b, bComp))) => fiberA.join.map { case (a, aComp) => (combine(a, b), aComp *> bComp) }
        }
    }

    interpret(this).map(_._1)
  }

  def zipPar[B](that: Saga[F, B]): Saga[F, (A, B)] =
    zipWithPar(that)((_, _))

  def zipWithPar[B, C](that: Saga[F, B])(f: (A, B) => C): Saga[F, C] =
    Saga.Par(this, that, f)
}

object Saga {

  private case class Suceeded[F[_], A](value: A)                                              extends Saga[F, A]
  private case class Step[F[_], A](action: F[A], compensate: Either[Throwable, A] => F[Unit]) extends Saga[F, A]
  private case class FlatMap[F[_], A, B](fa: Saga[F, A], f: A => Saga[F, B])                  extends Saga[F, B]
  private case class Par[F[_], A, B, C](fa: Saga[F, A], fb: Saga[F, B], combine: (A, B) => C) extends Saga[F, C]

  def compensate[F[_], A](comp: F[A], compensation: F[Unit]): Saga[F, A] =
    compensate(comp, _ => compensation)

  def compensate[F[_], A](comp: F[A], compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
    Step(comp, compensation)

  def compensateIfFail[F[_], A](comp: F[A], compensation: Throwable => F[Unit])(F: InvariantMonoidal[F]): Saga[F, A] =
    compensate(comp, result => result.fold(compensation, _ => F.unit))

  def compensateIfSuccess[F[_], A](comp: F[A], compensation: A => F[Unit])(F: InvariantMonoidal[F]): Saga[F, A] =
    compensate(comp, result => result.fold(_ => F.unit, compensation))

  /**
   * Runs all Sagas in iterable in parallel and collects
   * the results.
   */
  def collectAllPar[F[_]: Applicative, A](sagas: Iterable[Saga[F, A]]): Saga[F, List[A]] =
    foreachPar[F, Saga[F, A], A](sagas)(identity)

  /**
   * Runs all Sagas in iterable in parallel, and collect
   * the results.
   */
  def collectAllPar[F[_]: Applicative, A](saga: Saga[F, A], rest: Saga[F, A]*): Saga[F, List[A]] =
    collectAllPar(saga +: rest)

  /**
   * Constructs Saga without compensation that fails with an error.
    **/
  def fail[F[_], A](error: Throwable)(implicit F: MonadError[F, Throwable]): Saga[F, A] =
    noCompensate(F.raiseError(error))

  /**
   * Constructs a Saga that applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   */
  def foreachPar[F[_], A, B](as: Iterable[A])(fn: A => Saga[F, B])(implicit F: Applicative[F]): Saga[F, List[B]] =
    as.foldRight[Saga[F, List[B]]](Saga.noCompensate(F.pure(Nil))) { (a, io) =>
      fn(a).zipWithPar(io)((b, bs) => b :: bs)
    }

  def noCompensate[F[_], A](comp: F[A])(implicit F: InvariantMonoidal[F]): Saga[F, A] =
    Step(comp, _ => F.unit)

  def succeed[F[_], A](value: A): Saga[F, A] =
    Suceeded(value)

  implicit class Compensable[F[_], A](val request: F[A]) {

    def compensate(compensator: F[Unit]): Saga[F, A] = Saga.compensate(request, compensator)

    def compensate(compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
      Saga.compensate(request, compensation)

    def compensateIfFail(compensation: Throwable => F[Unit])(F: InvariantMonoidal[F]): Saga[F, A] =
      Saga.compensate(request, result => result.fold(compensation, _ => F.unit))

    def compensateIfSuccess(compensation: A => F[Unit])(F: InvariantMonoidal[F]): Saga[F, A] =
      Saga.compensate(request, result => result.fold(_ => F.unit, compensation))

    def noCompensate(implicit F: InvariantMonoidal[F]): Saga[F, A] = Saga.noCompensate(request)

  }

  implicit def monad[F[_]]: Monad[Saga[F, ?]] = new Monad[Saga[F, ?]] {
    override def pure[A](x: A): Saga[F, A] = Saga.succeed(x)

    override def flatMap[A, B](fa: Saga[F, A])(f: A => Saga[F, B]): Saga[F, B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => Saga[F, Either[A, B]]): Saga[F, B] = flatMap(f(a)) {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => pure(b)
    }
  }
}
