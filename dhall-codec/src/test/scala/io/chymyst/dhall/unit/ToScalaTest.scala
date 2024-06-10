package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.codec.ToScala
import munit.FunSuite

class ToScalaTest extends FunSuite {

  test("convert simple Dhall expressions to Scala source code") {
    Map(
      "1.23"                      -> "Expression(DoubleLiteral(1.23))",
      "123"                       -> "Expression(NaturalLiteral(BigInt(123)))",
      "-123"                      -> "Expression(IntegerLiteral(BigInt(-123)))",
      "\\(x: Natural) -> x + 1"   -> "Expression(Lambda(VarName(\"x\"), Expression(ExprBuiltin(Builtin.Natural)),\n  Expression(ExprOperator(Expression(Variable(VarName(\"x\"), BigInt(0))), Operator.Plus, Expression(NaturalLiteral(BigInt(1)))))))",
      "{ x : Integer, y : Bool }" -> "Expression(RecordType(List((FieldName(\"x\"), Expression(ExprBuiltin(Builtin.Integer))), (FieldName(\"y\"), Expression(ExprBuiltin(Builtin.Bool))))))",
      "let x = 1 in x"            -> "\nExpression(Let(VarName(\"x\"), None, Expression(NaturalLiteral(BigInt(1))), Expression(Variable(VarName(\"x\"), BigInt(0)))))",
      "[1, 2, 3]"                 -> "Expression(NonEmptyList(Seq(Expression(NaturalLiteral(BigInt(1))), Expression(NaturalLiteral(BigInt(2))), Expression(NaturalLiteral(BigInt(3))))))",
      "{ x = 1, y = True }"       -> "Expression(RecordLiteral(List((FieldName(\"x\"), Expression(NaturalLiteral(BigInt(1)))), (FieldName(\"y\"), Expression(ExprConstant(Constant.True))))))",
      "< Nil | Cons : Bool > "    -> "Expression(UnionType(List((ConstructorName(\"Cons\"), Some(Expression(ExprBuiltin(Builtin.Bool)))), (ConstructorName(\"Nil\"), None))))",
      "let x = \"abc\" in y"      -> "\nExpression(Let(VarName(\"x\"), None, Expression(TextLiteral(List(), \"\"\"abc\"\"\")), Expression(Variable(VarName(\"y\"), BigInt(0)))))",
    ).foreach { case (input, output) =>
      val x = ToScala.printSourceCodeTC(input.dhall).result
      expect(x == output)
    }
  }

}
