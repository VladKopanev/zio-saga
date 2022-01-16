package com.vladkopanev.zio.saga

import com.vladkopanev.zio.saga.Saga.Compensator
import zio.Clock
import zio.{Cause, Exit, Fiber, IO, RIO, Schedule, Task, UIO, ZIO}

/**
 * A Saga is an immutable structure that models a distributed transaction.
 *
 * @see [[https://blog.couchbase.com/saga-pattern-implement-business-transactions-using-microservices-part/ Saga pattern]]
 *
 * Saga class has three type parameters - R for environment, E for errors and A for successful result.
 * Saga wraps a ZIO that carries the compensating action in both error and result channels and enables a composition
 * with another Sagas in for-comprehensions.
 * If error occurs Saga will execute compensating actions starting from action that corresponds to failed request
 * till the first already completed request.
 * */
final class Saga[-R, +E, +A] private (
  private val request: ZIO[R, (E, Compensator[R, E]), (A, Compensator[R, E])]
) extends AnyVal {
  self =>

  /**
   * Maps the resulting value `A` of this Saga to value `B` with function `f`.
   * */
  def map[B](f: A => B): Saga[R, E, B] =
    new Saga(request.map { case (a, comp) => (f(a), comp) })

  /**
   * Sequences the result of this Saga to the next Saga.
   * */
  def flatMap[R1 <: R, E1 >: E, B](f: A => Saga[R1, E1, B]): Saga[R1, E1, B] =
    new Saga(request.flatMap {
      case (a, compA) =>
        f(a).request.mapBoth(
          { case (e, compB) => (e, compB *> compA) },
          { case (r, compB) => (r, compB *> compA) }
        )
    })

  /**
   * Flattens the structure of this Saga by executing outer Saga first and then executes inner Saga.
   * */
  def flatten[R1 <: R, E1 >: E, B](implicit ev: A <:< Saga[R1, E1, B]): Saga[R1, E1, B] =
    self.flatMap(r => ev(r))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result in a tuple.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipPar[R1 <: R, E1 >: E, B](that: Saga[R1, E1, B]): Saga[R1, E1, (A, B)] =
    zipWithPar(that)((a, b) => (a, b))

  /**
   * Returns Saga that will execute this Saga in sequence with other, combining the result in a tuple.
   * Both compensating actions would be executed in case of failure.
   * */
  def zip[R1 <: R, E1 >: E, B](that: Saga[R1, E1, B]): Saga[R1, E1, (A, B)] =
    zipWith(that)((a, b) => (a, b))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result with specified function `f`.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipWithPar[R1 <: R, E1 >: E, B, C](that: Saga[R1, E1, B])(f: (A, B) => C): Saga[R1, E1, C] =
    zipWithParAll(that)(f)((a, b) => a *> b)

  /**
   * Returns Saga that will execute this Saga in sequence with other, combining the result with specified function `f`.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipWith[R1 <: R, E1 >: E, B, C](that: Saga[R1, E1, B])(f: (A, B) => C): Saga[R1, E1, C] = (for {
    thisResult <- this
    thatResult <- that
  } yield f(thisResult, thatResult))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result with specified function `f`
   * and combining the compensating actions with function `g` (this allows user to choose a strategy of running both
   * compensating actions e.g. in sequence or in parallel).
   * */
  def zipWithParAll[R1 <: R, E1 >: E, B, C](
    that: Saga[R1, E1, B]
  )(f: (A, B) => C)(g: (Compensator[R1, E1], Compensator[R1, E1]) => Compensator[R1, E1]): Saga[R1, E1, C] = {
    def coordinate[A1, B1, C1](f: (A1, B1) => C1)(
      fasterSaga: Exit[(E1, Compensator[R1, E1]), (A1, Compensator[R1, E1])],
      slowerSaga: Fiber[(E1, Compensator[R1, E1]), (B1, Compensator[R1, E1])]
    ): ZIO[R1, (E1, Compensator[R1, E1]), (C1, Compensator[R1, E1])] =
      fasterSaga.foldZIO(
        { cause =>
          def extractCompensatorFrom(c: Cause[(E1, Compensator[R1, E1])]): Compensator[R1, E1] =
            c.failures.headOption.map[Compensator[R1, E1]](_._2).getOrElse(ZIO.dieMessage("Compensator was lost"))
          /* we can't use interrupt here because we won't get a compensation action in case
           IO was still running and interrupted */
          slowerSaga.await.flatMap(
            _.foldZIO(
              { loserCause =>
                val compA = extractCompensatorFrom(cause)
                val compB = extractCompensatorFrom(loserCause)
                ZIO.failCause((cause && loserCause).map { case (e, _) => (e, g(compB, compA)) })
              }, { case (_, compB) => ZIO.failCause(cause.map { case (e, compA) => (e, g(compB, compA)) }) }
            )
          )
        }, {
          case (a, compA) =>
            slowerSaga.join.mapBoth(
              { case (e, compB) => (e, g(compB, compA)) },
              { case (b, compB) => (f(a, b), g(compB, compA)) }
            )
        }
      )

    val h = (b: B, a: A) => f(a, b)
    new Saga(request.raceWith(that.request)(coordinate(f), coordinate(h)))
  }

  /**
   * Materializes this Saga to ZIO effect.
   * */
  def transact: ZIO[R, E, A] =
    request.tapError({ case (e, compA) => compA.orElseFail((e, IO.unit)) }).mapBoth(_._1, _._1)
}

