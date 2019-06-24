package zio.saga

import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import scalaz.zio.duration.Duration
import Saga.Compensator
import scalaz.zio.{DefaultRuntime, IO, Ref, Schedule, UIO, ZIO}

class SagaSpec extends FlatSpec {
  import Saga._
  import SagaSpec._
  import scalaz.zio.duration._

  "Saga#map" should "change the result value with provided function" in new TestRuntime {
    val saga = Saga.compensate(ZIO.succeed(1), ZIO.unit).map(_.toString)
    unsafeRun(saga.transact) shouldBe "1"
  }

  "Saga#zipPar" should "successfully run two Sagas" in new TestRuntime {
    val saga = bookFlight compensate cancelFlight zipPar (bookHotel compensate cancelHotel)
    unsafeRun(saga.transact) shouldBe ((FlightPayment, HotelPayment))
  }

  "Saga#zipWithPar" should "successfully run two Sagas in parallel" in new TestRuntime {

    val saga = (sleep(1000.millis) *> bookFlight compensate cancelFlight)
      .zipWithPar(sleep(1000.millis) *> bookHotel compensate cancelHotel)((_, _) => ())

    val start = System.currentTimeMillis()
    unsafeRun(saga.transact)
    val time = System.currentTimeMillis() - start
    assert(time <= 1500, "Time limit for executing two Sagas in parallel exceeded")
  }

  it should "run both compensating actions in case right request fails" in new TestRuntime {
    val bookFlightS = sleep(1000.millis) *> bookFlight
    val failHotel = sleep(100.millis) *> IO.fail(HotelBookingError())

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _ <- (bookFlightS compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))).zipWithPar(
            failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))((_, _) => ()).transact.orElse(IO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector("flight canceled", "hotel canceled")
  }

  it should "run both compensating actions in case left request fails" in new TestRuntime {
    val bookFlightS = sleep(1000.millis) *> bookFlight
    val failHotel = sleep(100.millis) *> IO.fail(HotelBookingError())

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _ <- (failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))).zipWithPar(
            bookFlightS compensate cancelFlight(actionLog.update(_ :+ "flight canceled")))((_, _) => ()).transact.orElse(IO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector("flight canceled", "hotel canceled")
  }

  it should "run both compensating actions in case both requests fails" in new TestRuntime {
    val failFlight = sleep(1000.millis) *> IO.fail(FlightBookingError())
    val failHotel = sleep(1000.millis) *> IO.fail(HotelBookingError())

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _ <- (failFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))).zipWithPar(
            failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))((_, _) => ()).transact.orElse(IO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog should contain theSameElementsAs Vector("flight canceled", "hotel canceled")
  }

  it should "run compensating actions in order that is opposite to which requests finished" in new TestRuntime {
    val failFlight = sleep(1000.millis) *> IO.fail(FlightBookingError())
    val failHotel = sleep(100.millis) *> IO.fail(HotelBookingError())

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _         <- (failFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))).zipWithPar(
                    failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))((_, _) => ())
        .transact.orElse(IO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector("flight canceled", "hotel canceled")
  }

  "Saga#retryableCompensate" should "construct Saga that repeats compensating action once" in new TestRuntime {
    val failFlight: ZIO[Any, FlightBookingError, PaymentInfo] = sleep(1000.millis) *> IO.fail(FlightBookingError())

    def failCompensator(log: Ref[Vector[String]]): Compensator[Any, FlightBookingError] =
      cancelFlight(log.update(_ :+ "Compensation failed")) *> IO.fail(FlightBookingError())

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _         <- (failFlight retryableCompensate (failCompensator(actionLog), Schedule.once)).transact.orElse(ZIO.unit)
      log       <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector.fill(2)("Compensation failed")
  }

  it should "work with other combinators" in new TestRuntime {
    val saga = for {
      _ <- bookFlight.noCompensate
      _ <- bookHotel retryableCompensate (cancelHotel, Schedule.once)
      _ <- bookCar compensate cancelCar
    } yield ()

    unsafeRun(saga.transact)
  }

  "Saga#collectAllPar" should "construct a Saga that runs several requests in parallel" in new TestRuntime {
    def bookFlightS(log: Ref[Vector[String]]): IO[FlightBookingError, PaymentInfo] =
      sleep(1000.millis) *> bookFlight <* log.update(_ :+ "flight is booked")
    def bookHotelS(log: Ref[Vector[String]]): IO[HotelBookingError, PaymentInfo] =
      sleep(600.millis) *> bookHotel <* log.update(_ :+ "hotel is booked")
    def bookCarS(log: Ref[Vector[String]]): IO[CarBookingError, PaymentInfo] =
      sleep(300.millis) *> bookCar <* log.update(_ :+ "car is booked")
    def bookCarS2(log: Ref[Vector[String]]): IO[CarBookingError, PaymentInfo] =
      sleep(100.millis) *> bookCar <* log.update(_ :+ "car2 is booked")

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      flight    = bookFlightS(actionLog) compensate cancelFlight
      hotel     = bookHotelS(actionLog) compensate cancelHotel
      car       = bookCarS(actionLog) compensate cancelCar
      car2      = bookCarS2(actionLog) compensate cancelCar
      _         <- Saga.collectAllPar(flight, hotel, car, car2).transact
      log       <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector("car2 is booked", "car is booked", "hotel is booked", "flight is booked")
  }

  it should "run all compensating actions in case of error" in new TestRuntime {
    val failFlightBooking = sleep(1000.millis) *> IO.fail(FlightBookingError())
    val bookHotelS        = sleep(600.millis) *> bookHotel
    val bookCarS          = sleep(300.millis) *> bookCar
    val bookCarS2         = sleep(100.millis) *> bookCar

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      flight    = failFlightBooking compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
      hotel     = bookHotelS compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
      car       = bookCarS compensate cancelCar(actionLog.update(_ :+ "car canceled"))
      car2      = bookCarS2 compensate cancelCar(actionLog.update(_ :+ "car2 canceled"))
      _         <- Saga.collectAllPar(flight, hotel, car, car2).transact.orElse(IO.unit)
      log       <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector("flight canceled", "hotel canceled", "car canceled", "car2 canceled")
  }

  "Saga#succeed" should "construct saga that will succeed" in new TestRuntime {
    val failFlightBooking = IO.fail(FlightBookingError())
    val stub              = 1

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _ <- (for {
            i <- Saga.succeed(stub)
            _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ s"flight canceled $i"))
          } yield ()).transact.orElse(ZIO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector(s"flight canceled $stub")
  }

  "Saga#fail" should "construct saga that will fail" in new TestRuntime {
    val failFlightBooking = IO.fail(FlightBookingError())
    val stub              = 1

    val sagaIO = for {
      actionLog <- Ref.make(Vector.empty[String])
      _ <- (for {
            i <- Saga.fail(FlightBookingError())
            _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ s"flight canceled $i"))
          } yield ()).transact.orElse(ZIO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = unsafeRun(sagaIO)
    actionLog shouldBe Vector.empty
  }
}

