package scalaz.zio.saga
import scalaz.zio.clock.Clock
import scalaz.zio.saga.Saga.Compensator
import scalaz.zio.{ Exit, Fiber, IO, Schedule, ZIO }

/**
 * A Saga is an immutable structure that models a distributed transaction.
 *
 * @see [[https://medium.com/@tomasz_96685/saga-pattern-and-microservices-architecture-d4b46071afcf Saga pattern]]
 *
 * Saga class has two type parameters - E for errors and A for successful result.
 * Saga wraps a ZIO that carries the compensating action in both error and result channels and enables a composition
 * with another Sagas in for-comprehensions.
 * If error occurs Saga will execute compensating actions starting from action that corresponds to failed request
 * till the first already completed request.
 * */
final case class Saga[+E, +A] private (
  private val request: ZIO[Any with Clock, (E, Compensator[Any, E]), (A, Compensator[Any, E])]
) extends AnyVal {
  self =>

  /**
   * Maps the resulting value `A` of this Saga to value `B` with function `f`.
   * */
  def map[B](f: A => B): Saga[E, B] =
    Saga(request.map { case (a, comp) => (f(a), comp) })

  /**
   * Sequences the result of this Saga to the next Saga.
   * */
  def flatMap[E1 >: E, B](f: A => Saga[E1, B]): Saga[E1, B] =
    Saga(request.flatMap {
      case (a, compA) =>
        tapError(f(a).request)({ case (e, compB) => compB.mapError(_ => (e, IO.unit)) }).mapError {
          case (e, _) => (e, compA)
        }
    })

  /**
   * Flattens the structure of this Saga by executing outer Saga first and then executes inner Saga.
   * */
  def flatten[E1 >: E, B](implicit ev: A <:< Saga[E1, B]): Saga[E1, B] =
    self.flatMap(r => ev(r))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result in a tuple.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipPar[E1 >: E, B](that: Saga[E1, B]): Saga[E1, (A, B)] =
    zipWithPar(that)((a, b) => (a, b))

  /**
   * Returns Saga that will execute this Saga in parallel with other, combining the result with specified function `f`.
   * Both compensating actions would be executed in case of failure.
   * */
  def zipWithPar[E1 >: E, B, C](that: Saga[E1, B])(f: (A, B) => C): Saga[E1, C] = {
    def coordinate[A1, B1, C1](f: (A1, B1) => C1)(
      fasterSaga: Exit[(E1, Compensator[Any, E1]), (A1, Compensator[Any, E1])],
      slowerSaga: Fiber[(E1, Compensator[Any, E1]), (B1, Compensator[Any, E1])]
    ): ZIO[Any, (E1, Compensator[Any, E1]), (C1, Compensator[Any, E1])] =
      fasterSaga match {
        case Exit.Success((a, compA)) =>
          slowerSaga.join.bimap({ case (e, compB) => (e, compB *> compA) }, {
            case (b, compB)                       => (f(a, b), compB *> compA)
          })
        case Exit.Failure(cause) =>
          slowerSaga.interrupt.flatMap {
            case Exit.Success((a, compA)) =>
              ZIO.halt(cause.map { case (e, compB) => (e, compB *> compA) })
            case Exit.Failure(loserCause) =>
              val (_, compA) = cause.failures.head
              val (_, compB) = loserCause.failures.head
              val combined   = compB *> compA
              ZIO.halt((cause && loserCause).map { case (e, _) => (e, combined) })
          }
      }
    val g = (b: B, a: A) => f(a, b)
    Saga(request.raceWith(that.request)(coordinate(f), coordinate(g)))
  }

  /**
   * Materializes this Saga to ZIO effect.
   * */
  def run: ZIO[Any with Clock, E, A] =
    tapError(request)({ case (e, compA) => compA.mapError(_ => (e, IO.unit)) }).bimap(_._1, _._1)

  //backported from ZIO version after 1.0-RC4
  private def tapError[R, E1, E2 >: E1, B](zio: ZIO[R, E1, B])(f: E1 => ZIO[R, E2, _]): ZIO[R, E2, B] =
    zio.foldM(
      e => f(e) *> ZIO.fail(e),
      ZIO.succeed
    )
}

object Saga {

  type Compensator[-R, +E] = ZIO[R with Clock, E, Unit]

  /**
   * Constructs new Saga from action and compensating action.
   * */
  def compensate[R >: Any, E, A](request: IO[E, A], compensator: Compensator[R, E]): Saga[E, A] =
    Saga(request.bimap((_, compensator), (_, compensator)))

  /**
   * Constructs new Saga from action, compensating action and a scheduling policy for retrying compensation.
   * */
  def retryableCompensate[R >: Any, E, A](
    request: IO[E, A],
    compensator: Compensator[R, E],
    schedule: Schedule[E, Any]
  ): Saga[E, A] = {
    val retry: Compensator[R, E] = compensator.retry(schedule.unit)
    compensate(request, retry)
  }

  /**
   * Constructs new `no-op` Saga that will do nothing on error.
   * */
  def noCompensate[E, A](request: IO[E, A]): Saga[E, A] = compensate(request, ZIO.unit)

  /**
   * Extension methods for IO requests.
   * */
  implicit class Compensable[+E, +A](val request: IO[E, A]) extends AnyVal {
    def compensate[R >: Any, E1 >: E](c: Compensator[R, E1]): Saga[E1, A] = Saga.compensate(request, c)

    def retryableCompensate[R >: Any, E1 >: E](c: Compensator[R, E1], schedule: Schedule[E1, Any]): Saga[E1, A] =
      Saga.retryableCompensate(request, c, schedule)

    def noCompensate: Saga[E, A] = Saga.noCompensate(request)
  }

}