object Saga {

  type Compensator[-R, +E] = ZIO[R, E, Unit]

  /**
   * Constructs new Saga from action and compensating action.
   * */
  def compensate[R, E, A](request: ZIO[R, E, A], compensator: Compensator[R, E]): Saga[R, E, A] =
    compensate(request, (_: Either[E, A]) => compensator)

  /**
   * Constructs new Saga from action and compensation function that will be applied the result of this request.
   * */
  def compensate[R, E, A](action: ZIO[R, E, A], compensation: Either[E, A] => Compensator[R, E]): Saga[R, E, A] =
    new Saga(action.mapBoth(e => (e, compensation(Left(e))), a => (a, compensation(Right(a)))))

  /**
   * Constructs new Saga from action and compensation function that will be applied only to failed result of this request.
   * If given action succeeds associated compensating action would not be executed during the compensation phase.
   * */
  def compensateIfFail[R, E, A](action: ZIO[R, E, A], compensation: E => Compensator[R, E]): Saga[R, E, A] =
    compensate(action, (result: Either[E, A]) => result.fold(compensation, _ => ZIO.unit))

  /**
   * Constructs new Saga from action and compensation function that will be applied only to successful result of this request.
   * If given action fails associated compensating action would not be executed during the compensation phase.
   * */
  def compensateIfSuccess[R, E, A](action: ZIO[R, E, A], compensation: A => Compensator[R, E]): Saga[R, E, A] =
    compensate(action, (result: Either[E, A]) => result.fold(_ => ZIO.unit, compensation))

  /**
   * Runs all Sagas in iterable in parallel and collects
   * the results.
   */
  def collectAllPar[R, E, A](sagas: Iterable[Saga[R, E, A]]): Saga[R, E, List[A]] =
    foreachPar[R, E, Saga[R, E, A], A](sagas)(identity)

  /**
   * Runs all Sagas in iterable in sequence and collects the results.
   */
  def collectAll[R, E, A](sagas: Iterable[Saga[R, E, A]]): Saga[R, E, List[A]] =
    foreach[R, E, Saga[R, E, A], A](sagas)(identity)

  /**
   * Runs all Sagas in iterable in parallel, and collect
   * the results.
   */
  def collectAllPar[R, E, A](sagas: Saga[R, E, A]*): Saga[R, E, List[A]] =
    collectAllPar(sagas)

  /**
   * Constructs Saga without compensation that fails with an error.
   * */
  def fail[R, E](error: E): Saga[R, E, Nothing] = noCompensate(ZIO.fail(error))

