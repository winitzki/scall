package io.chymyst.tc

import io.chymyst.tc.Monoid.MonoidSyntax

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

  def apply[F[_]: Applicative]: Applicative[F]                               = implicitly[Applicative[F]]
  /*
  implicit val ApplicativeTry: Applicative[Try] = new Applicative[Try] {
    override def zip[A, B](fa: Try[A], fb: Try[B]): Try[(A, B)] = fa.flatMap { a => fb.map((a, _)) }

    override def map[A, B](f: A => B)(fa: Try[A]): Try[B] = fa.map(f)

    override def pure[A](a: A): Try[A] = Success(a)
  }
   */
  implicit def eitherMonoidApplicative[E: Monoid]: Applicative[Either[E, *]] = new Applicative[Either[E, *]] {
    override def zip[A, B](fa: Either[E, A], fb: Either[E, B]): Either[E, (A, B)] = (fa, fb) match {
      case (Right(a), Right(b)) => Right((a, b))
      case (Left(ea), Right(_)) => Left(ea)
      case (Right(_), Left(eb)) => Left(eb)
      case (Left(ea), Left(eb)) => Left(ea ++ eb)
    }

    override def map[A, B](f: A => B)(fa: Either[E, A]): Either[E, B] = fa map f

    override def pure[A](a: A): Either[E, A] = Right(a)
  }
}

trait Monoid[M] {
  def empty: M
  def combine(a: M, b: M): M
}

object Monoid {
  type Const[M, A] = M
  implicit def trivialApplicative[M: Monoid]: Applicative[Const[M, *]] = new Applicative[Const[M, *]] {
    override def zip[A, B](fa: Const[M, A], fb: Const[M, B]): Const[M, (A, B)] = fa ++ fb

    override def map[A, B](f: A => B)(fa: Const[M, A]): Const[M, B] = fa

    override def pure[A](a: A): Const[M, A] = Monoid[M].empty
  }

  implicit class MonoidSyntax[M: Monoid](m: M) {
    def ++(other: M): M = implicitly[Monoid[M]].combine(m, other)
  }

  implicit def monoidSeq[A]: Monoid[Seq[A]] = new Monoid[Seq[A]] {
    override def empty: Seq[A] = Seq()

    override def combine(a: Seq[A], b: Seq[A]): Seq[A] = a ++ b
  }

  def apply[M: Monoid]: Monoid[M] = implicitly[Monoid[M]]
}
/*
final case class State[S, A](run: S => (S, A))

object State {
  implicit def applicativeState[S]: Applicative[State[S, *]] = new Applicative[State[S, *]] {
    override def zip[A, B](fa: State[S, A], fb: State[S, B]): State[S, (A, B)] = State { s =>
      val (s1, a) = fa.run(s)
      val (s2, b) = fb.run(s1)
      (s2, (a, b))
    }

    override def map[A, B](f: A => B)(fa: State[S, A]): State[S, B] = ...

    override def pure[A](a: A): State[S, A] = ...
  }
}
 */
