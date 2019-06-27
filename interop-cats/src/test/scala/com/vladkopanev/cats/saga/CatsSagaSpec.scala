package com.vladkopanev.cats.saga
import cats.effect.concurrent.Ref
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import cats.effect.{IO, Timer}
import cats.syntax.all._
import com.vladkopanev.cats.saga.CatsSagaSpec._
import com.vladkopanev.cats.saga.Saga._

import scala.concurrent.ExecutionContext

class CatsSagaSpec extends FlatSpec {

  import scala.concurrent.duration._

  it should "run all compensating actions in case of error" in new TestRuntime {
    val failFlightBooking: IO[PaymentInfo] = IO.sleep(1000.millis) *> IO.raiseError(FlightBookingError())
    val bookHotelS        = IO.sleep(600.millis) *> bookHotel
    val bookCarS          = IO.sleep(300.millis) *> bookCar
    val bookCarS2         = IO.sleep(100.millis) *> bookCar

    val sagaIO: IO[Vector[String]] = for {
      actionLog <- Ref.of[IO, Vector[String]](Vector.empty[String])
      _ <- (for {
            _ <- bookHotelS compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
            _ <- bookCarS compensate cancelCar(actionLog.update(_ :+ "car canceled"))
            _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
            _ <- bookCarS2 compensate cancelCar(actionLog.update(_ :+ "car2 canceled"))
      } yield ()).transact.handleErrorWith(_ => IO.unit)
      log <- actionLog.get
    } yield log

    val actionLog = sagaIO.unsafeRunSync()
    actionLog shouldBe Vector("flight canceled", "car canceled", "hotel canceled")
  }

}

trait TestRuntime {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
}


object CatsSagaSpec {
  sealed trait SagaError extends RuntimeException {
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

  def bookFlight: IO[PaymentInfo] = IO.pure(FlightPayment)

  def bookHotel: IO[PaymentInfo] = IO.pure(HotelPayment)

  def bookCar: IO[PaymentInfo] = IO.pure(CarPayment)

  def collectPayments(paymentInfo: PaymentInfo*): IO[Unit] = IO.pure(paymentInfo)

  def cancelFlight: IO[Unit] = IO.unit

  def cancelFlight(postAction: IO[Any]): IO[Unit] = postAction *> IO.unit

  def cancelHotel: IO[Unit] = IO.unit

  def cancelHotel(postAction: IO[Any]): IO[Unit] =
    postAction *> IO.unit

  def cancelCar: IO[Unit] = IO.unit

  def cancelCar(postAction: IO[Any]): IO[Unit] = postAction *> IO.unit

  def refundPayments(paymentInfo: PaymentInfo*): IO[Unit] = IO.pure(paymentInfo).void

}