package zio.saga
import scalaz.zio.TaskR
import scalaz.zio.clock.Clock

package object example {
  type TaskC[+A] = TaskR[Clock, A]
}
