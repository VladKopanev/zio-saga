package zio.saga.example.dao
import java.util.UUID

import scalaz.zio.ZIO
import zio.saga.example.model.SagaStep

trait SagaLogDao {
  def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit]

  def startSaga(initiator: UUID): ZIO[Any, Throwable, Long]

  def createSagaStep(name: String, sagaId: Long): ZIO[Any, Throwable, Unit]

  def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]]
}
