package zio.saga.example.endpoint
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import scalaz.zio.interop.CatsPlatform
import zio.saga.example.{OrderSagaCoordinator, TaskC}
import zio.saga.example.model.OrderInfo
import org.http4s.implicits._

class SagaEndpoint(orderSagaCoordinator: OrderSagaCoordinator) extends CatsPlatform with Http4sDsl[TaskC]  {

  implicit val decoder = jsonOf[TaskC, OrderInfo]

  val service: HttpApp[TaskC] = HttpRoutes.of[TaskC] {
    case req @ POST -> Root / "finishOrder" =>
      for {
        OrderInfo(userId, orderId, money, bonuses) <- req.as[OrderInfo]
        _                                          <- orderSagaCoordinator.runSaga(userId, orderId, money, bonuses, None)
        resp                                       <- Ok("Saga submitted")
      } yield resp
  }.orNotFound
}
