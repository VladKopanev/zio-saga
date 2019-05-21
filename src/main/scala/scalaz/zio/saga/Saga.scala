package scalaz.zio.saga
import scalaz.zio.{ IO, Schedule, ZIO }
import scalaz.zio.clock.Clock
import scalaz.zio.saga.Saga.Compensator

final case class Saga[+E, +A] private (
  private val request: ZIO[Any with Clock, (E, Compensator[Any, E]), (A, Compensator[Any, E])]
) extends AnyVal {
  self =>

  def map[C](f: A => C): Saga[E, C] =
    Saga(request.map { case (a, comp) => (f(a), comp) })

  def flatMap[E1 >: E, C](f: A => Saga[E1, C]): Saga[E1, C] =
    Saga(request.flatMap {
      case (a, compA) =>
        tapError(f(a).request)({ case (e, _) => compA.mapError(_ => (e, IO.unit)) })
    })

  def flatten[E1 >: E, B](implicit ev: A <:< Saga[E1, B]): Saga[E1, B] =
    self.flatMap(r => ev(r))

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

  def compensate[R >: Any, E, A](action: IO[E, A], c: Compensator[R, E]): Saga[E, A] =
    Saga(action.bimap((_, c), (_, c)))

  def retryableCompensate[R >: Any, E, A](action: IO[E, A],
                                          c: Compensator[R, E],
                                          schedule: Schedule[E, Any]): Saga[E, A] = {
    compensate(action, c.retry(schedule.unit))
  }

  def noCompensate[E, A](action: IO[E, A]): Saga[E, A] = compensate(action, ZIO.unit)

  implicit class Compensable[+E, +A](val action: IO[E, A]) extends AnyVal {
    def compensate[R >: Any, E1 >: E](c: Compensator[R, E1]): Saga[E1, A] = Saga.compensate(action, c)

    def retryableCompensate[R >: Any, E1 >: E](c: Compensator[R, E1], schedule: Schedule[E1, Any]): Saga[E1, A] =
      Saga.retryableCompensate(action, c, schedule)

    def noCompensate: Saga[E, A] = Saga.noCompensate(action)
  }

}
