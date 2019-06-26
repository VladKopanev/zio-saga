package zio.saga.cats

import cats._
import cats.implicits._
import zio.saga.cats.Saga.{FlatMap, Step, Suceeded}

import scala.util.control.NonFatal


sealed abstract class Saga[F[_], A] {

  def flatMap[B](f: A => Saga[F, B]): Saga[F, B] =
    Saga.FlatMap(this, a => f(a))

  def map[B](f: A => B): Saga[F, B] =
    Saga.FlatMap(this, (a: A) => Saga.Suceeded(f(a)))

  def transact(implicit F: MonadError[F, Throwable]): F[A] = {
    def interpret[X](saga: Saga[F, X], compensations: List[F[Unit]]): F[(X, List[F[Unit]])] = saga match {
      case Suceeded(value) => F.pure((value, List.empty))
      case Step(action, compensator) =>
        action.map(x => (x, compensator(Right(x)) :: compensations)).onError {
          case Saga.Halted(_) => F.unit
          case NonFatal(ex) => compensator(Left(ex)) *> F.raiseError(Saga.Halted(ex))
        }
      case FlatMap(chained: Saga[F, Any], continuation: Function1[Any, Saga[F, X]]) =>
        interpret(chained, compensations).flatMap {
          case (v, acc) => interpret(continuation(v), acc).onError {
            case Saga.Halted(_) => F.unit
            case NonFatal(ex) => acc.sequence *> F.raiseError(Saga.Halted(ex))
          }
        }
    }

    interpret(this, List.empty).map(_._1)
  }
}

object Saga {

  final case class Halted(throwable: Throwable) extends Throwable

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

  implicit def monad[F[_]]: Monad[Saga[F, ?]] = new Monad[Saga[F, ?]] {
    override def pure[A](x: A): Saga[F, A] = Saga.succeed(x)

    override def flatMap[A, B](fa: Saga[F, A])(f: A => Saga[F, B]): Saga[F, B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => Saga[F, Either[A, B]]): Saga[F, B] = flatMap(f(a)) {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => pure(b)
    }
  }
}
