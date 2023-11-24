package io.chymyst.dhall.unit

import io.chymyst.dhall.Grammar
import io.chymyst.dhall.Grammar
import io.chymyst.dhall.unit.TestFixtures._
import io.chymyst.dhall.unit.TestUtils._
import munit.FunSuite

class ParserTest2 extends FunSuite {

  test("import_only") {
    importExpressions.foreach { case (s, d) =>
      check(Grammar.import_only(_), s, d, s.length)
    }
  }

  test("import_hashed") {
    import io.chymyst.dhall.Grammar
    import io.chymyst.dhall.SyntaxConstants.FilePath
    import io.chymyst.dhall.SyntaxConstants.FilePrefix.Here
    import io.chymyst.dhall.SyntaxConstants.ImportType.Path

    check(Grammar.import_hashed(_), s"./local/import sha256:$sha256example", (Path(Here, FilePath(List("local", "import"))), Some(sha256example)), 86)
  }

  test("import_expression") {
    check(primitiveExpressions ++ selectorExpressions ++ completionExpressions ++ importExpressions, Grammar.import_expression(_))
  }

  test("plus_expression") {
    check(primitiveExpressions ++ selectorExpressions ++ completionExpressions ++ importExpressions ++ plusExpressions, Grammar.plus_expression(_))
  }

  test("primitive_expression") {
    check(recordExpressions ++ primitiveExpressions, Grammar.primitive_expression(_))
  }

  test("let_binding") {
    check(letBindings, Grammar.let_binding(_))
  }

  test("expression_let_binding") {
    check(letBindingExpressions, Grammar.expression_let_binding(_))
  }

  test("interpolation") {
    check(interpolationExpressions, Grammar.interpolation(_))
  }

  test("double-quoted text with interpolations") {
    check(doubleQuotedExpressions, Grammar.double_quote_literal(_))
  }

  test("single-quoted multiline text with interpolations") {
    check(singleQuotedExpressions, Grammar.single_quote_literal(_))
  }

  test("text_literal") {
    check(singleQuotedExpressions ++ doubleQuotedExpressions, Grammar.text_literal(_))
  }

}
