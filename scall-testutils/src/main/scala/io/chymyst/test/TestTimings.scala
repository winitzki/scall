package io.chymyst.test

trait TestTimings {

  def elapsedNanos[R](code: => R): (R, Long) = {
    val initNanos  = System.nanoTime()
    val result     = code
    val finalNanos = System.nanoTime()
    (result, finalNanos - initNanos)
  }

}
