package zio.saga.example

import java.util.UUID

import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Schedule, Task, ZIO }
import zio.saga.example.client.{ LoyaltyPointsServiceClient, OrderServiceClient, PaymentServiceClient }
import zio.saga.example.dao.SagaLogDao
import zio.saga.example.model.{ OrderSagaData, OrderSagaError, SagaStep }

import scala.concurrent.TimeoutException

trait OrderSagaCoordinator {
  def runSaga(userId: UUID, orderId: BigInt, money: BigDecimal, bonuses: Double, sagaIdOpt: Option[Long]): TaskC[Unit]

  def recoverSagas: TaskC[Unit]
}

class OrderSagaCoordinatorImpl(
  paymentServiceClient: PaymentServiceClient,
  loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
  orderServiceClient: OrderServiceClient,
  sagaLogDao: SagaLogDao,
  maxRequestTimeout: Int,
  logger: StructuredLogger[Task]
) extends OrderSagaCoordinator {

  import zio.saga.Saga._

  def runSaga(
    userId: UUID,
    orderId: BigInt,
    money: BigDecimal,
    bonuses: Double,
    sagaIdOpt: Option[Long]
  ): TaskC[Unit] = {

    import scalaz.zio.duration._

    def mkSagaRequest(
      request: TaskC[Unit],
      sagaId: Long,
      stepName: String,
      executedSteps: List[SagaStep],
      compensating: Boolean = false
    ) =
      ZIO.fromOption(
          executedSteps.find(step => step.name == stepName && !compensating).flatMap(_.failure).map(new OrderSagaError(_))
        ).flip *> request
          .timeoutFail(new TimeoutException(s"Execution Timeout occurred for $stepName step"))(maxRequestTimeout.seconds)
          .tapBoth(
            e => sagaLogDao.createSagaStep(stepName, sagaId, result = None, failure = Some(e.getMessage)),
            _ => sagaLogDao.createSagaStep(stepName, sagaId, result = None)
          )
          .when(!executedSteps.exists(_.name == stepName))

    def collectPayments(executed: List[SagaStep], sagaId: Long) = mkSagaRequest(
      paymentServiceClient.collectPayments(userId, money, sagaId.toString),
      sagaId,
      "collectPayments",
      executed
    )

    def assignLoyaltyPoints(executed: List[SagaStep], sagaId: Long) = mkSagaRequest(
      loyaltyPointsServiceClient.assignLoyaltyPoints(userId, bonuses, sagaId.toString),
      sagaId,
      "assignLoyaltyPoints",
      executed
    )

    def closeOrder(executed: List[SagaStep], sagaId: Long) =
      mkSagaRequest(orderServiceClient.closeOrder(userId, orderId, sagaId.toString), sagaId, "closeOrder", executed)

    def refundPayments(executed: List[SagaStep], sagaId: Long) = mkSagaRequest(
      paymentServiceClient.refundPayments(userId, money, sagaId.toString),
      sagaId,
      "refundPayments",
      executed,
      compensating = true
    )

    def cancelLoyaltyPoints(executed: List[SagaStep], sagaId: Long) = mkSagaRequest(
      loyaltyPointsServiceClient.cancelLoyaltyPoints(userId, bonuses, sagaId.toString),
      sagaId,
      "cancelLoyaltyPoints",
      executed,
      compensating = true
    )

    def reopenOrder(executed: List[SagaStep], sagaId: Long) =
      mkSagaRequest(
        orderServiceClient.reopenOrder(userId, orderId, sagaId.toString),
        sagaId,
        "reopenOrder",
        executed,
        compensating = true
      )

    val expSchedule = Schedule.exponential(1.second)
    def buildSaga(sagaId: Long, executedSteps: List[SagaStep]) =
      for {
        _ <- collectPayments(executedSteps, sagaId) retryableCompensate (refundPayments(executedSteps, sagaId), expSchedule)
        _ <- assignLoyaltyPoints(executedSteps, sagaId) retryableCompensate (cancelLoyaltyPoints(executedSteps, sagaId), expSchedule)
        _ <- closeOrder(executedSteps, sagaId) retryableCompensate (reopenOrder(executedSteps, sagaId), expSchedule)
      } yield ()

    import io.circe.syntax._

    val mdcLog = wrapMDC(logger, userId, orderId, sagaIdOpt)
    val data   = OrderSagaData(userId, orderId, money, bonuses).asJson

    for {
      _        <- mdcLog.info("Saga execution started")
      sagaId   <- sagaIdOpt.fold(sagaLogDao.startSaga(userId, data))(ZIO.succeed)
      executed <- sagaLogDao.listExecutedSteps(sagaId)
      _ <- buildSaga(sagaId, executed).transact.tapBoth(
        {
          case e: OrderSagaError => sagaLogDao.finishSaga(sagaId)
          case _ => ZIO.unit
        },
        _ => sagaLogDao.finishSaga(sagaId)
      )
      _ <- mdcLog.info("Saga execution finished")
    } yield ()

  }

  override def recoverSagas: TaskC[Unit] =
    for {
      _     <- logger.info("Sagas recovery stared")
      sagas <- sagaLogDao.listUnfinishedSagas
      _     <- logger.info(s"Found unfinished sagas: $sagas")
      _ <- ZIO.foreachParN_(100)(sagas) { sagaInfo =>
            ZIO.fromEither(sagaInfo.data.as[OrderSagaData]).flatMap {
              case OrderSagaData(userId, orderId, money, bonuses) =>
                runSaga(userId, orderId, money, bonuses, Some(sagaInfo.id)).catchSome {
                  case e: OrderSagaError => ZIO.unit
                }
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
  def apply(
    paymentServiceClient: PaymentServiceClient,
    loyaltyPointsServiceClient: LoyaltyPointsServiceClient,
    orderServiceClient: OrderServiceClient,
    sagaLogDao: SagaLogDao,
    maxRequestTimeout: Int
  ): Task[OrderSagaCoordinatorImpl] =
    Slf4jLogger
      .create[Task]
      .map(
        new OrderSagaCoordinatorImpl(
          paymentServiceClient,
          loyaltyPointsServiceClient,
          orderServiceClient,
          sagaLogDao,
          maxRequestTimeout,
          _
        )
      )
}
