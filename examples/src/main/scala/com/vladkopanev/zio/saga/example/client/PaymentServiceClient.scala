package com.vladkopanev.zio.saga.example.client

import java.util.UUID

import com.vladkopanev.zio.saga.example.TaskC
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import zio.Task

trait PaymentServiceClient {

  def collectPayments(userId: UUID, amount: BigDecimal, traceId: String): TaskC[Unit]

  def refundPayments(userId: UUID, amount: BigDecimal, traceId: String): TaskC[Unit]
}

class PaymentServiceClientStub(logger: Logger[Task], maxRequestTimeout: Int, flaky: Boolean)
    extends PaymentServiceClient {

  override def collectPayments(userId: UUID, amount: BigDecimal, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep(maxRequestTimeout)
      _ <- randomFail("collectPayments").when(flaky)
      _ <- logger.info(s"Payments collected from user #$userId")
    } yield ()

  override def refundPayments(userId: UUID, amount: BigDecimal, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep(maxRequestTimeout)
      _ <- randomFail("refundPayments").when(flaky)
      _ <- logger.info(s"Payments refunded to user #$userId")
    } yield ()
}

object PaymentServiceClientStub {

  import zio.interop.catz._

  def apply(maxRequestTimeout: Int, flaky: Boolean): Task[PaymentServiceClient] =
    Slf4jLogger.create[Task].map(new PaymentServiceClientStub(_, maxRequestTimeout, flaky))
}