trait TestRuntime extends DefaultRuntime {
  def sleep(d: Duration): UIO[Unit] = ZIO.sleep(d).provide(Environment)
}

object SagaSpec {
  sealed trait SagaError {
    def message: String
  }
  case class FlightBookingError(message: String = "Can't book a flight")        extends SagaError
  case class HotelBookingError(message: String = "Can't book a hotel room")     extends SagaError
  case class CarBookingError(message: String = "Can't book a car")              extends SagaError
  case class PaymentFailedError(message: String = "Can't collect the payments") extends SagaError

  case class PaymentInfo(amount: Double)

  val FlightPayment = PaymentInfo(420d)
  val HotelPayment  = PaymentInfo(1448d)
  val CarPayment    = PaymentInfo(42d)

  def bookFlight: IO[FlightBookingError, PaymentInfo] = IO.succeed(FlightPayment)

  def bookHotel: IO[HotelBookingError, PaymentInfo] = IO.succeed(HotelPayment)

  def bookCar: IO[CarBookingError, PaymentInfo] = IO.succeed(CarPayment)

  def collectPayments(paymentInfo: PaymentInfo*): IO[PaymentFailedError, Unit] = IO.succeed(paymentInfo).unit

  def cancelFlight: Compensator[Any, FlightBookingError] = IO.unit

  def cancelFlight(postAction: UIO[Any]): Compensator[Any, FlightBookingError] =
    postAction *> IO.unit

  def cancelHotel: Compensator[Any, HotelBookingError] = IO.unit

  def cancelHotel(postAction: UIO[Any]): Compensator[Any, HotelBookingError] =
    postAction *> IO.unit

  def cancelCar: Compensator[Any, CarBookingError] = IO.unit

  def cancelCar(postAction: UIO[Any]): Compensator[Any, CarBookingError] = postAction *> IO.unit

  def refundPayments(paymentInfo: PaymentInfo*): Compensator[Any, PaymentFailedError] = IO.succeed(paymentInfo).unit

}
