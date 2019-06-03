package zio.saga.example.model
import java.time.Instant

case class SagaStep(name: String, sagaId: Long, startedAt: Instant)
