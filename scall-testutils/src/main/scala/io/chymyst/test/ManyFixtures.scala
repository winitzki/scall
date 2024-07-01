package io.chymyst.test

import scala.util.Try

trait ManyFixtures {

  def failureAndSuccessCounts(totalTests: Int, results: Seq[Try[_]], allowFailures: Int = 0): (Int, Int) = {
    val failures          = results.count(_.isFailure)
    val successes         = results.count(_.isSuccess)
    val unexpectedSuccess = math.max(0, successes - (totalTests - allowFailures))
    println(s"Success count: $successes, failure count: $failures${if (unexpectedSuccess > 0) s" but the success count is $unexpectedSuccess more than expected"
      else ""}")
    (failures, successes)
  }
}
