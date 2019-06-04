package zio.saga.example

import scalaz.zio.{App, ZIO}

object SagaApp extends App {

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] = {

    ZIO.succeed(1)
  }
}
