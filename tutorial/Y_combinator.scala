//> using scala 3.4.1

// Recursive type defined with a contravariant pattern functor!
// The type Bad[R] is the solution of the type equation B = B → R.
final case class Bad[R](run: Bad[R] => R):
  def apply(other: Bad[R]): R = run(other)

// The type Bad allows us to write and type-check its self-application, like x(x).

// Implement a general fixpoint combinator (without explicit recursion).
// This will always create an infinite loop because we do not properly implement lazy function arguments.
def fixpoint[R](f : R => R): R = { // Y f = ( λ(x : Bad) → f (x x) ) ( λ(x : Bad) → f (x x) )
  val y : Bad[R] = Bad { (x : Bad[R]) => f (x(x) ) }
  y(y)
}

@main
def main(): Unit =
  val result: Boolean = fixpoint[Boolean](x => !x)