  /**
   * Constructs a Saga that applies the function `f` to each element of the `Iterable[A]` in parallel,
   * and returns the results in a new `List[B]`.
   *
   */
  def foreachPar[R, E, A, B](as: Iterable[A])(fn: A => Saga[R, E, B]): Saga[R, E, List[B]] =
    as.foldRight[Saga[R, E, List[B]]](Saga.noCompensate(IO.succeed(Nil))) { (a, io) =>
      fn(a).zipWithPar(io)((b, bs) => b :: bs)
    }

  /**
   * Constructs a Saga that applies the function `f` to each element of the `Iterable[A]` in sequence, and returns the
   * results in a new `List[B]`.
   */
  def foreach[R, E, A, B](as: Iterable[A])(fn: A => Saga[R, E, B]): Saga[R, E, List[B]] =
    as.foldRight[Saga[R, E, List[B]]](Saga.noCompensate(IO.succeed(Nil))) { (a, io) =>
      fn(a).zipWith(io)((b, bs) => b :: bs)
    }

  /**
   * Constructs new `no-op` Saga that will do nothing on error.
   * */
  def noCompensate[R, E, A](request: ZIO[R, E, A]): Saga[R, E, A] = compensate(request, ZIO.unit)

  /**
   * Constructs new Saga from action, compensating action and a scheduling policy for retrying compensation.
   * */
  def retryableCompensate[R, SR, E, A](
    request: ZIO[R, E, A],
    compensator: Compensator[R, E],
    schedule: Schedule[SR, E, Any]
  ): Saga[R with SR with Clock, E, A] = {
    val retry: Compensator[R with SR with Clock, E] = compensator.retry(schedule.unit)
    compensate(request, retry)
  }

  /**
   * Constructs Saga without compensation that succeeds with a strict value.
   * */
  def succeed[R, A](value: A): Saga[R, Nothing, A] = noCompensate(ZIO.succeed(value))

  // $COVERAGE-OFF$
  implicit def IOtoCompensable[E, A](io: IO[E, A]): Compensable[Any, E, A] = new Compensable(io)

  implicit def ZIOtoCompensable[R, E, A](zio: ZIO[R, E, A]): Compensable[R, E, A] = new Compensable(zio)

  implicit def UIOtoCompensable[A](uio: UIO[A]): Compensable[Any, Nothing, A] = new Compensable(uio)

  implicit def RIOtoCompensable[R, A](rio: RIO[R, A]): Compensable[R, Throwable, A] = new Compensable(rio)

  implicit def TaskToCompensable[A](task: Task[A]): Compensable[Any, Throwable, A] = new Compensable(task)
  // $COVERAGE-ON$

  /**
   * Extension methods for IO requests.
   * */
  class Compensable[-R, +E, +A](val request: ZIO[R, E, A]) extends AnyVal {

    def compensate[R1 <: R, E1 >: E](c: Compensator[R1, E1]): Saga[R1, E1, A] =
      Saga.compensate(request, c)

    def compensate[R1 <: R, E1 >: E](compensation: Either[E1, A] => Compensator[R1, E1]): Saga[R1, E1, A] =
      Saga.compensate(request, compensation)

    def compensateIfFail[R1 <: R, E1 >: E](compensation: E1 => Compensator[R1, E1]): Saga[R1, E1, A] =
      Saga.compensateIfFail(request, compensation)

    def compensateIfSuccess[R1 <: R, E1 >: E](compensation: A => Compensator[R1, E1]): Saga[R1, E1, A] =
      Saga.compensateIfSuccess(request, compensation)

    def retryableCompensate[R1 <: R, SR, E1 >: E](
      c: Compensator[R1, E1],
      schedule: Schedule[SR, E1, Any]
    ): Saga[R1 with SR with Clock, E1, A] =
      Saga.retryableCompensate(request, c, schedule)

    def noCompensate: Saga[R, E, A] = Saga.noCompensate(request)
  }

}
