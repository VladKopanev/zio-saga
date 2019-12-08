package com.vladkopanev.zio.saga.example.dao

import java.util.UUID

import doobie._
import doobie.implicits._

import com.vladkopanev.zio.saga.example.model.{ SagaInfo, SagaStep }
import io.circe.Json
import org.postgresql.util.PGobject
import zio.{ Task, ZIO }

trait SagaLogDao {
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

class SagaLogDaoImpl extends SagaLogDao {
  import doobie._
  import doobie.implicits._
  import doobie.postgres.implicits._
  import zio.interop.catz._

  val xa = Transactor.fromDriverManager[Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:Saga",
    "postgres",
    "root"
  )
  implicit val han = LogHandler.jdkLogHandler

  override def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit] =
    SQL
      .finishSaga(sagaId)
      .run
      .transact(xa)
      .unit
      .orDie

  override def startSaga(initiator: UUID, data: Json): ZIO[Any, Throwable, Long] =
    SQL
      .startSaga(initiator, data)
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)

  override def createSagaStep(
    name: String,
    sagaId: Long,
    result: Option[Json],
    failure: Option[String]
  ): ZIO[Any, Throwable, Unit] =
    SQL
      .createStep(name, sagaId, result, failure)
      .run
      .transact(xa)
      .unit

  override def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]] =
    SQL
      .listExecutedSteps(sagaId)
      .to[List]
      .transact(xa)

  override def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]] =
    SQL.listUnfinishedSagas
      .to[List]
      .transact(xa)
}

object SQL {
  import doobie._
  import doobie.implicits._
  import doobie.postgres.implicits._
  import zio.interop.catz._
  import io.circe.Json

  def finishSaga(sagaId: Long): Update0 =
    sql"""UPDATE saga SET "finishedAt" = now() WHERE id = $sagaId""".update

  def startSaga(initiator: UUID, data: Json): Update0 =
    sql"""INSERT INTO saga("initiator", "createdAt", "finishedAt", "data", "type") 
          VALUES ($initiator, now(), null, $data, 'order')""".update

  def createStep(
    name: String,
    sagaId: Long,
    result: Option[Json],
    failure: Option[String]
  ): Update0 =
    sql"""INSERT INTO saga_step("sagaId", "name", "result", "finishedAt", "failure")
                  VALUES ($sagaId, $name, $result, now(), $failure)""".update

  def listExecutedSteps(sagaId: Long): Query0[SagaStep] =
    sql"""SELECT "sagaId", "name", "finishedAt", "result", "failure"
          from saga_step WHERE "sagaId" = $sagaId""".query[SagaStep]

  def listUnfinishedSagas: Query0[SagaInfo] =
    sql"""SELECT "id", "initiator", "createdAt", "finishedAt", "data", "type"
          from saga s WHERE "finishedAt" IS NULL""".query[SagaInfo]

  implicit lazy val JsonMeta: Meta[Json] = {
    import io.circe.parser._
    Meta.Advanced
      .other[PGobject]("jsonb")
      .timap[Json](
        pgObj => parse(pgObj.getValue).fold(e => sys.error(e.message), identity)
      )(
        json => {
          val pgObj = new PGobject
          pgObj.setType("jsonb")
          pgObj.setValue(json.noSpaces)
          pgObj
        }
      )
  }
}
