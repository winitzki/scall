package io.chymyst.abnf

import fastparse.NoWhitespace._
import fastparse._
import io.chymyst.abnf.ABNF.ProseValue

sealed trait ABNF

object ABNF {
  final case class Alpha(data: Byte)        extends ABNF
  final case class Bit(data: Byte)          extends ABNF
  final case class Char(data: Char)         extends ABNF
  final case class ProseValue(data: String) extends ABNF
}

object ABNFGrammar {
  def ALPHA[$: P] = P(
    CharIn("\u0041-\u005A", "\u0061-\u007A") //  A_Z | a_z
  )

  def BIT[$: P] = P(CharIn("01"))

  def CHAR[$: P] = P(
    CharIn("\u0001-\u007F")
    //  any 7_bit US_ASCII character,
    //   excluding NUL
  )

  def CR[$: P] = P(
    "\n"
    //  carriage return
  )

  def CRLF[$: P] = P(
    CR ~ LF
    //  Internet standard newline
  )

  def CTL[$: P] = P(
    CharIn("\u0000-\u001F", "\u007F")
    //  controls
  )

  def DIGIT[$: P] = P(
    CharIn("0-9")
    //  0_9
  )

  def DQUOTE[$: P] = P(
    "\""
    //  " (Double Quote)
  )

  def HEXDIG[$: P] = P(
    CharIn("0-9", "A-F") // DIGIT | "A" | "B" | "C" | "D" | "E" | "F"
  )

  def HTAB[$: P] = P(
    "\u0009"
    //  horizontal tab
  )

  def LF[$: P] = P(
    "\r"
    //  linefeed
  )

  def LWSP[$: P] = P(
    (WSP | (CRLF ~ WSP)).rep
      //  Use of this linear_white_space rule
      //   permits lines containing only white
      //   space that are no longer legal in
      //   mail headers and have caused
      //   interoperability problems in other
      //   contexts.
      //  Do not use when defining mail
      //   headers and use with caution in
      //   other contexts.
  )

  def OCTET[$: P] = P(
    CharIn("\u0000-\u00FF")
    //  8 bits of data
  )

  def SP[$: P] = P(" ")

  def VCHAR[$: P] = P(
    CharIn("\u0021-\u007E")
    //  visible (printing) characters
  )

  def WSP[$: P] = P(
    SP | HTAB
    //  white space
  )

  def rule_list[$: P] = P((rule | (c_wsp ~ c_nl).rep).rep(1))

  def rule[$: P] = P(
    rulename ~ defined_as ~ elements ~ c_nl
    //  continues if next line starts
    //   with white space
  )

  def rulename[$: P] = P(ALPHA ~ (ALPHA | DIGIT | "-").rep)

  def defined_as[$: P] = P(
    c_wsp.rep ~ ("=" | "=/") ~ c_wsp.rep
    //  basic rules definition and
    //   incremental alternatives
  )

  def elements[$: P] = P(alternation ~ c_wsp.rep)

  def c_wsp[$: P] = P(WSP | (c_nl ~ WSP))

  def c_nl[$: P] = P(
    comment | CRLF
    //  comment or newline
  )

  def comment[$: P] = P(";" ~ (WSP | VCHAR).rep ~ CRLF)

  def alternation[$: P]: P[Unit] = P(
    concatenation ~
      (c_wsp.rep ~ "/" ~ c_wsp.rep ~ concatenation).rep
  )

  def concatenation[$: P] = P(repetition ~ (c_wsp.rep(1) ~ repetition).rep)

  def repetition[$: P] = P(repeat.? ~ element)

  def repeat[$: P] = P(DIGIT.rep(1) | (DIGIT.rep ~ "*" ~ DIGIT.rep))

  def element[$: P] = P(
    rulename | group | option |
      char_val | num_val | prose_val
  )

  def group[$: P] = P("(" ~ c_wsp.rep ~ alternation ~ c_wsp.rep ~ ")")

  def option[$: P] = P("[" ~ c_wsp.rep ~ alternation ~ c_wsp.rep ~ "]")

  def char_val[$: P] = P(
    DQUOTE ~ CharIn("\u0020-\u0021", "\u0023-\u007E").rep ~ DQUOTE
    //  quoted string of SP and VCHAR without DQUOTE
  )

  def num_val[$: P] = P("%" ~ (bin_val | dec_val | hex_val))

  def bin_val[$: P] = P(
    "b" ~ BIT.rep(1) ~
      (("." ~ BIT.rep(1)).rep(1) | ("-" ~ BIT.rep(1))).?
      //  series of concatenated bit values
      //   or single ONEOF range
  )

  def dec_val[$: P] = P(
    "d" ~ DIGIT.rep(1) ~
      (("." ~ DIGIT.rep(1)).rep(1) | ("-" ~ DIGIT.rep(1))).?
  )

  def hex_val[$: P] = P(
    "x" ~ HEXDIG.rep(1) ~
      (("." ~ HEXDIG.rep(1)).rep(1) | ("-" ~ HEXDIG.rep(1))).?
  )

  def prose_val[$: P] = P(
    "<" ~ CharIn("\u0020-\u003D", "\u003F-\u007E").rep.!.map(ProseValue) ~ ">"
    //  bracketed string of SP and VCHAR
    //   without angles
    //  prose description, to be used as
    //   last resort
  )
}

object FuzzABNF {
  def generate: String = ???
}
