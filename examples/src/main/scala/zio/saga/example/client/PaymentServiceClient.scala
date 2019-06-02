package zio.saga.example.client

import java.util.UUID

import scalaz.zio.ZIO

trait PaymentServiceClient {

  def collectPayments(userId: UUID, amount: BigDecimal): ZIO[Any, Throwable, Unit]

  def refundPayments(userId: UUID, amount: BigDecimal): ZIO[Any, Throwable, Unit]
}
