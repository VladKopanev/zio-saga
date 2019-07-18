package com.vladkopanev.zio.saga
import zio.TaskR
import zio.clock.Clock

package object example {
  type TaskC[+A] = TaskR[Clock, A]
}
