package zio.saga.example
import scalaz.zio.{Task, ZIO}

import scala.util.Random

package object client {

  import scalaz.zio.duration._

  def randomSleep: TaskC[Unit] =
    for {
      randomSeconds <- ZIO.effectTotal(Random.nextInt(10))
      _             <- ZIO.sleep(randomSeconds.seconds)
    } yield ()

  def randomSleep(maxTimeout: Int): TaskC[Unit] =
    for {
      randomSeconds <- ZIO.effectTotal(Random.nextInt(maxTimeout))
      _             <- ZIO.sleep(randomSeconds.seconds)
    } yield ()

  def randomFail(operationName: String): Task[Unit] =
    for {
      randomInt <- ZIO.effectTotal(Random.nextInt(100))
      _ <- if(randomInt % 10 == 0) ZIO.fail(new RuntimeException(s"Failed to execute $operationName")) else ZIO.unit
    } yield ()
}
