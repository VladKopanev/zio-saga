package zio.saga.example

import java.util.UUID

import scalaz.zio.ZIO
import zio.saga.example.client.{ LoyaltyPointsServiceClient, OrderServiceClient, PaymentServiceClient }
import zio.saga.example.dao.SagaLogDao

trait OrderSagaCoordinator {
  def runSaga(userId: UUID,
              orderId: BigInt,
              money: BigDecimal,
              bonuses: Double,
              sagaIdOpt: Option[Long]): ZIO[Any, Throwable, Unit]
}

class OrderSagaCoordinatorImpl(
  paymentServiceClient: PaymentServiceClient,
  loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
  orderServiceClient: OrderServiceClient,
  sagaLogDao: SagaLogDao
) extends OrderSagaCoordinator {

  import zio.saga.Saga._

  def runSaga(
    userId: UUID,
    orderId: BigInt,
    money: BigDecimal,
    bonuses: Double,
    sagaIdOpt: Option[Long]
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

    def saga(sagaId: Long, executedSteps: List[String]) =
      (for {
        _ <- collectPayments(executedSteps, sagaId) compensate refundPayments(executedSteps, sagaId)
        _ <- assignLoyaltyPoints(executedSteps, sagaId) compensate cancelLoyaltyPoints(executedSteps, sagaId)
        _ <- closeOrder(executedSteps, sagaId) compensate reopenOrder(executedSteps, sagaId)
      } yield ()).transact *> sagaLogDao.finishSaga(sagaId)

    for {
      sagaId        <- sagaIdOpt.fold(sagaLogDao.startSaga(userId))(ZIO.succeed)
      executed      <- sagaLogDao.listExecutedSteps(sagaId)
      executedSteps = executed.map(_.name)
      _             <- saga(sagaId, executedSteps)
    } yield ()

  }

}
