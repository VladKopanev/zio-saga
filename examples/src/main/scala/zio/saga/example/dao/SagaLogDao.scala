package zio.saga.example.dao
import java.util.UUID

import io.circe.Json
import org.postgresql.util.PGobject
import scalaz.zio.interop.CatsPlatform
import scalaz.zio.{ Task, ZIO }
import zio.saga.example.model.{ SagaInfo, SagaStep }

trait SagaLogDao {
  def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit]

  def startSaga(initiator: UUID, data: Json): ZIO[Any, Throwable, Long]

  def createSagaStep(name: String, sagaId: Long, result: Option[Json]): ZIO[Any, Throwable, Unit]

  def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]]

  def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]]
}

class SagaLogDaoImpl extends CatsPlatform with SagaLogDao {
  import doobie._
  import doobie.implicits._
  import doobie.postgres.implicits._

  val xa = Transactor.fromDriverManager[Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:Saga",
    "postgres",
    "root"
  )

  override def finishSaga(sagaId: Long): ZIO[Any, Throwable, Unit] =
    sql"""UPDATE saga  SET "finishedAt" = now() WHERE id = $sagaId""".update.run.transact(xa).unit

  override def startSaga(initiator: UUID, data: Json): ZIO[Any, Throwable, Long] =
    sql"""INSERT INTO saga("initiator", "createdAt", "finishedAt", "data", "type") VALUES ($initiator, now(), null, $data, 'order')""".update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)

  override def createSagaStep(name: String, sagaId: Long, result: Option[Json]): ZIO[Any, Throwable, Unit] =
    sql"""INSERT INTO saga_step("sagaId", "name", "result", "finishedAt") VALUES ($sagaId, $name, $result, now()) ON CONFLICT DO NOTHING""".update.run
      .transact(xa)
      .unit

  override def listExecutedSteps(sagaId: Long): ZIO[Any, Throwable, List[SagaStep]] =
    sql"""SELECT * from saga_step WHERE "sagaId" = $sagaId""".query[SagaStep].to[List].transact(xa)

  override def listUnfinishedSagas: ZIO[Any, Throwable, List[SagaInfo]] =
    sql"""SELECT * from saga s WHERE "finishedAt" IS NULL""".query[SagaInfo].to[List].transact(xa)

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
