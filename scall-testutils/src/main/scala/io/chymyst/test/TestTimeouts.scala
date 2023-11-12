package io.chymyst.test

import scala.concurrent.duration.Duration

trait TestTimeouts {
  def timeout[R](duration: Duration)(code: => R): R = {
    code // TODO: implement timeouts with a 1-thread executor
  }

  def elapsedNanos[R](code: => R): (R, Long) = {
    val initNanos = System.nanoTime()
    val result = code
    val finalNanos = System.nanoTime()
    (result, finalNanos - initNanos)
  }
}
