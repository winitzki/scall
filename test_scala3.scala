//> using scala 3.3.1

object Test {
  final case class Fix[+F[+_]](unfix: F[Fix[F]]) // Define a fixpoint of F.

// Define an example of `F` to use with the Fix constructor.
  sealed trait ExampleF[+A]

  object ExampleF {
    final case class Leaf(s: String) extends ExampleF[Nothing]

    final case class Branch[+A](l: A, r: A) extends ExampleF[A]
  }

  import ExampleF._

  def test = {

    // The type Fix[ExampleF] is a binary tree with a String at each leaf.
    // Create some values of this type.
    val x = Fix[ExampleF](Branch(Fix(Leaf("x")), Fix(Leaf("y"))))
    val y = Fix[ExampleF](Branch(x, Fix(Leaf("z"))))
    // Pattern-match on those trees now:
    x match {
      case Fix(Branch(Fix(_), Fix(_))) => true
    } // Returns `true`.

    x match {
      case Fix(Branch(Fix(_), Fix(Leaf(_)))) => true // Causes scalac error with Scala 2.13
    }

    y match {
      case Fix(Branch(Fix(Branch(_, _)), Fix(_))) => true // Causes scalac error with Scala 2.13
    }

  }

//@main
  def main(code: Boolean): Unit = println("Hello")

}
