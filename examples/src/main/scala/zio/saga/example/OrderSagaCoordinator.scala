package zio.saga.example

import java.util.UUID

import scalaz.zio.ZIO
import zio.saga.example.client.{ LoyaltyPointsServiceClient, OrderServiceClient, PaymentServiceClient }
import zio.saga.example.dao.SagaLogDao

trait OrderSagaCoordinator {
  def orderSaga(userId: UUID, orderId: BigInt, money: BigDecimal, bonuses: Double): ZIO[Any, Throwable, Unit]
}

class OrderSagaCoordinatorImpl(
  paymentServiceClient: PaymentServiceClient,
  loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
  orderServiceClient: OrderServiceClient,
  sagaLogDao: SagaLogDao
) extends OrderSagaCoordinator {

  import zio.saga.Saga._

  override def orderSaga(
    userId: UUID,
    orderId: BigInt,
    money: BigDecimal,
    bonuses: Double
  ): ZIO[Any, Throwable, Unit] = {

    def collectPayments(sagaId: Long) =
      paymentServiceClient.collectPayments(userId, money) <* sagaLogDao.createSagaStep("collectPayments", sagaId)

    def assignLoyaltyPoints(sagaId: Long) =
      loyaltyPointsServiceClient.assignLoyaltyPoints(userId, bonuses) <* sagaLogDao.createSagaStep(
        "assignLoyaltyPoints",
        sagaId
      )

    def closeOrder(sagaId: Long) =
      orderServiceClient.closeOrder(userId, orderId) <* sagaLogDao.createSagaStep("closeOrder", sagaId)

    def refundPayments(sagaId: Long) =
      paymentServiceClient.refundPayments(userId, money) <* sagaLogDao.createSagaStep("refundPayments", sagaId)

    def cancelLoyaltyPoints(sagaId: Long) =
      loyaltyPointsServiceClient.cancelLoyaltyPoints(userId, bonuses) <* sagaLogDao.createSagaStep(
        "cancelLoyaltyPoints",
        sagaId
      )

    def reopenOrder(sagaId: Long) =
      orderServiceClient.reopenOrder(userId, orderId) <* sagaLogDao.createSagaStep("reopenOrder", sagaId)

    sagaLogDao.startSaga(userId) >>= { sagaId =>
      (for {
        _      <- collectPayments(sagaId) compensate refundPayments(sagaId)
        _      <- assignLoyaltyPoints(sagaId) compensate cancelLoyaltyPoints(sagaId)
        _      <- closeOrder(sagaId) compensate reopenOrder(sagaId)
      } yield ()).transact *> sagaLogDao.finishSaga(sagaId)
    }

  }

  def restoreSaga(
    sagaId: Long,
    userId: UUID,
    orderId: BigInt,
    money: BigDecimal,
    bonuses: Double
  ): ZIO[Any, Throwable, Unit] = {

    def collectPayments(executed: List[String], sagaId: Long) =
      (paymentServiceClient.collectPayments(userId, money) <*
        sagaLogDao.createSagaStep("collectPayments", sagaId)).when(!executed.contains("collectPayments"))

    def assignLoyaltyPoints(executed: List[String], sagaId: Long) =
      (loyaltyPointsServiceClient.assignLoyaltyPoints(userId, bonuses) <* sagaLogDao.createSagaStep(
        "assignLoyaltyPoints",
        sagaId
      )).when(!executed.contains("assignLoyaltyPoints"))

    def closeOrder(executed: List[String], sagaId: Long) =
      (orderServiceClient.closeOrder(userId, orderId) <* sagaLogDao.createSagaStep("closeOrder", sagaId))
        .when(!executed.contains("closeOrder"))

    def refundPayments(executed: List[String], sagaId: Long) =
      (paymentServiceClient.refundPayments(userId, money) <* sagaLogDao.createSagaStep("refundPayments", sagaId))
        .when(!executed.contains("refundPayments"))

    def cancelLoyaltyPoints(executed: List[String], sagaId: Long) =
      (loyaltyPointsServiceClient.cancelLoyaltyPoints(userId, bonuses) <* sagaLogDao.createSagaStep(
        "cancelLoyaltyPoints",
        sagaId
      )).when(!executed.contains("cancelLoyaltyPoints"))

    def reopenOrder(executed: List[String], sagaId: Long) =
      (orderServiceClient.reopenOrder(userId, orderId) <* sagaLogDao.createSagaStep("reopenOrder", sagaId))
        .when(!executed.contains("reopenOrder"))


    sagaLogDao.listExecutedSteps(sagaId) >>= { executed =>
      val executedSteps = executed.map(_.name)
      (for {
        _      <- collectPayments(executedSteps, sagaId) compensate refundPayments(executedSteps, sagaId)
        _      <- assignLoyaltyPoints(executedSteps, sagaId) compensate cancelLoyaltyPoints(executedSteps, sagaId)
        _      <- closeOrder(executedSteps, sagaId) compensate reopenOrder(executedSteps, sagaId)
      } yield ()).transact *> sagaLogDao.finishSaga(sagaId)
    }
  }

}
