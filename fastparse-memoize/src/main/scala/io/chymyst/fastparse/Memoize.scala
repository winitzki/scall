package io.chymyst.fastparse

import fastparse.{P, Parsed, ParserInput, ParserInputSource, ParsingRun}
import fastparse.internal.{Instrument, Msgs}

import scala.collection.mutable

/* See discussion in https://github.com/com-lihaoyi/fastparse/discussions/301 */

final case class PRunData( // Copy all the mutable data from ParsingRun.
  terminalMsgs: Msgs,
  aggregateMsgs: Msgs,
  shortMsg: Msgs,
  lastFailureMsg: Msgs,
  failureStack: List[(String, Int)],
  isSuccess: Boolean,
  logDepth: Int,
  index: Int,
  cut: Boolean,
  successValue: Any,
  verboseFailures: Boolean,
  noDropBuffer: Boolean,
  misc: collection.mutable.Map[Any, Any],
) {
  override def toString: String = {
    s"ParsingRun(index=$index, isSuccess = $isSuccess, successValue = $successValue)"
  }

}

object PRunData { // Copy all the mutable data from a parsing run into a PRunData value.
  def ofParsingRun[T](pr: ParsingRun[T]): PRunData = PRunData(
    pr.terminalMsgs,
    pr.aggregateMsgs,
    pr.shortMsg,
    pr.lastFailureMsg,
    pr.failureStack,
    pr.isSuccess,
    pr.logDepth,
    pr.index,
    pr.cut,
    pr.successValue,
    pr.verboseFailures,
    pr.noDropBuffer,
    mutable.Map.from(pr.misc),
  )
}

object Memoize {
  val enable = true

  def assignToParsingRun[T](data: PRunData, pr: ParsingRun[T]): ParsingRun[T] = { // Assign the mutable data to a given ParsingRun value.
    pr.terminalMsgs = data.terminalMsgs
    pr.aggregateMsgs = data.aggregateMsgs
    pr.shortMsg = data.shortMsg
    pr.lastFailureMsg = data.lastFailureMsg
    pr.failureStack = data.failureStack
    pr.isSuccess = data.isSuccess
    pr.logDepth = data.logDepth
    pr.index = data.index
    pr.cut = data.cut
    pr.successValue = data.successValue
    pr.verboseFailures = data.verboseFailures
    pr.noDropBuffer = data.noDropBuffer
    data.misc.foreach { case (k, v) => pr.misc.put(k, v) }
    pr
  }

  @inline private def cacheGrammar[R](cache: mutable.Map[Int, PRunData], parser: => P[_])(implicit p: P[_]): P[R] = {
    // The `parser` has not yet been run! And it is mutable. Do not run it twice!
    val cachedData: PRunData = cache.getOrElseUpdate(p.index, PRunData.ofParsingRun(parser))
    // After the `parser` has been run on `p`, the value of `p` changes and becomes equal to the result of running the parser.
    // If the result was cached, we need to assign it to the current value of `p`. This will imitate the side effect of running the parser again.
    assignToParsingRun(cachedData, p).asInstanceOf[P[R]]
  }

  private val cache = new mutable.HashMap[(sourcecode.File, sourcecode.Line), mutable.Map[Int, PRunData]]

  private def getOrCreateCache(file: sourcecode.File, line: sourcecode.Line): mutable.Map[Int, PRunData] = {
    cache.getOrElseUpdate((file, line), new mutable.HashMap[Int, PRunData])
  }

  implicit class MemoizeParser[A](parser: => P[A]) {
    @inline def memoize(implicit file: sourcecode.File, line: sourcecode.Line, p: P[_]): P[A] = if (enable) {
      val cache: mutable.Map[Int, PRunData] = getOrCreateCache(file, line)
      cacheGrammar(cache, parser)
    } else parser
  }

  def clearAll(): Unit = cache.values.foreach(_.clear())

  def statistics: String = cache.map { case ((file, line), c) => s"$file#$line: ${c.size} entries" }.mkString("\n")

  def parse[T](
    input: ParserInputSource,
    parser: P[_] => P[T],
    verboseFailures: Boolean = false,
    startIndex: Int = 0,
    instrument: Instrument = null,
  ): Parsed[T] = {
    clearAll()
    fastparse.parse(input, parser, verboseFailures, startIndex, instrument)
  }

  def parseInputRaw[T](
    input: ParserInput,
    parser: P[_] => P[T],
    verboseFailures: Boolean = false,
    startIndex: Int = 0,
    traceIndex: Int = -1,
    instrument: Instrument = null,
    enableLogging: Boolean = true,
  ): ParsingRun[T] = {
    clearAll()
    fastparse.parseInputRaw(input, parser, verboseFailures, startIndex, traceIndex, instrument, enableLogging)
  }

}
