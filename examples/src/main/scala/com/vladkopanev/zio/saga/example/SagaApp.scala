package com.vladkopanev.zio.saga.example
import com.vladkopanev.zio.saga.example.client.{
    LoyaltyPointsServiceClientStub,
    OrderServiceClientStub,
    PaymentServiceClientStub
  }
import com.vladkopanev.zio.saga.example.dao.SagaLogDaoImpl
import com.vladkopanev.zio.saga.example.endpoint.SagaEndpoint
import zio.interop.catz._
import zio.console.putStrLn
import zio.{ App, ZIO }

object SagaApp extends App {

  import org.http4s.server.blaze._

  implicit val runtime = this

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] = {
    val flakyClient         = sys.env.getOrElse("FLAKY_CLIENT", "false").toBoolean
    val clientMaxReqTimeout = sys.env.getOrElse("CLIENT_MAX_REQUEST_TIMEOUT_SEC", "10").toInt
    val sagaMaxReqTimeout   = sys.env.getOrElse("SAGA_MAX_REQUEST_TIMEOUT_SEC", "12").toInt

    (for {
      paymentService <- PaymentServiceClientStub(clientMaxReqTimeout, flakyClient)
      loyaltyPoints  <- LoyaltyPointsServiceClientStub(clientMaxReqTimeout, flakyClient)
      orderService   <- OrderServiceClientStub(clientMaxReqTimeout, flakyClient)
      logDao         = new SagaLogDaoImpl
      orderSEC       <- OrderSagaCoordinatorImpl(paymentService, loyaltyPoints, orderService, logDao, sagaMaxReqTimeout)
      app            = new SagaEndpoint(orderSEC).service
      _              <- orderSEC.recoverSagas.fork
      _              <- BlazeServerBuilder[TaskC].bindHttp(8042).withHttpApp(app).serve.compile.drain
    } yield ()).foldM(
      e => putStrLn(s"Saga Coordinator fails with error $e, stopping server...").const(1),
      _ => putStrLn(s"Saga Coordinator finished successfully, stopping server...").const(0)
    )
  }

}
