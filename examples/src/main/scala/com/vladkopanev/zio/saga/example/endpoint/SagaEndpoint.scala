package com.vladkopanev.zio.saga.example.endpoint

import com.vladkopanev.zio.saga.example.{ OrderSagaCoordinator, TaskC }
import com.vladkopanev.zio.saga.example.model.OrderInfo
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.{ HttpApp, HttpRoutes }
import zio.interop.catz._

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
