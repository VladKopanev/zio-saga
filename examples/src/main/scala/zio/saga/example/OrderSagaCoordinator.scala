package zio.saga.example

import java.util.UUID

import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }
import zio.saga.example.client.{ LoyaltyPointsServiceClient, OrderServiceClient, PaymentServiceClient }
import zio.saga.example.dao.SagaLogDao
import zio.saga.example.model.OrderSagaData

trait OrderSagaCoordinator {
  def runSaga(userId: UUID,
              orderId: BigInt,
              money: BigDecimal,
              bonuses: Double,
              sagaIdOpt: Option[Long]): ZIO[Any, Throwable, Unit]

  def recoverSagas: TaskC[Unit]
}

class OrderSagaCoordinatorImpl(
  paymentServiceClient: PaymentServiceClient,
  loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
  orderServiceClient: OrderServiceClient,
  sagaLogDao: SagaLogDao,
  logger: StructuredLogger[Task]
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
      (paymentServiceClient.collectPayments(userId, money, sagaId.toString) <*
        sagaLogDao.createSagaStep("collectPayments", sagaId, result = None)).when(!executed.contains("collectPayments"))

    def assignLoyaltyPoints(executed: List[String], sagaId: Long) =
      (loyaltyPointsServiceClient.assignLoyaltyPoints(userId, bonuses, sagaId.toString) <*
        sagaLogDao.createSagaStep("assignLoyaltyPoints", sagaId, result = None))
        .when(!executed.contains("assignLoyaltyPoints"))

    def closeOrder(executed: List[String], sagaId: Long) =
      (orderServiceClient.closeOrder(userId, orderId, sagaId.toString) <*
        sagaLogDao.createSagaStep("closeOrder", sagaId, result = None))
        .when(!executed.contains("closeOrder"))

    def refundPayments(executed: List[String], sagaId: Long) =
      (paymentServiceClient.refundPayments(userId, money, sagaId.toString) <*
        sagaLogDao.createSagaStep("refundPayments", sagaId, result = None)).when(!executed.contains("refundPayments"))

    def cancelLoyaltyPoints(executed: List[String], sagaId: Long) =
      (loyaltyPointsServiceClient.cancelLoyaltyPoints(userId, bonuses, sagaId.toString) <*
        sagaLogDao.createSagaStep("cancelLoyaltyPoints", sagaId, result = None))
        .when(!executed.contains("cancelLoyaltyPoints"))

    def reopenOrder(executed: List[String], sagaId: Long) =
      (orderServiceClient.reopenOrder(userId, orderId, sagaId.toString) <*
        sagaLogDao.createSagaStep("reopenOrder", sagaId, result = None))
        .when(!executed.contains("reopenOrder"))

    def saga(sagaId: Long, executedSteps: List[String]) =
      (for {
        _ <- collectPayments(executedSteps, sagaId) compensate refundPayments(executedSteps, sagaId)
        _ <- assignLoyaltyPoints(executedSteps, sagaId) compensate cancelLoyaltyPoints(executedSteps, sagaId)
        _ <- closeOrder(executedSteps, sagaId) compensate reopenOrder(executedSteps, sagaId)
      } yield ()).transact *> sagaLogDao.finishSaga(sagaId)

    val mdcLog = wrapMDC(logger, userId, orderId, sagaIdOpt)
    for {
      _             <- mdcLog.info("Saga execution started")
      sagaId        <- sagaIdOpt.fold(sagaLogDao.startSaga(userId))(ZIO.succeed)
      executed      <- sagaLogDao.listExecutedSteps(sagaId)
      executedSteps = executed.map(_.name)
      _             <- saga(sagaId, executedSteps)
      _             <- mdcLog.info("Saga execution finished")
    } yield ()

  }

  override def recoverSagas: TaskC[Unit] =
    for {
      _     <- logger.info("Sagas recovery stared")
      sagas <- sagaLogDao.listUnfinishedSagas
      _ <- ZIO.foreachParN_(100)(sagas) { sagaInfo =>
            ZIO.fromEither(sagaInfo.data.as[OrderSagaData]).flatMap {
              case OrderSagaData(userId, orderId, money, bonuses) =>
                runSaga(userId, orderId, money, bonuses, Some(sagaInfo.id))
            }
          }
      _ <- logger.info("Sagas recovery finished")
    } yield ()

  private def wrapMDC(logger: StructuredLogger[Task], userId: UUID, orderId: BigInt, sagaIdOpt: Option[Long]) =
    StructuredLogger.withContext(logger)(
      Map("userId" -> userId.toString, "orderId" -> orderId.toString, "sagaId" -> sagaIdOpt.toString)
    )
}

object OrderSagaCoordinatorImpl extends CatsPlatform {
  def apply(paymentServiceClient: PaymentServiceClient,
            loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
            orderServiceClient: OrderServiceClient,
            sagaLogDao: SagaLogDao): Task[OrderSagaCoordinatorImpl] =
    Slf4jLogger
      .create[Task]
      .map(
        new OrderSagaCoordinatorImpl(paymentServiceClient,
                                     loyaltyPointsServiceClient,
                                     orderServiceClient,
                                     sagaLogDao,
                                     _)
      )
}
