package io.chymyst.dhall

import scala.util.{Success, Try}

trait Applicative[F[_]] {
  def zip[A, B](fa: F[A], fb: F[B]): F[(A, B)]

  def map[A, B](f: A => B)(fa: F[A]): F[B]

  def pure[A](a: A): F[A]

}

object Applicative {

  implicit class ApplicativeOps[F[_], A](fa: F[A])(implicit ev: Applicative[F]) {
    def map[B](f: A => B): F[B] = ev.map(f)(fa)

    def zip[B](fb: F[B]): F[(A, B)] = ev.zip(fa, fb)
  }

  def seqOption[F[_]: Applicative, A](t2: Option[F[A]]): F[Option[A]] = t2 match {
    case Some(value) => value.map(Option.apply)
    case None        => Applicative[F].pure(None: Option[A])
  }

  def seqTuple2[F[_]: Applicative, A, B](t2: (F[A], F[B])): F[(A, B)] = t2._1 zip t2._2

  def seqTuple3[F[_]: Applicative, A, B, C](t3: (F[A], F[B], F[C])): F[(A, B, C)] = (t3._1 zip t3._2 zip t3._3).map { case ((a, b), c) => (a, b, c) }

  def seqSeq[F[_]: Applicative, A](fas: Seq[F[A]]): F[Seq[A]] =
    fas.foldLeft(Applicative[F].pure(Seq[A]())) { (prev, fa) => (prev zip fa).map { case (prevSeq, a) => prevSeq :+ a } }

  def apply[F[_]: Applicative]: Applicative[F] = implicitly[Applicative[F]]

  implicit val ApplicativeTry: Applicative[Try] = new Applicative[Try] {
    override def zip[A, B](fa: Try[A], fb: Try[B]): Try[(A, B)] = fa.flatMap { a => fb.map((a, _)) }

    override def map[A, B](f: A => B)(fa: Try[A]): Try[B] = fa.map(f)

    override def pure[A](a: A): Try[A] = Success(a)
  }
}
