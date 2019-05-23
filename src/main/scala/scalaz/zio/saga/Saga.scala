package scalaz.zio.saga
import scalaz.zio.clock.Clock
import scalaz.zio.saga.Saga.Compensator
import scalaz.zio.{ IO, Schedule, ZIO }

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
   * Maps the resulting value `A` of this saga to value `B` with function `f`.
   * */
  def map[B](f: A => B): Saga[E, B] =
    Saga(request.map { case (a, comp) => (f(a), comp) })

  /**
   * Sequences the result of this saga to the next saga.
   * */
  def flatMap[E1 >: E, B](f: A => Saga[E1, B]): Saga[E1, B] =
    Saga(request.flatMap {
      case (a, compA) =>
        tapError(f(a).request)({ case (e, compC) => compC.mapError(_ => (e, IO.unit)) }).mapError {
          case (e, _) => (e, compA)
        }
    })

  /**
   * Flattens the structure of this saga by executing outer saga first and then executes inner saga.
   * */
  def flatten[E1 >: E, B](implicit ev: A <:< Saga[E1, B]): Saga[E1, B] =
    self.flatMap(r => ev(r))

  /**
   * Materializes this saga to ZIO effect.
   * */
  def run: ZIO[Any with Clock, E, A] =
    tapError(request)({ case (e, compC) => compC.mapError(_ => (e, IO.unit)) }).bimap(_._1, _._1)

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
   * Constructs new `no-op` saga that will do nothing on error.
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
