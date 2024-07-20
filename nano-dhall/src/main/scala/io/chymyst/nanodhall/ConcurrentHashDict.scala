package io.chymyst.dhall

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

// Each distinct value of type A is mapped to a unique `Int` key.

trait HashDict[A] {
  def lookup(key: Int): Option[A]
  def store(value: A): Int
}

class ConcurrentHashDict[A](maxSize: Int) extends HashDict[A] {
  private val valueDict: ConcurrentMap[Int, A] = new ConcurrentHashMap[Int, A]
  private val keyDict: ConcurrentMap[A, Int]   = new ConcurrentHashMap[A, Int]

  override def lookup(key: Int): Option[A] = Option(valueDict.get(key))

  override def store(value: A): Int = {
    val key = keyDict.computeIfAbsent(value, _ => valueDict.size + 1)
    valueDict.put(key, value)
    key
  }
}
