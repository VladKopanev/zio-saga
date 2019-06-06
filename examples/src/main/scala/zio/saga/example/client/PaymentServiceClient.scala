package zio.saga.example.client

import java.util.UUID

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }

trait PaymentServiceClient {

  def collectPayments(userId: UUID, amount: BigDecimal, traceId: String): ZIO[Any, Throwable, Unit]

  def refundPayments(userId: UUID, amount: BigDecimal, traceId: String): ZIO[Any, Throwable, Unit]
}

class PaymentServiceClientStub(logger: Logger[Task]) extends PaymentServiceClient {

  override def collectPayments(userId: UUID, amount: BigDecimal, traceId: String): ZIO[Any, Throwable, Unit] =
    logger.info(s"Payments collected from user #$userId")

  override def refundPayments(userId: UUID, amount: BigDecimal, traceId: String): ZIO[Any, Throwable, Unit] =
    logger.info(s"Payments refunded to user #$userId")
}

object PaymentServiceClientStub extends CatsPlatform {

  def apply(): Task[PaymentServiceClient] =
    Slf4jLogger.create[Task].map(new PaymentServiceClientStub(_))
}
