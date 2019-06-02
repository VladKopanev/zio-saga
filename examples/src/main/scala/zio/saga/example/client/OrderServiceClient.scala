package zio.saga.example.client

import java.util.UUID

import scalaz.zio.ZIO

trait OrderServiceClient {

  def closeOrder(userId: UUID, orderId: BigInt): ZIO[Any, Throwable, Unit]

  def reopenOrder(userId: UUID, orderId: BigInt): ZIO[Any, Throwable, Unit]
}
