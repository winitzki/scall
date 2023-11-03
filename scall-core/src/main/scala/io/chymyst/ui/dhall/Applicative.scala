package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Imports.ImportContext
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme.Import

import scala.util.chaining.scalaUtilChainingOps

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

  def seqOption[F[_] : Applicative, A](t2: Option[F[A]]): F[Option[A]] = t2 match {
    case Some(value) => value.map(Option.apply)
    case None => Applicative[F].pure(None: Option[A])
  }

  def seqTuple2[F[_] : Applicative, A, B](t2: (F[A], F[B])): F[(A, B)] = t2._1 zip t2._2

  def seqTuple3[F[_] : Applicative, A, B, C](t3: (F[A], F[B], F[C])): F[(A, B, C)] = (t3._1 zip t3._2 zip t3._3).map { case ((a, b), c) => (a, b, c) }

  def seqSeq[F[_] : Applicative, A](fas: Seq[F[A]]): F[Seq[A]] =
    fas.foldLeft(Applicative[F].pure(Seq[A]())) { (prev, fa) => (prev zip fa).map { case (prevSeq, a) => prevSeq :+ a } }

  def apply[F[_] : Applicative]: Applicative[F] = implicitly[Applicative[F]]
}

final case class ImportResolutionState(visited: Seq[Import[Expression]] /* non-empty */ , gamma: ImportContext)

final case class ImportResolutionMonad[E](run: ImportResolutionState => (E, ImportResolutionState))

object ImportResolutionMonad {
  implicit val ApplicativeIRMonad: Applicative[ImportResolutionMonad] = new Applicative[ImportResolutionMonad] {
    override def zip[A, B](fa: ImportResolutionMonad[A], fb: ImportResolutionMonad[B]): ImportResolutionMonad[(A, B)] =
      ImportResolutionMonad[(A, B)](initState => fa.run(initState).pipe { case (a, s) => (a, fb.run(s)) }.pipe { case (a, (b, s)) => ((a, b), s) })

    override def map[A, B](f: A => B)(fa: ImportResolutionMonad[A]): ImportResolutionMonad[B] =
      ImportResolutionMonad[B](s => fa.run(s).pipe { case (a, s) => (f(a), s) })

    override def pure[A](a: A): ImportResolutionMonad[A] =
      ImportResolutionMonad[A](s => (a, s))
  }

}
