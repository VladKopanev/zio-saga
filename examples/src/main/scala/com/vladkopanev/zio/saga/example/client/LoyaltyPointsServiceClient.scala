package com.vladkopanev.zio.saga.example.client

import java.util.UUID

import com.vladkopanev.zio.saga.example.TaskC
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import zio.Task

trait LoyaltyPointsServiceClient {

  def assignLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit]

  def cancelLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit]
}

class LoyaltyPointsServiceClientStub(logger: Logger[Task], maxRequestTimeout: Int, flaky: Boolean)
  extends LoyaltyPointsServiceClient {

  override def assignLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep(maxRequestTimeout)
      _ <- randomFail("assignLoyaltyPoints").when(flaky)
      _ <- logger.info(s"Loyalty points assigned to user $userId")
    } yield ()

  override def cancelLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep(maxRequestTimeout)
      _ <- randomFail("cancelLoyaltyPoints").when(flaky)
      _ <- logger.info(s"Loyalty points canceled for user $userId")
    } yield ()

}

object LoyaltyPointsServiceClientStub {

  import zio.interop.catz._

  def apply(maxRequestTimeout: Int, flaky: Boolean): Task[LoyaltyPointsServiceClientStub] =
    Slf4jLogger.create[Task].map(new LoyaltyPointsServiceClientStub(_, maxRequestTimeout, flaky))
}
