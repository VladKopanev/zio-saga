package zio.saga

import scalaz.zio.Exit.Cause
import scalaz.zio.clock.Clock
import Saga.Compensator
import scalaz.zio.{ Exit, Fiber, IO, Schedule, ZIO }

/**
 * A Saga is an immutable structure that models a distributed transaction.
 *
 * @see [[https://medium.com/@tomasz_96685/saga-pattern-and-microservices-architecture-d4b46071afcf Saga pattern]]
 *
 * Saga class has three type parameters - R for environment, E for errors and A for successful result.
 * Saga wraps a ZIO that carries the compensating action in both error and result channels and enables a composition
 * with another Sagas in for-comprehensions.
 * If error occurs Saga will execute compensating actions starting from action that corresponds to failed request
 * till the first already completed request.
 * */
final case class Saga[-R, -RC, +E, +A] private (
  private val request: ZIO[R, (E, Compensator[RC, E]), (A, Compensator[RC, E])]
) extends AnyVal {
  self =>

  /**
   * Maps the resulting value `A` of this Saga to value `B` with function `f`.
   * */
  def map[B](f: A => B): Saga[R, RC, E, B] =
    Saga(request.map { case (a, comp) => (f(a), comp) })

  /**
   * Sequences the result of this Saga to the next Saga.
   * */
  def flatMap[R1 <: R, RC1 <: RC, E1 >: E, B](f: A => Saga[R1, RC1, E1, B]): Saga[R1 with RC1, RC1, E1, B] =
    Saga(request.flatMap {
      case (a, compA) =>
        tapError(f(a).request)({ case (e, compB) => compB.mapError(_ => (e, IO.unit)) }).mapError {
          case (e, _) => (e, compA)
        }
    })

  /**
   * Flattens the structure of this Saga by executing outer Saga first and then executes inner Saga.
   * */
  def flatten[R1 <: R, RC1 <: RC, E1 >: E, B](implicit ev: A <:< Saga[R1, RC1, E1, B]): Saga[R1 with RC1, RC1, E1, B] =
    self.flatMap(r => ev(r))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result in a tuple.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipPar[R1 <: R, RC1 <: RC, E1 >: E, B](that: Saga[R1, RC1, E1, B]): Saga[R1, RC1, E1, (A, B)] =
    zipWithPar(that)((a, b) => (a, b))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result with specified function `f`.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipWithPar[R1 <: R, RC1 <: RC, E1 >: E, B, C](that: Saga[R1, RC1, E1, B])(f: (A, B) => C): Saga[R1, RC1, E1, C] = {
    def coordinate[A1, B1, C1](f: (A1, B1) => C1)(
      fasterSaga: Exit[(E1, Compensator[RC1, E1]), (A1, Compensator[RC1, E1])],
      slowerSaga: Fiber[(E1, Compensator[RC1, E1]), (B1, Compensator[RC1, E1])]
    ): ZIO[R1, (E1, Compensator[RC1, E1]), (C1, Compensator[RC1, E1])] =
      fasterSaga match {
        case Exit.Success((a, compA)) =>
          slowerSaga.join.bimap({ case (e, compB) => (e, compB *> compA) }, {
            case (b, compB)                       => (f(a, b), compB *> compA)
          })
        case Exit.Failure(cause) =>
          def extractCompensatorFrom(c: Cause[(E1, Compensator[RC1, E1])]): Compensator[RC1, E1] =
            c.failures.headOption.map[Compensator[RC1, E1]](_._2).getOrElse(ZIO.dieMessage("Compensator was lost"))
          //TODO we can't use interrupt here because we won't get a compensation action in case
          //IO was still running and interrupted
          slowerSaga.await.flatMap {
            case Exit.Success((_, compB)) =>
              ZIO.halt(cause.map { case (e, compA) => (e, compB *> compA) })
            case Exit.Failure(loserCause) =>
              val compA    = extractCompensatorFrom(cause)
              val compB    = extractCompensatorFrom(loserCause)
              val combined = compB *> compA
              ZIO.halt((cause && loserCause).map { case (e, _) => (e, combined) })
          }
      }
    val g = (b: B, a: A) => f(a, b)
    Saga(request.raceWith(that.request)(coordinate(f), coordinate(g)))
  }

  /**
   * Materializes this Saga to ZIO effect.
   * */
  def run[R1 <: R, RC1 <: RC, E1 >: E]: ZIO[R1 with RC1, E1, A] =
    tapError(request)({ case (e, compA) => compA.mapError(_ => (e, IO.unit)) }).bimap(_._1, _._1)

  //backported from ZIO version after 1.0-RC4
  private def tapError[R1, R2 <: R1, E1, E2 >: E1, B](zio: ZIO[R1, E1, B])(f: E1 => ZIO[R2, E2, _]): ZIO[R2, E2, B] =
    zio.foldM(
      e => f(e) *> ZIO.fail(e),
      ZIO.succeed
    )
}

object Saga {

  type Compensator[-R, +E] = ZIO[R, E, Unit]

  /**
   * Constructs new Saga from action and compensating action.
   * */
  def compensate[R, RC, E, A](request: ZIO[R, E, A], compensator: Compensator[RC, E]): Saga[R, RC, E, A] =
    Saga(request.bimap((_, compensator), (_, compensator)))

  /**
   * Runs all Sagas in iterable in parallel and collects
   * the results.
   */
  def collectAllPar[R, RC, E, A](sagas: Iterable[Saga[R, RC, E, A]]): Saga[R, RC, E, List[A]] =
    foreachPar[R, RC, E, Saga[R, RC, E, A], A](sagas)(identity)

  /**
   * Runs all Sagas in iterable in parallel, and collect
   * the results.
   */
  def collectAllPar[R, RC, E, A](sagas: Saga[R, RC, E, A]*): Saga[R, RC, E, List[A]] =
    collectAllPar(sagas)

  /**
   * Constructs a Saga that applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   */
  def foreachPar[R, RC, E, A, B](as: Iterable[A])(fn: A => Saga[R, RC, E, B]): Saga[R, RC, E, List[B]] =
    as.foldRight[Saga[R, RC, E, List[B]]](Saga.noCompensate(IO.effectTotal(Nil))) { (a, io) =>
      fn(a).zipWithPar(io)((b, bs) => b :: bs)
    }

  /**
   * Constructs new `no-op` Saga that will do nothing on error.
   * */
  def noCompensate[R, E, A](request: ZIO[R, E, A]): Saga[R, Any, E, A] = compensate(request, ZIO.unit)

  /**
   * Constructs new Saga from action, compensating action and a scheduling policy for retrying compensation.
   * */
  def retryableCompensate[R, RC, E, A](
    request: ZIO[R, E, A],
    compensator: Compensator[RC, E],
    schedule: Schedule[E, Any]
  ): Saga[R, RC with Clock, E, A] = {
    val retry: Compensator[RC with Clock, E] = compensator.retry(schedule.unit)
    compensate(request, retry)
  }

  /**
   * Extension methods for IO requests.
   * */
  implicit class Compensable[R, E, A](val request: ZIO[R, E, A]) extends AnyVal {
    def compensate[RC, E1 >: E](c: Compensator[RC, E1]): Saga[R, RC, E1, A] = Saga.compensate(request, c)

    def retryableCompensate[RC, E1 >: E](
      c: Compensator[RC, E1],
      schedule: Schedule[E1, Any]
    ): Saga[R, RC with Clock, E1, A] =
      Saga.retryableCompensate(request, c, schedule)

    def noCompensate: Saga[R, Any, E, A] = Saga.noCompensate(request)
  }

}
