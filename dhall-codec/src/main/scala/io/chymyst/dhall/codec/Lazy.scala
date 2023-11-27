package io.chymyst.dhall.codec

import io.chymyst.dhall.Applicative

import java.util.concurrent.atomic.AtomicBoolean

class Lazy[+A](a: => A) {
  private val strictness = new AtomicBoolean(false)

  def isStrict: Boolean = strictness.get

  lazy val value: A = {
    strictness set true
    a
  }

  def map[B](f: A => B): Lazy[B] = new Lazy(f(value))

  def flatMap[B](f: A => Lazy[B]): Lazy[B] = new Lazy(f(value).value)

  def zip[B](other: Lazy[B]): Lazy[(A, B)] = new Lazy((value, other.value))
}

object Lazy {
  def apply[A](a: => A): Lazy[A] = new Lazy(a)

  def strict[A](a: A): Lazy[A] = {
    val result = new Lazy(a)
    result.value
    result
  }

  implicit val applicativeLazy: Applicative[Lazy] = new Applicative[Lazy] {
    override def zip[A, B](fa: Lazy[A], fb: Lazy[B]): Lazy[(A, B)] = fa zip fb

    override def map[A, B](f: A => B)(fa: Lazy[A]): Lazy[B] = fa map f

    override def pure[A](a: A): Lazy[A] = Lazy.strict(a)
  }
}
