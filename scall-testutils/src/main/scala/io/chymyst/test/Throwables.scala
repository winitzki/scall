package io.chymyst.test

import java.io.{PrintWriter, StringWriter}

object Throwables {
  def printThrowable(t: Throwable): String = {
    val stackTrace = new StringWriter
    t.printStackTrace(new PrintWriter(stackTrace))
    stackTrace.flush()
    // No need to print the message because the stack trace already contains that.
    stackTrace.toString
  }
}
