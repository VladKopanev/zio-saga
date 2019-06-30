package com.vladkopanev.cats.saga

import cats._
import cats.implicits._
import Saga.{ FlatMap, Step, Suceeded }

import scala.util.control.NonFatal

sealed abstract class Saga[F[_], A] {

  def flatMap[B](f: A => Saga[F, B]): Saga[F, B] =
    Saga.FlatMap(this, a => f(a))

  def map[B](f: A => B): Saga[F, B] =
    Saga.FlatMap(this, (a: A) => Saga.Suceeded(f(a)))

  def flatten[B](implicit ev: A <:< Saga[F, B]): Saga[F, B] =
    Saga.FlatMap(this, a => ev(a))

  def transact(implicit F: MonadError[F, Throwable]): F[A] = {
    def interpret[X](saga: Saga[F, X]): F[(X, F[Unit])] = saga match {
      case Suceeded(value) => F.pure((value, F.unit))
      case Step(action, compensator) =>
        action.map(x => (x, compensator(Right(x)))).onError {
          case NonFatal(ex) => compensator(Left(ex))
        }
      case FlatMap(chained: Saga[F, Any], continuation: (Any => Saga[F, X])) =>
        interpret(chained).flatMap {
          case (v, prevStepCompensator) => interpret(continuation(v)).onError {
            case NonFatal(ex) => prevStepCompensator
          }
        }
    }

    interpret(this).map(_._1)
  }
}

object Saga {

  private case class Suceeded[F[_], A](value: A) extends Saga[F, A]
  private case class Step[F[_], A](action: F[A], compensate: Either[Throwable, A] => F[Unit]) extends Saga[F, A]
  private case class FlatMap[F[_], A, B](fa: Saga[F, A], f: A => Saga[F, B]) extends Saga[F, B]

  def succeed[F[_], A](value: A): Saga[F, A] =
    Suceeded(value)

  def compensate[F[_], A](comp: F[A], compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
    Step(comp, compensation)

  def compensate[F[_], A](comp: F[A], compensation: F[Unit]): Saga[F, A] =
    Step(comp, _ => compensation)

  def noCompensate[F[_], A](comp: F[A])(implicit F: InvariantMonoidal[F]): Saga[F, A] =
    Step(comp, _ => F.unit)

  implicit class Compensable[F[_], A](val request: F[A]) {

    def compensate(compensator: F[Unit]): Saga[F, A] = Saga.compensate(request, compensator)

    def noCompensate(implicit F: InvariantMonoidal[F]): Saga[F, A] = Saga.noCompensate(request)

    def compensate(compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
      Saga.compensate(request, compensation)
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
