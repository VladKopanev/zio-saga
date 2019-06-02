package zio.saga.example.client

import java.util.UUID

import scalaz.zio.ZIO

trait LoyaltyPointsServiceClient {

  def assignLoyaltyPoints(userId: UUID, amount: Double): ZIO[Any, Throwable, Unit]

  def cancelLoyaltyPoints(userId: UUID, amount: Double): ZIO[Any, Throwable, Unit]
}
