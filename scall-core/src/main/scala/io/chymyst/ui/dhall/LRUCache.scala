package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.ui.dhall.TypeCheck.Gamma

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.IteratorHasAsScala


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

