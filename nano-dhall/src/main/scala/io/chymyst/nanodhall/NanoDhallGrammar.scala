package io.chymyst.nanodhall

import fastparse.NoWhitespace._
import fastparse._

import scala.util.{Failure, Success, Try}
import io.chymyst.fastparse.Memoize.MemoizeParser

object NanoDhallGrammar {

  def ALPHA[$: P] = P(
    CharIn("\u0041-\u005A", "\u0061-\u007A") //  A_Z | a_z
  )

  def DIGIT[$: P] = P(
    CharIn("0-9")
    //  0_9
  )

  def end_of_line[$: P] = P("\n" | "\r\n")

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
      // U+10400 = "\uD801\uDC00"
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
    .memoize

  def tab[$: P] = P("\t")

  def whitespace_chunk[$: P] = P(
    " "
      | tab
      | end_of_line
  )
    .memoize

  def whsp[$: P]: P[Unit] = P(
    NoCut(whitespace_chunk.rep)
  )

  def whsp1[$: P]: P[Unit] = P(
    NoCut(whitespace_chunk.rep(1))
  )

  def ALPHANUM[$: P] = P(
    ALPHA | DIGIT
  )

}
