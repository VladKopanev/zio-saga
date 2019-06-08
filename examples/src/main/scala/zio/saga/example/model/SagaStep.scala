package zio.saga.example.model
import java.time.Instant

import io.circe.Json

case class SagaStep(sagaId: Long, name: String, finishedAt: Instant, result: Option[Json], failure: Option[String])
