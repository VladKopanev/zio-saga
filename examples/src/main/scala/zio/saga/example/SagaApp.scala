package zio.saga.example

import scalaz.zio.console._
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ App, ZIO }
import zio.saga.example.client.{ LoyaltyPointsServiceClientStub, OrderServiceClientStub, PaymentServiceClientStub }
import zio.saga.example.dao.SagaLogDaoImpl
import zio.saga.example.endpoint.SagaEndpoint

object SagaApp extends CatsPlatform with App {

  import org.http4s.server.blaze._

  implicit val runtime = this

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    (for {
      paymentService <- PaymentServiceClientStub()
      loyaltyPoints  <- LoyaltyPointsServiceClientStub()
      orderService   <- OrderServiceClientStub()
      orderSEC       = new OrderSagaCoordinatorImpl(paymentService, loyaltyPoints, orderService, new SagaLogDaoImpl)
      app            = new SagaEndpoint(orderSEC).service
      _              <- BlazeServerBuilder[TaskC].bindHttp(8042).withHttpApp(app).serve.compile.drain
    } yield ()).foldM(
      e => putStrLn(s"Saga Coordinator fails with error $e, stopping server...").const(1),
      _ => putStrLn(s"Saga Coordinator finished successfully, stopping server...").const(0)
    )
}
