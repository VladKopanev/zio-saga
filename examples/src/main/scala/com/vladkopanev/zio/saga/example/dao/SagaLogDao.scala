package com.vladkopanev.zio.saga.example.repo

import java.util.UUID

import doobie._
import io.circe.Json
import org.postgresql.util.PGobject
import zio.{ Task, ZIO }
import com.vladkopanev.zio.saga.example.model.{ SagaInfo, SagaStep }

// // @accessible
// // trait GenericRepository {
// //   def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit]
  
// //   def startSaga(initiator: UUID, data: Json): ZIO[Any, Throwable, Long]

// //   def createSagaStep(
// //     name: String,
// //     sagaId: Long,
// //     result: Option[Json],
// //     failure: Option[String] = None
// //   ): ZIO[Any, Throwable, Unit]

// //   def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]]

// //   def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]]
// }

import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.util.transactor.Transactor
import zio.Task





