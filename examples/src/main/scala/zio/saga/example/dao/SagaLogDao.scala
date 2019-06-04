package zio.saga.example.dao
import java.util.UUID

import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }
import zio.saga.example.model.SagaStep

trait SagaLogDao {
  def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit]

  def startSaga(initiator: UUID): ZIO[Any, Throwable, Long]

  def createSagaStep(name: String, sagaId: Long): ZIO[Any, Throwable, Unit]

  def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]]
}

class SagaLogDaoImpl extends CatsPlatform with SagaLogDao {
  import doobie._
  import doobie.implicits._

  val xa = Transactor.fromDriverManager[Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:world",
    "postgres",
    ""
  )

  override def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit] =
    sql"""UPDATE saga  SET "finishedAt" = now() WHERE id = $sagaId""".update.run.transact(xa).unit

  override def startSaga(initiator: UUID): ZIO[Any, Throwable, Long] =
    sql"""INSERT INTO saga("initiator", "createdAt", "finishedAt") VALUES (initiator, now(), null)""".update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)

  override def createSagaStep(name: String, sagaId: Long): ZIO[Any, Throwable, Unit] =
    sql"""INSERT INTO saga_step("saga_id", "name", "result") VALUES ($sagaId, $name, null)""".update.run
      .transact(xa)
      .unit

  override def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]] =
    sql"SELECT  * from saga_step WHERE saga_id = $sagaId".query[SagaStep].to[Task]
}
