package zio.saga.example.client

import java.util.UUID

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }

trait OrderServiceClient {

  def closeOrder(userId: UUID, orderId: BigInt): ZIO[Any, Throwable, Unit]

  def reopenOrder(userId: UUID, orderId: BigInt): ZIO[Any, Throwable, Unit]
}

class OrderServiceClientStub(logger: Logger[Task]) extends OrderServiceClient {
  override def closeOrder(userId: UUID, orderId: BigInt): ZIO[Any, Throwable, Unit] =
    logger.info(s"Order #$orderId closed")
  override def reopenOrder(userId: UUID, orderId: BigInt): ZIO[Any, Throwable, Unit] =
    logger.info(s"Order #$orderId reopened")
}

object OrderServiceClientStub extends CatsPlatform {
  def apply(): Task[OrderServiceClientStub] = Slf4jLogger.create[Task].map(new OrderServiceClientStub(_))
}
