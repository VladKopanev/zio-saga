package zio.saga.example

import java.util.UUID

import scalaz.zio.ZIO
import zio.saga.example.client.{ LoyaltyPointsServiceClient, OrderServiceClient, PaymentServiceClient }

trait OrderSagaCoordinator {
  def orderSaga(userId: UUID, orderId: BigInt, money: BigDecimal, bonuses: Double): ZIO[Any, Throwable, Unit]
}

class OrderSagaCoordinatorImpl(
  paymentServiceClient: PaymentServiceClient,
  loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
  orderServiceClient: OrderServiceClient
) extends OrderSagaCoordinator {

  override def orderSaga(
    userId: UUID,
    orderId: BigInt,
    money: BigDecimal,
    bonuses: Double
  ): ZIO[Any, Throwable, Unit] =
    for {
      _ <- paymentServiceClient.collectPayments(userId, money)
      _ <- loyaltyPointsServiceClient.assignLoyaltyPoints(userId, bonuses)
      _ <- orderServiceClient.closeOrder(userId, orderId)
    } yield ()
}
