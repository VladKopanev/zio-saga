package zio.saga.example.model
import java.time.Instant

case class SagaStep(sagaId: Long, name: String, startedAt: Instant, result: Option[String])
