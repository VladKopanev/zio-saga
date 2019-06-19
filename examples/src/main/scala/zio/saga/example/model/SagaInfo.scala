package zio.saga.example.model
import java.time.Instant
import java.util.UUID

import io.circe.Json

case class SagaInfo(id: Long,
                    initiator: UUID,
                    createdAt: Instant,
                    finishedAt: Option[Instant],
                    data: Json,
                    `type`: String)

case class OrderSagaData(userId: UUID, orderId: BigInt, money: BigDecimal, bonuses: Double)

object OrderSagaData {
  import io.circe._, io.circe.generic.semiauto._
  implicit val decoder: Decoder[OrderSagaData] = deriveDecoder[OrderSagaData]
  implicit val encoder: Encoder[OrderSagaData] = deriveEncoder[OrderSagaData]
}
