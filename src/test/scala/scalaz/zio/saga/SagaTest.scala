package scalaz.zio.saga

import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import scalaz.zio.saga.Saga.Compensator
import scalaz.zio.{DefaultRuntime, IO, ZIO}

class SagaTest extends FlatSpec {
  import Saga._
  import SagaTest._

  "Saga#map" should "change the result value with provided function" in {
    val runtime = new DefaultRuntime {}
    val saga    = Saga.compensate(ZIO.succeed(1), ZIO.unit).map(_.toString)
    runtime.unsafeRun(saga.run) shouldBe "1"
  }

  "Saga#zipPar" should "successfully run two Sagas" in  new DefaultRuntime {
    val saga = bookFlight compensate cancelFlight zipPar (bookHotel compensate cancelHotel)
    unsafeRun(saga.run) shouldBe (FlightPayment, HotelPayment)
  }


  "Saga#zipWithPar" should "successfully run two Sagas in parallel" in  new DefaultRuntime {
    import scalaz.zio.duration._
    val sleep = ZIO.sleep(1000.millis).provide(Environment)

    val saga = (sleep *> bookFlight compensate cancelFlight)
      .zipWithPar(sleep *> bookHotel compensate cancelHotel)((_, _) => ())

    val start = System.currentTimeMillis()
    unsafeRun(saga.run)
    val time = System.currentTimeMillis() - start
    assert(time <= 1500, "Time limit for executing two Sagas in parallel exceeded")
  }
}

object SagaTest {
  sealed trait SagaError {
    def message: String
  }
  case class FlightBookingError(message: String = "Can't book a flight")        extends SagaError
  case class HotelBookingError(message: String = "Can't book a hotel room")     extends SagaError
  case class CarBookingError(message: String = "Can't book a car")              extends SagaError
  case class PaymentFailedError(message: String = "Can't collect the payments") extends SagaError

  case class PaymentInfo(amount: Double)

  val FlightPayment = PaymentInfo(420d)
  val HotelPayment = PaymentInfo(1448d)
  val CarPayment = PaymentInfo(42d)

  def bookFlight: IO[FlightBookingError, PaymentInfo] = IO.succeed(FlightPayment)

  def bookHotel: IO[HotelBookingError, PaymentInfo] = IO.succeed(HotelPayment)

  def bookCar: IO[CarBookingError, PaymentInfo] = IO.succeed(CarPayment)

  def collectPayments(paymentInfo: PaymentInfo*): IO[PaymentFailedError, Unit] = IO.unit

  def cancelFlight: Compensator[Any, FlightBookingError] = IO.unit

  def cancelHotel: Compensator[Any, HotelBookingError] = IO.unit

  def cancelCar: Compensator[Any, CarBookingError] = IO.unit

  def refundPayments(paymentInfo: PaymentInfo*): Compensator[Any, PaymentFailedError] = IO.unit

}
