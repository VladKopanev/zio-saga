package zio.saga.example.dao
import java.util.UUID

import io.circe.Json
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }
import zio.saga.example.model.{ SagaInfo, SagaStep }

trait SagaLogDao {
  def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit]

  def startSaga(initiator: UUID): ZIO[Any, Throwable, Long]

  def createSagaStep(name: String, sagaId: Long, result: Option[Json]): ZIO[Any, Throwable, Unit]

  def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]]

  def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]]
}

class SagaLogDaoImpl extends CatsPlatform with SagaLogDao {
  import doobie._
  import doobie.implicits._

  val xa = Transactor.fromDriverManager[Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:localhost",
    "postgres",
    "root"
  )

  override def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit] =
    sql"""UPDATE saga  SET "finishedAt" = now() WHERE id = $sagaId""".update.run.transact(xa).unit

  override def startSaga(initiator: UUID): ZIO[Any, Throwable, Long] =
    sql"""INSERT INTO saga("initiator", "createdAt", "finishedAt", "type") VALUES (initiator, now(), null, 'order')"""
      .update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)

  override def createSagaStep(name: String, sagaId: Long, result: Option[Json]): ZIO[Any, Throwable, Unit] =
    sql"""INSERT INTO saga_step("sagaId", "name", "result") VALUES ($sagaId, $name, $result) ON CONFLICT DO NOTHING"""
      .update
      .run
      .transact(xa)
      .unit

  override def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]] =
    sql"""SELECT * from saga_step WHERE "sagaId" = $sagaId""".query[SagaStep].to[List].transact(xa)

  override def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]] =
    sql"""SELECT * from saga s WHERE "finishedAt" IS NULL""".query[SagaInfo].to[List].transact(xa)
}
