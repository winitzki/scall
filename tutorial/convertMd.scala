//> using dep com.lihaoyi::fastparse:3.1.0

//> using scala 3.4.1

import fastparse.*
import fastparse.NoWhitespace.*

def end_of_line[$: P] = P("\n" | "\r\n")

def space[$: P] = P(CharIn(" \t").rep(1))

def not_end_of_line[$: P] = P(
  CharIn("\u0020-\u007F") | valid_non_ascii | tab
)

def valid_non_ascii[$: P] = P(
  CharIn(
    "\u0080-\uD7FF",
    // %xD800_DFFF = surrogate pairs
    //      "\uE000-\uFFFC",
    "\uE000-\uFFFC", // Workaround: Disallow the "replacement" character ("\uFFFD") because it will be generated for invalid utf-8 encodings.
  )
    // Encode other Unicode ranges into Java's UTF-16 using UTF-16 surrogates.
    // See https://www.cogsci.ed.ac.uk/~richard/utf-8.cgi?input=10000&mode=hex and look for "UTF-16 surrogates".
    // %xFFFE_FFFF = non_characters
    //        | % x10000_1FFFD
    // U+10000 = "\uD800\uDC00"
    // U+103FF = "\uD800\uDFFF"
    // U+10400 = "\uD01\uDC00"
    // U+1FFFD = "\uD83F\uDFFD"
    // format: off
    | (CharIn("\uD800-\uD83E") ~ CharIn("\uDC00-\uDFFF"))
    // format: on
    | (CharIn("\uD83F") ~ CharIn("\uDC00-\uDFFD"))
    //      // %x1FFFE_1FFFF = non_characters
    //      | % x20000_2FFFD   // U+20000 = \uD840\uDC00
    | (CharIn("\uD840-\uD87E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD87F") ~ CharIn("\uDC00-\uDFFD"))
    //        // %x2FFFE_2FFFF = non_characters
    //        | % x30000_3FFFD
    | (CharIn("\uD880-\uD8BE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD8BF") ~ CharIn("\uDC00-\uDFFD"))
    //      // %x3FFFE_3FFFF = non_characters
    //      | % x40000_4FFFD
    | (CharIn("\uD8C0-\uD8FE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD8FF") ~ CharIn("\uDC00-\uDFFD"))
    //        // %x4FFFE_4FFFF = non_characters
    //        | % x50000_5FFFD
    | (CharIn("\uD900-\uD93E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD93F") ~ CharIn("\uDC00-\uDFFD"))
    //      // %x5FFFE_5FFFF = non_characters
    //      | % x60000_6FFFD
    | (CharIn("\uD940-\uD97E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD97F") ~ CharIn("\uDC00-\uDFFD"))
    //        // %x6FFFE_6FFFF = non_characters
    //        | % x70000_7FFFD
    | (CharIn("\uD980-\uD9BE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD9BF") ~ CharIn("\uDC00-\uDFFD"))
    //      // %x7FFFE_7FFFF = non_characters
    //      | % x80000_8FFFD
    | (CharIn("\uD9C0-\uD9FE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uD9FF") ~ CharIn("\uDC00-\uDFFD"))
    //        // %x8FFFE_8FFFF = non_characters
    //        | % x90000_9FFFD
    | (CharIn("\uDA00-\uDA3E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDA3F") ~ CharIn("\uDC00-\uDFFD"))
    //      // %x9FFFE_9FFFF = non_characters
    //      | % xA0000_AFFFD
    | (CharIn("\uDA40-\uDA7E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDA7F") ~ CharIn("\uDC00-\uDFFD"))
    //        // %xAFFFE_AFFFF = non_characters
    //        | % xB0000_BFFFD
    | (CharIn("\uDA80-\uDABE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDABF") ~ CharIn("\uDC00-\uDFFD"))
    //      // %xBFFFE_BFFFF = non_characters
    //      | % xC0000_CFFFD
    | (CharIn("\uDAC0-\uDAFE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDAFF") ~ CharIn("\uDC00-\uDFFD"))
    //        // %xCFFFE_CFFFF = non_characters
    //        | % xD0000_DFFFD
    | (CharIn("\uDB00-\uDB3E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDB3F") ~ CharIn("\uDC00-\uDFFD"))
    //      // %xDFFFE_DFFFF = non_characters
    //      | % xE0000_EFFFD
    | (CharIn("\uDB40-\uDB7E") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDB7F") ~ CharIn("\uDC00-\uDFFD"))
    //        // %xEFFFE_EFFFF = non_characters
    //        | % xF0000_FFFFD
    | (CharIn("\uDB80-\uDBBE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDBBF") ~ CharIn("\uDC00-\uDFFD"))
    // U+F0000 = "\uDB80\uDC00"
    // U+FFFFD = "\uDBBF\uDFFD"
    //      // %xFFFFE_FFFFF = non_characters
    //      | % x100000_10FFFD
    | (CharIn("\uDBC0-\uDBFE") ~ CharIn("\uDC00-\uDFFF"))
    | (CharIn("\uDBFF") ~ CharIn("\uDC00-\uDFFD"))
  // U+100000 = "\uDBC0\uDC00"
  // U+10FFFD = "\uDBFF\uDFFD"
  // %x10FFFE_10FFFF = non_characters
)

def tab[$: P] = P("\t")

def closingSequence[$: P] = P(space ~ "#".rep ~ space.rep).? ~ end_of_line

enum SpanKind:
  case Emphasis, StrongEmphasis, CodeSpan, Regular

import SpanKind.*

enum Textual:
  case Span(kind: SpanKind, text: String)
  case Hyperlink(text: String, target: String)

import Textual.*

enum Markdown:
  case Heading(level: Int, text: Paragraph)
  case Paragraph(contents: Seq[Textual])
  case BulletList(content: Seq[Paragraph])
  case CodeBlock(language: String, content: String)
  case BlankLine

import Markdown.*

def regularText_no_star[$: P] = P((!"*" ~ not_end_of_line).rep(1))

def regularText_no_backquote[$: P] = P((!"`" ~ not_end_of_line).rep(1))

def regularText_no_underscore[$: P] = P((!"_" ~ not_end_of_line).rep(1))

def regularText_no_markup[$: P]: P[Span] = P((!CharIn("*_`[") ~ not_end_of_line).rep(1).!).map(Span(Regular, _))

def heading[$: P](level: Int): P[Heading] =
  P(("#" * level) ~ space ~ paragraph ~ closingSequence).map(Heading(level, _))

def anyHeading[$: P]: P[Heading] = P(heading(1) | heading(2) | heading(3) | heading(4) | heading(5) | heading(6))

def codeSpan[$: P]: P[Span] = P("`" ~ regularText_no_backquote.! ~ "`").map(Span(CodeSpan, _))

def emphasis[$: P] = P(CharIn("*") ~ regularText_no_star.! ~ "*").map(Span(Emphasis, _))

def emphasis_underscore[$: P] = P(CharIn("_") ~ regularText_no_underscore.! ~ "_").map(Span(Emphasis, _))

def strongEmphasis[$: P] = P("**" ~ regularText_no_star.! ~ "**").map(Span(StrongEmphasis, _))

def fencedCodeBlock[$: P]: P[CodeBlock] =
  P(
    "```" ~ CharIn("A-Za-z0-9").rep.! ~ end_of_line ~
      (!"```" ~ not_end_of_line.rep ~ end_of_line).rep.! ~ "```" ~ end_of_line)
    .map { case (language, content) => CodeBlock(language, content) }

def blankLine[$: P] = P((space.? ~ end_of_line).rep(1))

def hyperlink[$: P]: P[Hyperlink] =
  P("[" ~ space.? ~ (!"]" ~ not_end_of_line).rep.! ~ space.? ~ "]" ~ "(" ~ (!")" ~ not_end_of_line).rep.! ~ ")")
    .map { case (text, target) => Hyperlink(text, target) }

def bulletListItem[$: P]: P[Paragraph] = P("-" ~ space ~ paragraph ~ blankLine)

def bulletList[$: P]: P[BulletList] = P(bulletListItem.rep(1)).map(BulletList.apply)

def paragraph[$: P]: P[Paragraph] =
  P((emphasis | emphasis_underscore | strongEmphasis | codeSpan | hyperlink | regularText_no_markup.!.map(Span(Regular, _))).rep)
    .map(Paragraph.apply)

def block[$: P]: P[Markdown] =
  P(blankLine.map(_ => BlankLine) | anyHeading | fencedCodeBlock | bulletList | (paragraph ~ end_of_line))

def markdown[$: P]: P[Seq[Markdown]] = P(block.rep(1))

def textualToLatex: Textual => String = {
  case Textual.Span(kind, text) => kind match
    case SpanKind.Emphasis => s"\\emph{$text}"
    case SpanKind.StrongEmphasis => s"\\textbf{$text}\\index{$text}"
    case SpanKind.CodeSpan => s"\\lstinline!$text!"
    case SpanKind.Regular => text
  case Textual.Hyperlink(text, target) =>
    val cleanedText = text.replaceAll("#", "\\\\#")
    val cleanedTarget = target.replaceAll("#", "\\\\#")
    s"$cleanedText\\footnote{\\texttt{\\url{$cleanedTarget}}}" // \\texttt{\\href{${text.replaceAll("#", "\\\\#")}}{${}}}"
}

def languageOption(str: String): String =
  val replaced = if str `equalsIgnoreCase` "dhall" then "haskell" else str
  val capitalized = (if replaced `equalsIgnoreCase` "haskell" then "" else replaced).capitalize
  if capitalized.isEmpty then "" else s"[language=$capitalized]"

val dhallAddLet = Seq(
  "-- This is a complete program",
  "-- This file is `",
)

val dhallToIgnore = Seq(
  "⊢",
  "↳",
  "???",
  "≅",
  "-- Type error: ",
  "-- Symbolic derivation.",
  "$ dhall --file ",
)

def toDhall: Markdown => String = {
  case Markdown.CodeBlock("dhall", content) if dhallAddLet.exists(content.contains(_)) =>
    s"let _ = $content"
  case Markdown.CodeBlock("dhall", content) if !dhallToIgnore.exists(content.contains(_)) =>
     content
  case _ => ""
}

def toLatex: Markdown => String = {
  case Markdown.Heading(level, text) =>
    val heading = level match {
      case 1 => "part"
      case 2 => "chapter"
      case 3 => "section"
      case 4 => "subsection"
      case 5 => "subsubsection"
      case 6 => "paragraph"
      case _ => "relax"
    }
    // Disable book parts!
    if level == 1 then "" else s"\\$heading{${toLatex(text)}}"

  case Markdown.Paragraph(contents) => contents.map(textualToLatex).mkString("")
  case Markdown.BulletList(content) => content.map(toLatex).mkString("\\begin{itemize}\n\\item{", "}\n\\item{", "}\n\\end{itemize}")
  case Markdown.CodeBlock(language, content) =>
    s"\\begin{lstlisting}${languageOption(language)}\n$content\\end{lstlisting}"
  case Markdown.BlankLine => "\n"
}

@main
def main(code: Boolean): Unit =
  val result: Seq[Markdown] = parse(System.in, markdown(_)).get.value

  val convert = if code then toDhall else toLatex
  val sep = if code then "" else "\n"
  val finalLine = if code then " in True\n" else ""
  
  println(result.map(convert).mkString(sep) + finalLine)  
