package io.chymyst.dhall.codec

class Lazy[A](a: => A) {
  lazy val value: A = a

  def map[B](f: A => B): Lazy[B] = new Lazy(f(value))

  def flatMap[B](f: A => Lazy[B]): Lazy[B] = new Lazy(f(value).value)

  def zip[B](other: Lazy[B]): Lazy[(A, B)] = new Lazy((value, other.value))
}
