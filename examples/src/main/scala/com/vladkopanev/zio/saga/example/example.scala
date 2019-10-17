package com.vladkopanev.zio.saga
import zio.RIO
import zio.clock.Clock

package object example {
  type TaskC[+A] = RIO[Clock, A]
}
