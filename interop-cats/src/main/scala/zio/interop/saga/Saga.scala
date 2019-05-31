package zio.interop.saga

import cats.effect.Async
import scalaz.zio.Runtime
import zio.saga.{Saga => ZSaga}

import scala.language.higherKinds

final class Saga[+A] private (private val underlying: ZSaga[Any, Throwable, A]) {
  self =>

  def map[B](f: A => B): Saga[B] = new Saga(underlying.map(f))

  def flatMap[B](f: A => Saga[B]): Saga[B] = new Saga(underlying.flatMap(f.andThen(_.underlying)))

  def flatten[B](implicit ev: A <:< Saga[B]): Saga[B] =
    self.flatMap(r => ev(r))

  def zipPar[B](that: Saga[B]): Saga[(A, B)] =
    new Saga(underlying.zipPar(that.underlying))

  def zipWithPar[B, C](that: Saga[B])(f: (A, B) => C): Saga[C] =
    new Saga(underlying.zipWithPar(that.underlying)(f))

  def run[F[+ _]](implicit R: Runtime[Any], A: Async[F]): F[A] = A.async { cb =>
    R.unsafeRunAsync(underlying.run) { exit =>
      cb(exit.toEither)
    }
  }

}