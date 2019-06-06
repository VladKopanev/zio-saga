package zio.saga.example.client

import java.util.UUID

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.Task
import scalaz.zio.interop.CatsPlatform
import zio.saga.example.TaskC

trait LoyaltyPointsServiceClient {

  def assignLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit]

  def cancelLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit]
}

class LoyaltyPointsServiceClientStub(logger: Logger[Task]) extends LoyaltyPointsServiceClient {

  override def assignLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep
      _ <- randomFail("assignLoyaltyPoints")
      _ <- logger.info(s"Loyalty points assigned to user $userId")
    } yield ()

  override def cancelLoyaltyPoints(userId: UUID, amount: Double, traceId: String): TaskC[Unit] =
    for {
      _ <- randomSleep
      _ <- randomFail("cancelLoyaltyPoints")
      _ <- logger.info(s"Loyalty points canceled for user $userId")
    } yield ()

}

object LoyaltyPointsServiceClientStub extends CatsPlatform {
  def apply(): Task[LoyaltyPointsServiceClientStub] =
    Slf4jLogger.create[Task].map(new LoyaltyPointsServiceClientStub(_))
}
