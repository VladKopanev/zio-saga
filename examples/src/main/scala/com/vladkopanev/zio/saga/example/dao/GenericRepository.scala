package com.vladkopanev.zio.saga.example.repo

import java.util.UUID
import io.circe.Json
import zio.{ ZIO }
import com.vladkopanev.zio.saga.example.model.{ SagaInfo, SagaStep }

// @accessible
trait GenericRepository {
  def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit]

  def startSaga(initiator: UUID, data: Json): ZIO[Any, Throwable, Long]

  def createSagaStep(
    name: String,
    sagaId: Long,
    result: Option[Json],
    failure: Option[String] = None
  ): ZIO[Any, Throwable, Unit]

  def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]]

  def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]]
}
