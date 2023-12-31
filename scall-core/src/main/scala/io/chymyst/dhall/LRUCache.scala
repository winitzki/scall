package io.chymyst.dhall

import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.dhall.TypeCheck.KnownVars

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.IteratorHasAsScala

final case class AllCaches(
  alpha: IdempotentCache[Expression],
  beta: IdempotentCache[Expression],
  gamma: ObservedCache[(KnownVars, ExpressionScheme[Expression]), TypecheckResult[Expression]],
)

object AllCaches {
  def apply(alphaCacheSize: Int, betaCacheSize: Int, gammaCacheSize: Int): AllCaches = AllCaches(
    alpha = IdempotentCache("Alpha-normalization cache", ObservedCache.createCache[Expression, Expression](Option(alphaCacheSize).filter(_ >= 0))),
    beta = IdempotentCache("Beta-normalization cache", ObservedCache.createCache[Expression, Expression](Option(betaCacheSize).filter(_ >= 0))),
    gamma = new ObservedCache(
      "Type-checking cache",
      ObservedCache.createCache[(KnownVars, ExpressionScheme[Expression]), TypecheckResult[Expression]](Option(gammaCacheSize).filter(_ >= 0)),
    ),
  )

  def default = apply(2000000, 2000000, 1000000)
}

final case class LRUCache[K, V](maxSize: Int) extends mutable.Map[K, V] {
  private val lruCache = new util.LinkedHashMap[K, V](maxSize * 4 / 3, 0.75f, true) {
    override def removeEldestEntry(eldest: util.Map.Entry[K, V]): Boolean = size > maxSize
  }

  override def get(key: K): Option[V] = Option(lruCache.get(key))

  override def addOne(elem: (K, V)): LRUCache.this.type = {
    lruCache.put(elem._1, elem._2)
    this
  }

  override def iterator: Iterator[(K, V)] = lruCache.entrySet.iterator.asScala.map(entry => (entry.getKey, entry.getValue))

  override def subtractOne(elem: K): LRUCache.this.type = {
    lruCache.remove(elem)
    this
  }
}

class ObservedCache[A, B](val name: String, cache: mutable.Map[A, B]) {
  protected var requests: Long = 0

  protected var hits: Long = 0

  val step = 100000

  final def get(key: A): Option[B] = cache.get(key)

  def getOrElseUpdate(key: A, default: => B): B = this.synchronized {
    requests += 1
    if (requests > 1 && requests % step == 0)
      println(s"INFO $name processed ${requests / 1000}K requests with $percentHits% cache hits, this request is for key = ${key match {
          case e: Expression => e.print
          case _             => key
        }}")
    if (cache contains key) hits += 1
    cache.getOrElseUpdate(key, default)
  }

  def percentHits = f"${hits.toDouble * 100 / (if (requests > 0) requests else 1).toDouble}%2.2f"

  def statistics: String = this.synchronized(s"Total requests: $requests, cache hits: $percentHits%, total cache size: ${cache.size}")
}

final case class IdempotentCache[A](override val name: String, cache: mutable.Map[A, A]) extends ObservedCache[A, A](name, cache) {
  override def getOrElseUpdate(key: A, default: => A): A = this.synchronized {
    val result = super.getOrElseUpdate(key, default)
    cache.put(result, result) // The cached operation is assumed to be idempotent. Do not use `default` here because we want to avoid computing it if possible.
    result
  }
}

object ObservedCache {
  def createCache[A, B](maybeSize: Option[Int]): mutable.Map[A, B] = maybeSize match {
    case Some(maxSize) => new LRUCache[A, B](maxSize)
    case None          => mutable.Map[A, B]()
  }
}
