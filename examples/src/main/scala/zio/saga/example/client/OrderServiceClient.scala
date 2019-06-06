package zio.saga.example.client

import java.util.UUID

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.Task
import scalaz.zio.interop.CatsPlatform
import zio.saga.example.TaskC

trait OrderServiceClient {

  def closeOrder(userId: UUID, orderId: BigInt, traceId: String): TaskC[Unit]

  def reopenOrder(userId: UUID, orderId: BigInt, traceId: String): TaskC[Unit]
}

class OrderServiceClientStub(logger: Logger[Task]) extends OrderServiceClient {

  override def closeOrder(userId: UUID, orderId: BigInt, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep
      _ <- randomFail("closeOrder")
      _ <- logger.info(s"Order #$orderId closed")
    } yield ()

  override def reopenOrder(userId: UUID, orderId: BigInt, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep
      _ <- randomFail("reopenOrder")
      _ <- logger.info(s"Order #$orderId reopened")
    } yield ()
}

object OrderServiceClientStub extends CatsPlatform {
  def apply(): Task[OrderServiceClientStub] = Slf4jLogger.create[Task].map(new OrderServiceClientStub(_))
}
