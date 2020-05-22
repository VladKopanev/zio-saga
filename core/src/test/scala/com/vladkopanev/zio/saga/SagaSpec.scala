package com.vladkopanev.zio.saga

import java.util.concurrent.TimeUnit
import com.vladkopanev.zio.saga.Saga.Compensator
import zio._
import zio.clock.{ currentTime, Clock }
import zio.duration._
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

import Saga._
import SagaSpecUtil._

object SagaSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Any] =  suite("SagaSpec")(
    suite("Saga#map")(testM("should change the result value with provided function") {
      assertM(Saga.compensate(ZIO.succeed(1), ZIO.unit).map(_.toString).transact)(
        equalTo("1")
      )
    }),
    suite("Saga#zipPar")(testM("should successfully run two Sagas") {
      assertM(
        (bookFlight compensate cancelFlight zipPar (bookHotel compensate cancelHotel)).transact)(
        equalTo((FlightPayment, HotelPayment))
      )
    }),
    suite("Saga#zipWithPar")(
      testM("should successfully run two Sagas in parallel") {
        (for {
          startTime <- currentTime(TimeUnit.MILLISECONDS)
          _ <- (sleep(1000.millis) *> bookFlight compensate cancelFlight)
            .zipWithPar(sleep(1000.millis) *> bookHotel compensate cancelHotel)((_, _) => ())
            .transact
          endTime <- currentTime(TimeUnit.MILLISECONDS)
        } yield assert(endTime - startTime)(isLessThanEqualTo(1500L))).provideLayer(Clock.live)
      },
      testM("should run both compensating actions in case right request fails") {
        val bookFlightS = sleep(1000.millis) *> bookFlight
        val failHotel = sleep(100.millis) *> IO.fail(HotelBookingError())

        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (bookFlightS compensate cancelFlight(actionLog.update(_ :+ "flight canceled")))
            .zipWithPar(failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))((_, _) => ())
            .transact
            .orElse(IO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled")))
      },
      testM("should run both compensating actions in case left request fails") {
        val bookFlightS = sleep(1000.millis) *> bookFlight
        val failHotel = sleep(100.millis) *> IO.fail(HotelBookingError())

        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))
            .zipWithPar(bookFlightS compensate cancelFlight(actionLog.update(_ :+ "flight canceled")))((_, _) =>
              ()
            )
            .transact
            .orElse(IO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled")))
      },
      testM("should run both compensating actions in case both requests fails") {
        val failFlight = sleep(1000.millis) *> IO.fail(FlightBookingError())
        val failHotel = sleep(1000.millis) *> IO.fail(HotelBookingError())

        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (failFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled")))
            .zipWithPar(failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))((_, _) => ())
            .transact
            .orElse(IO.unit)
          log <- actionLog.get
        } yield assert(log)(hasSameElements(Vector("flight canceled", "hotel canceled")))
      },
      testM("should run compensating actions in order that is opposite to which requests finished") {
        val failFlight = sleep(1000.millis) *> IO.fail(FlightBookingError())
        val failHotel = sleep(100.millis) *> IO.fail(HotelBookingError())

        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (failFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled")))
            .zipWithPar(failHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled")))((_, _) => ())
            .transact
            .orElse(IO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled")))
      }
    ),
    suite("Saga#zipWithParAll")(testM("should allow combining compensations in parallel") {
      val failFlight = IO.fail(FlightBookingError())
      val failHotel = IO.fail(HotelBookingError())

      def cancelFlightC(actionLog: Ref[Vector[String]]) =
        sleep(100.millis) *>
          cancelFlight(actionLog.update(_ :+ "flight canceled"))

      def cancelHotelC(actionLog: Ref[Vector[String]]) =
        sleep(100.millis) *>
          cancelHotel(actionLog.update(_ :+ "hotel canceled"))

      for {
        actionLog <- Ref.make(Vector.empty[String])
        _ <- (failFlight compensate cancelFlightC(actionLog))
          .zipWithParAll(failHotel compensate cancelHotelC(actionLog))((_, _) => ())((a, b) => a.zipPar(b).unit)
          .transact
          .orElse(IO.unit)
        log <- actionLog.get
      } yield assert(log)(hasSameElements(Vector("flight canceled", "hotel canceled")))
    }),
    suite("Saga#retryableCompensate")(
      testM("should construct Saga that repeats compensating action once") {
        val failFlight: ZIO[Clock, FlightBookingError, PaymentInfo] = sleep(1000.millis) *> IO.fail(
          FlightBookingError()
        )

        def failCompensator(log: Ref[Vector[String]]): Compensator[Any, FlightBookingError] =
          cancelFlight(log.update(_ :+ "Compensation failed")) *> IO.fail(FlightBookingError())

        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (failFlight retryableCompensate(failCompensator(actionLog), Schedule.once)).transact
            .orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector.fill(2)("Compensation failed")))
      },
      testM("should work with other combinators") {
        val saga = for {
          _ <- bookFlight.noCompensate
          _ <- bookHotel retryableCompensate(cancelHotel, Schedule.once)
          _ <- bookCar compensate cancelCar
        } yield ()

        assertM(saga.transact)(anything)
      }
    ),
    suite("Saga#collectAllPar")(
      testM("should construct a Saga that runs several requests in parallel") {
        def bookFlightS(log: Ref[Vector[String]]): ZIO[Clock, FlightBookingError, PaymentInfo] =
          sleep(1000.millis) *> bookFlight <* log.update(_ :+ "flight is booked")

        def bookHotelS(log: Ref[Vector[String]]): ZIO[Clock, HotelBookingError, PaymentInfo] =
          sleep(600.millis) *> bookHotel <* log.update(_ :+ "hotel is booked")

        def bookCarS(log: Ref[Vector[String]]): ZIO[Clock, CarBookingError, PaymentInfo] =
          sleep(300.millis) *> bookCar <* log.update(_ :+ "car is booked")

        def bookCarS2(log: Ref[Vector[String]]): ZIO[Clock, CarBookingError, PaymentInfo] =
          sleep(100.millis) *> bookCar <* log.update(_ :+ "car2 is booked")

        for {
          actionLog <- Ref.make(Vector.empty[String])
          flight = bookFlightS(actionLog) compensate cancelFlight
          hotel = bookHotelS(actionLog) compensate cancelHotel
          car = bookCarS(actionLog) compensate cancelCar
          car2 = bookCarS2(actionLog) compensate cancelCar
          _ <- Saga.collectAllPar(flight, hotel, car, car2).transact
          log <- actionLog.get
        } yield assert(log)(
          equalTo(Vector("car2 is booked", "car is booked", "hotel is booked", "flight is booked"))
        )
      },
      testM("should run all compensating actions in case of error") {
        val failFlightBooking = sleep(1000.millis) *> IO.fail(FlightBookingError())
        val bookHotelS = sleep(600.millis) *> bookHotel
        val bookCarS = sleep(300.millis) *> bookCar
        val bookCarS2 = sleep(100.millis) *> bookCar

        for {
          actionLog <- Ref.make(Vector.empty[String])
          flight = failFlightBooking compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
          hotel = bookHotelS compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
          car = bookCarS compensate cancelCar(actionLog.update(_ :+ "car canceled"))
          car2 = bookCarS2 compensate cancelCar(actionLog.update(_ :+ "car2 canceled"))
          _ <- Saga.collectAllPar(List(flight, hotel, car, car2)).transact.orElse(IO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled", "car canceled", "car2 canceled")))
      }
    ),
    suite("Saga#succeed")(testM("should construct saga that will succeed") {
      val failFlightBooking = IO.fail(FlightBookingError())
      val stub = 1

      for {
        actionLog <- Ref.make(Vector.empty[String])
        _ <- (for {
          i <- Saga.succeed(stub)
          _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ s"flight canceled $i"))
        } yield ()).transact.orElse(ZIO.unit)
        log <- actionLog.get
      } yield assert(log)(equalTo(Vector(s"flight canceled $stub")))
    }),
    suite("Saga#fail")(testM("should construct saga that will fail") {
      val failFlightBooking = IO.fail(FlightBookingError())

      for {
        actionLog <- Ref.make(Vector.empty[String])
        _ <- (for {
          i <- Saga.fail(FlightBookingError())
          _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ s"flight canceled $i"))
        } yield ()).transact.orElse(ZIO.unit)
        log <- actionLog.get
      } yield assert(log)(equalTo(Vector.empty))
    }),
    suite("Saga#compensateIfFail")(
      testM("should construct saga step that executes it's compensation if it's requests fails") {
        val failCar = IO.fail(CarBookingError())
        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (for {
            _ <- bookFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
            _ <- bookHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
            _ <- failCar compensateIfFail ((_: SagaError) => cancelCar(actionLog.update(_ :+ "car canceled")))
          } yield ()).transact.orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("car canceled", "hotel canceled", "flight canceled")))
      },
      testM("should construct saga step that do not executes it's compensation if it's request succeeds") {
        val failFlightBooking = IO.fail(FlightBookingError())
        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (for {
            _ <- bookCar compensateIfFail ((_: SagaError) => cancelCar(actionLog.update(_ :+ "car canceled")))
            _ <- bookHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
            _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
          } yield ()).transact.orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled")))
      }
    ),
    suite("Saga#compensateIfSuccess")(
      testM(
        "should construct saga step that executes it's compensation if it's requests succeeds"
      ) {
        val failFlightBooking = IO.fail(FlightBookingError())
        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (for {
            _ <- bookCar compensateIfSuccess (
              (_: PaymentInfo) => cancelCar(actionLog.update(_ :+ "car canceled"))
              )
            _ <- bookHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
            _ <- failFlightBooking compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
          } yield ()).transact.orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled", "car canceled")))
      },
      testM("should construct saga step that do not executes it's compensation if it's request fails") {
        val failCar = IO.fail(CarBookingError())
        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (for {
            _ <- bookHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
            _ <- bookFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
            _ <- failCar compensateIfSuccess (
              (_: PaymentInfo) => cancelCar(actionLog.update(_ :+ "car canceled"))
              )
          } yield ()).transact.orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("flight canceled", "hotel canceled")))
      }
    ),
    suite("Saga#compensate")(
      testM("should allow compensation to be dependent on the result of corresponding effect") {
        val failCar = IO.fail(CarBookingError())
        for {
          actionLog <- Ref.make(Vector.empty[String])
          _ <- (for {
            _ <- bookHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
            _ <- bookFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
            _ <- failCar compensate (
              (_: Either[SagaError, PaymentInfo]) => cancelCar(actionLog.update(_ :+ "car canceled"))
              )
          } yield ()).transact.orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("car canceled", "flight canceled", "hotel canceled")))
      }
    ),
    suite("Saga#flatten")(
      testM("should execute outer effect first and then the inner one producing the result of it") {
        for {
          actionLog <- Ref.make(Vector.empty[String])
          outer = bookFlight compensate cancelFlight(actionLog.update(_ :+ "flight canceled"))
          inner = bookHotel compensate cancelHotel(actionLog.update(_ :+ "hotel canceled"))
          failCar = IO.fail(CarBookingError()) compensate cancelCar(actionLog.update(_ :+ "car canceled"))

          _ <- outer
            .map(_ => inner)
            .flatten[Any, SagaError, PaymentInfo]
            .flatMap(_ => failCar)
            .transact
            .orElse(ZIO.unit)
          log <- actionLog.get
        } yield assert(log)(equalTo(Vector("car canceled", "hotel canceled", "flight canceled")))
      }
    ),
    suite("Saga#transact")(
      testM("should return original error in case compensator also fails") {
        val expectedError = FlightBookingError()
        val failFlight: ZIO[Clock, FlightBookingError, Any] = sleep(1000.millis) *> IO.fail(expectedError)

        val failCompensator = cancelFlight *> IO.fail(CarBookingError())

        val saga = (failFlight compensate failCompensator).transact.catchAll(e => IO.succeed(e))

        assertM(saga)(equalTo(expectedError))
      },
      testM("should return original error in case compensator also fails 2") {
        val expectedError = FlightBookingError()
        val failFlight: ZIO[Clock, FlightBookingError, Any] = sleep(1000.millis) *> IO.fail(expectedError)

        val failCompensator = cancelFlight *> IO.fail(new RuntimeException())

        val saga = (for {
          _ <- bookHotel compensate cancelHotel
          _ <- failFlight compensate failCompensator
          _ <- bookCar compensate cancelCar
        } yield ()).transact.catchAll[Clock, Any, Any](e => IO.succeed(e))

        assertM(saga)(equalTo(expectedError))
      }
    )
  )
}

object SagaSpecUtil {

  def sleep(d: Duration): URIO[Clock, Unit] = ZIO.sleep(d).provideLayer(Clock.live)

  sealed trait SagaError extends Product with Serializable {
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
