package io.chymyst.dhall.codec

import java.util.concurrent.atomic.AtomicBoolean

class Lazy[A](a: => A) {
  private val strictness  = new AtomicBoolean(false)

  def isStrict: Boolean = strictness.get

  lazy val value: A = {
    strictness set true
    a
  }

  def map[B](f: A => B): Lazy[B] = new Lazy(f(value))

  def flatMap[B](f: A => Lazy[B]): Lazy[B] = new Lazy(f(value).value)

  def zip[B](other: Lazy[B]): Lazy[(A, B)] = new Lazy((value, other.value))
}
