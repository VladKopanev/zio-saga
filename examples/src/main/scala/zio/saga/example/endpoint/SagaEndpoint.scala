package zio.saga.example.endpoint
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.{ HttpApp, HttpRoutes }
import scalaz.zio.interop.catz._
import zio.saga.example.model.OrderInfo
import zio.saga.example.{ OrderSagaCoordinator, TaskC }

final class SagaEndpoint(orderSagaCoordinator: OrderSagaCoordinator) extends Http4sDsl[TaskC] {

  private implicit val decoder = jsonOf[TaskC, OrderInfo]

  val service: HttpApp[TaskC] = HttpRoutes
    .of[TaskC] {
      case req @ POST -> Root / "saga" / "finishOrder" =>
        for {
          OrderInfo(userId, orderId, money, bonuses) <- req.as[OrderInfo]
          resp <- orderSagaCoordinator
                   .runSaga(userId, orderId, money, bonuses, None)
                   .foldM(fail => InternalServerError(fail.getMessage), _ => Ok("Saga submitted"))
        } yield resp
    }
    .orNotFound
}
