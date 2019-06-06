package zio.saga.example.client

import java.util.UUID

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }

trait LoyaltyPointsServiceClient {

  def assignLoyaltyPoints(userId: UUID, amount: Double, traceId: String): ZIO[Any, Throwable, Unit]

  def cancelLoyaltyPoints(userId: UUID, amount: Double, traceId: String): ZIO[Any, Throwable, Unit]
}

class LoyaltyPointsServiceClientStub(logger: Logger[Task]) extends LoyaltyPointsServiceClient {
  override def assignLoyaltyPoints(userId: UUID, amount: Double, traceId: String): ZIO[Any, Throwable, Unit] =
    logger.info(s"Loyalty points assigned to user $userId")
  override def cancelLoyaltyPoints(userId: UUID, amount: Double, traceId: String): ZIO[Any, Throwable, Unit] =
    logger.info(s"Loyalty points canceled for user $userId")
}

object LoyaltyPointsServiceClientStub extends CatsPlatform {
  def apply(): Task[LoyaltyPointsServiceClientStub] = Slf4jLogger.create[Task].map(new LoyaltyPointsServiceClientStub(_))
}
