grammar dhall;
// ABNF syntax based on RFC 5234

//

// The character encoding for Dhall is UTF-8

//

// Some notes on implementing this grammar:

//

// First, do not use a lexer to tokenize the file before parsing.  Instead, treat

// the individual characters of the file as the tokens to feed into the parser.

// You should not use a lexer because Dhall's grammar supports two features which

// cannot be correctly supported by a lexer:

//

// * String interpolation (i.e. "foo ${Natural/toInteger bar} baz")

// * Nested block comments (i.e. "{- foo {- bar -} baz -}")

//

// Second, this grammar assumes that your parser can backtrack and/or try

// multiple parses simultaneously.  For example, consider this expression:

//

//     List ./MyType

//

// A parser might first try to parse the period as the beginning of a field

// selector, only to realize immediately afterwards that `/MyType` is not a valid

// name for a field.  A conforming parser must backtrack so that the expression

// `./MyType` can instead be correctly interpreted as a relative path

//

// Third, if there are multiple valid parses then prefer the first parse

// according to the ordering of alternatives. That is, the order of evaluation

// of the alternatives is left_to_right.

//

// For example, the grammar for single quoted string literals is:

//

//     single_quote_continue :

//           "'''"               single_quote_continue

//         | "${" complete_expression "}" single_quote_continue

//         | "''${"              single_quote_continue

//         | "''"

//         | '\u0020-\u0010'FFFF         single_quote_continue

//         | tab                 single_quote_continue

//         | end_of_line         single_quote_continue

//

//         single_quote_literal : "''" single_quote_continue

//

// ... which permits valid parses for the following code:

//

//     "''''''''''''''''"

//

// If you tried to parse all alternatives then there are at least two valid

// interpretations for the above code:

//

// * A single quoted literal with four escape sequences of the form "'''"

//     * i.e. "''" followed by "'''"  four times in a row followed by "''"

// * Four empty single quoted literals

//     * i.e. "''''" four times in a row

//

// The correct interpretation is the first one because parsing the escape

// sequence "'''" takes precedence over parsing the termination sequence "''",

// according to the order of the alternatives in the `single_quote_continue`

// rule.

//

// Some parsing libraries do not backtrack by default but allow the user to

// selectively backtrack in certain parts of the grammar.  Usually parsing

// libraries do this to improve efficiency and error messages.  Dhall's grammar

// takes that into account by minimizing the number of rules that require the

// parser to backtrack and comments below will highlight where you need to

// explicitly backtrack

//

// Specifically, if you see an uninterrupted literal in a grammar rule such as:

//

//     "->"

//

// ... or:

//

//     %x66.6f.72.61.6c.6c

//

// ... then that string literal is parsed as a single unit, meaning that you

// should backtrack if you parse only part of the literal

//

// In all other cases you can assume that you do not need to backtrack unless

// there is a comment explicitly asking you to backtrack

//

// When parsing a repeated construct, prefer alternatives that parse as many

// repetitions as possible.  On in other words:

//

//     [a] : a | ""

//

//     a* : a* a | ""

//

// Note that the latter rule also specifies that repetition produces

// left_associated expressions.  For example, function application is

// left_associative and all operators are left_associative when they are not

// parenthesized.

//

// Additionally, try alternatives in an order that minimizes backtracking

// according to the following rule:

//

//     (a | b) (c | d) : a c | a d | b c | b d

// NOTE: There are many line endings in the wild

//

// See: https://en.wikipedia.org/wiki/Newline

//

// For simplicity this supports Unix and Windows line_endings, which are the most

// common

fragment END_OF_LINE
   : ('\u000A' // "\n"
   | '\u000D\u000A') // "\r\n"
   
   // This rule matches all characters that are not:
   
   //
   
   // * not ASCII
   
   // * not part of a surrogate pair
   
   // * not a "non_character"
   
   ;

fragment VALID_NON_ASCII
   : '\u0080-\uD7FF'
   |
   // %xD800_DFFF = surrogate pairs
   
   //      '\uE000-\uFFFC',
   '\uE000-\uFFFC' // Workaround: Disallow the 'replacement' character ('\uFFFD') because it will be generated for invalid utf-8 encodings.
   
   // Encode other Unicode ranges into Java's UTF-16 using UTF-16 surrogates.
   
   // See https://www.cogsci.ed.ac.uk/~richard/utf-8.cgi?input=10000&mode=hex and look for 'UTF-16 surrogates'.
   
   // %xFFFE_FFFF = non_characters
   
   //        | % x10000_1FFFD
   
   // U+10000 = '\uD800\uDC00'
   
   // U+103FF = '\uD800\uDFFF'
   
   // U+10400 = '\uD01\uDC00'
   
   // U+1FFFD = '\uD83F\uDFFD'
   
   // format: off
   | ('\uD800-\uD83E' '\uDC00-\uDFFF')
   // format: on
   | ('\uD83F' '\uDC00-\uDFFD')
   //      // %x1FFFE_1FFFF = non_characters
   
   //      | % x20000_2FFFD   // U+20000 = \uD840\uDC00
   | ('\uD840-\uD87E' '\uDC00-\uDFFF')
   | ('\uD87F' '\uDC00-\uDFFD')
   //        // %x2FFFE_2FFFF = non_characters
   
   //        | % x30000_3FFFD
   | ('\uD880-\uD8BE' '\uDC00-\uDFFF')
   | ('\uD8BF' '\uDC00-\uDFFD')
   //      // %x3FFFE_3FFFF = non_characters
   
   //      | % x40000_4FFFD
   | ('\uD8C0-\uD8FE' '\uDC00-\uDFFF')
   | ('\uD8FF' '\uDC00-\uDFFD')
   //        // %x4FFFE_4FFFF = non_characters
   
   //        | % x50000_5FFFD
   | ('\uD900-\uD93E' '\uDC00-\uDFFF')
   | ('\uD93F' '\uDC00-\uDFFD')
   //      // %x5FFFE_5FFFF = non_characters
   
   //      | % x60000_6FFFD
   | ('\uD940-\uD97E' '\uDC00-\uDFFF')
   | ('\uD97F' '\uDC00-\uDFFD')
   //        // %x6FFFE_6FFFF = non_characters
   
   //        | % x70000_7FFFD
   | ('\uD980-\uD9BE' '\uDC00-\uDFFF')
   | ('\uD9BF' '\uDC00-\uDFFD')
   //      // %x7FFFE_7FFFF = non_characters
   
   //      | % x80000_8FFFD
   | ('\uD9C0-\uD9FE' '\uDC00-\uDFFF')
   | ('\uD9FF' '\uDC00-\uDFFD')
   //        // %x8FFFE_8FFFF = non_characters
   
   //        | % x90000_9FFFD
   | ('\uDA00-\uDA3E' '\uDC00-\uDFFF')
   | ('\uDA3F' '\uDC00-\uDFFD')
   //      // %x9FFFE_9FFFF = non_characters
   
   //      | % xA0000_AFFFD
   | ('\uDA40-\uDA7E' '\uDC00-\uDFFF')
   | ('\uDA7F' '\uDC00-\uDFFD')
   //        // %xAFFFE_AFFFF = non_characters
   
   //        | % xB0000_BFFFD
   | ('\uDA80-\uDABE' '\uDC00-\uDFFF')
   | ('\uDABF' '\uDC00-\uDFFD')
   //      // %xBFFFE_BFFFF = non_characters
   
   //      | % xC0000_CFFFD
   | ('\uDAC0-\uDAFE' '\uDC00-\uDFFF')
   | ('\uDAFF' '\uDC00-\uDFFD')
   //        // %xCFFFE_CFFFF = non_characters
   
   //        | % xD0000_DFFFD
   | ('\uDB00-\uDB3E' '\uDC00-\uDFFF')
   | ('\uDB3F' '\uDC00-\uDFFD')
   //      // %xDFFFE_DFFFF = non_characters
   
   //      | % xE0000_EFFFD
   | ('\uDB40-\uDB7E' '\uDC00-\uDFFF')
   | ('\uDB7F' '\uDC00-\uDFFD')
   //        // %xEFFFE_EFFFF = non_characters
   
   //        | % xF0000_FFFFD
   | ('\uDB80-\uDBBE' '\uDC00-\uDFFF')
   | ('\uDBBF' '\uDC00-\uDFFD')
   // U+F0000 = '\uDB80\uDC00'
   
   // U+FFFFD = '\uDBBF\uDFFD'
   
   //      // %xFFFFE_FFFFF = non_characters
   
   //      | % x100000_10FFFD
   | ('\uDBC0-\uDBFE' '\uDC00-\uDFFF')
   | ('\uDBFF' '\uDC00-\uDFFD')
   // U+100000 = '\uDBC0\uDC00'
   
   // U+10FFFD = '\uDBFF\uDFFD'
   
   // %x10FFFE_10FFFF = non_characters
   
   ;

fragment TAB
   : '\u0009' // '\t'
   
   ;

block_comment
   : '{-' block_comment_continue
   ;

fragment PRINTABLE_ASCII
   : '\u0020-\u007F'
   ;

fragment BLOCK_COMMENT_CHAR
   : PRINTABLE_ASCII
   | VALID_NON_ASCII
   | TAB
   | END_OF_LINE
   ;

block_comment_continue
   : '-}'
   | block_comment block_comment_continue
   | BLOCK_COMMENT_CHAR block_comment_continue
   ;

fragment NOT_END_OF_LINE
   : PRINTABLE_ASCII
   | VALID_NON_ASCII
   | TAB
   // NOTE: Slightly different from Haskell_style single_line comments because this
   
   // does not require a space after the dashes
   
   ;

line_comment_prefix
   : '--' NOT_END_OF_LINE*
   ;

line_comment
   : line_comment_prefix END_OF_LINE
   ;

whitespace_chunk
   : ' '
   | TAB
   | END_OF_LINE
   | line_comment
   | block_comment
   ;

whsp
   : whitespace_chunk*
   // nonempty whitespace
   
   ;

whsp1
   : whitespace_chunk+
   // Uppercase or lowercase ASCII letter
   
   ;

ALPHA
   : ('\u0041-\u005A' | '\u0061-\u007A')
   // ASCII digit
   
   ;

DIGIT
   : '[0-9]' // 0-9
   
   ;

ALPHANUM
   : ALPHA
   | DIGIT
   ;

HEXDIG
   : DIGIT
   | 'A'
   | 'B'
   | 'C'
   | 'D'
   | 'E'
   | 'F'
   // A simple label cannot be one of the reserved keywords
   
   // listed in the `keyword` rule.
   
   // A PEG parser could use negative lookahead to
   
   // enforce this, e.g. as follows:
   
   // simple_label :
   
   //       keyword 1*simple_label_next_char
   
   //     | !keyword (simple_label_first_char *simple_label_next_char)
   
   ;

simple_label_first_char
   : ALPHA
   | '_'
   ;

simple_label_next_char
   : ALPHANUM
   | '-'
   | '/'
   | '_'
   ;

simple_label
   : simple_label_first_char simple_label_next_char*
   ;

quoted_label_char
   : '\u0020-\u005F'
   // '\u0060' : '`'
   | '\u0061-\u007E'
   ;

quoted_label
   : quoted_label_char*
   // NOTE: Dhall does not support Unicode labels, mainly to minimize the potential
   
   // for code obfuscation
   
   ;

label
   : ('`' quoted_label '`' | simple_label)
   // A nonreserved_label cannot not be any of the reserved identifiers for builtins
   
   // (unless quoted).
   
   // Their list can be found in the `builtin` rule.
   
   // The only place where this restriction applies is bound variables.
   
   // A PEG parser could use negative lookahead to avoid parsing those identifiers,
   
   // e.g. as follows:
   
   // nonreserved_label :
   
   //      builtin 1*simple_label_next_char
   
   //    | !builtin label
   
   ;

nonreserved_label
   : label
   // An any_label is allowed to be one of the reserved identifiers (but not a keyword).
   
   ;

any_label
   : label
   // Allow specifically `Some` in record and union labels.
   
   ;

any_label_or_some
   : any_label
   | KEYWORDSOME
   // Allow `?` as path component in `with`.
   
   ;

with_component
   : any_label_or_some
   | '?'
   // Dhall's double_quoted strings are similar to JSON strings (RFC7159) except:
   
   //
   
   // * Dhall strings support string interpolation
   
   //
   
   // * Dhall strings also support escaping string interpolation by adding a new
   
   //   `\$` escape sequence
   
   //
   
   // * Dhall strings also allow Unicode escape sequences of the form `\u{XXX}`
   
   ;

double_quote_chunk
   : interpolation
   // '\'    Beginning of escape sequence
   | '\u005C' double_quote_escaped
   | double_quote_char
   ;

double_quote_escaped
   : '\u0022' // '''    quotation mark  U+0022
   | '\u0024' // '$'    dollar sign     U+0024
   | '\u005C' // '\'    reverse solidus U+005C
   | '\u002F' // '/'    solidus         U+002F
   | '\u0062' // 'b'    backspace       U+0008
   | '\u0066' // 'f'    form feed       U+000C
   | '\u006E' // 'n'    line feed       U+000A
   | '\u0072' // 'r'    carriage return U+000D
   | '\u0074' // 't'    tab             U+0009
   | '\u0075' unicode_escape // 'uXXXX' | 'u{XXXX}'    U+XXXX
   
   // Valid Unicode escape sequences are as follows:
   
   //
   
   // * Exactly 4 hexadecimal digits without braces:
   
   //       `\uXXXX`
   
   // * 1-6 hexadecimal digits within braces (with optional zero padding):
   
   //       `\u{XXXX}`, `\u{000X}`, `\u{XXXXX}`, `\u{00000XXXXX}`, etc.
   
   //   Any number of leading zeros are allowed within the braces preceding the 1-6
   
   //   digits specifying the codepoint.
   
   //
   
   // From these sequences, the parser must also reject any codepoints that are in
   
   // the following ranges:
   
   //
   
   // * Surrogate pairs: `%xD800-DFFF`
   
   // * Non_characters: `%xNFFFE_NFFFF` | `%x10FFFE-10FFFF` for `N` in `{ 0 .. F }`
   
   //
   
   // See the `valid_non_ascii` rule for the exact ranges that are not allowed
   
   ;

unicode_escape
   : unbraced_escape
   | '{' braced_escape '}'
   // All valid last 4 digits for unicode codepoints (outside Plane 0): `0000-FFFD`
   
   ;

unicode_suffix
   : (DIGIT | 'A' | 'B' | 'C' | 'D' | 'E') HEXDIG HEXDIG HEXDIG
   | 'F' HEXDIG HEXDIG (DIGIT | 'A' | 'B' | 'C' | 'D')
   // All 4-hex digit unicode escape sequences that are not:
   
   //
   
   // * Surrogate pairs (i.e. `%xD800-DFFF`)
   
   // * Non_characters (i.e. `%xFFFE_FFFF`)
   
   ; //
   
unbraced_escape
   : (DIGIT | 'A' | 'B' | 'C') HEXDIG HEXDIG HEXDIG
   | 'D' ('0' | '1' | '2' | '3' | '4' | '5' | '6' | '7') HEXDIG HEXDIG
   // %xD800-DFFF Surrogate pairs
   | 'E' HEXDIG HEXDIG HEXDIG
   | 'F' HEXDIG HEXDIG (DIGIT | 'A' | 'B' | 'C' | 'D')
   // %xFFFE_FFFF Non_characters
   
   // All 1-6 digit unicode codepoints that are not:
   
   //
   
   // * Surrogate pairs: `%xD800-DFFF`
   
   // * Non_characters: `%xNFFFE_NFFFF` | `%x10FFFE-10FFFF` for `N` in `{ 0 .. F }`
   
   //
   
   // See the `valid_non_ascii` rule for the exact ranges that are not allowed
   
   ;

braced_codepoint
   : ('1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | '10') unicode_suffix // (Planes 1-16)
   | unbraced_escape // (Plane 0)
   | HEXDIG (HEXDIG HEXDIG?)? // %x000-FFF
   
   // Allow zero padding for braced codepoints
   
   ;

braced_escape
   : '0'* braced_codepoint
   // Printable characters except double quote and backslash
   
   ;

double_quote_char
   : '\u0020-\u0021'
   // '\u0022' : '''
   | '\u0023-\u005B'
   // '\u005C' : '\'
   | '\u005D-\u007F'
   | VALID_NON_ASCII
   ;

double_quote_literal
   : '\u0022' double_quote_chunk* '\u0022'
   // NOTE: The only way to end a single_quote string literal with a single quote is
   
   // to either interpolate the single quote, like this:
   
   //
   
   //     ''ABC${'''}''
   
   //
   
   // ... or concatenate another string, like this:
   
   //
   
   //     ''ABC'' ++ '''
   
   //
   
   // If you try to end the string literal with a single quote then you get ''''',
   
   // which is interpreted as an escaped pair of single quotes
   
   ;

single_quote_continue
   : interpolation single_quote_continue
   | escaped_quote_pair single_quote_continue
   | escaped_interpolation single_quote_continue
   | END_OF_TEXT_LITERAL
   | single_quote_char single_quote_continue
   // Escape two single quotes (i.e. replace this sequence with '''')
   
   ;

escaped_quote_pair
   : '\'\'\''
   // Escape interpolation (i.e. replace this sequence with '${')
   
   ;

escaped_interpolation
   : '\'\'${'
   ;

single_quote_char
   : PRINTABLE_ASCII
   | VALID_NON_ASCII
   | TAB
   | END_OF_LINE
   ;

END_OF_TEXT_LITERAL
   : '\'\''
   ;

single_quote_literal
   : END_OF_TEXT_LITERAL END_OF_LINE single_quote_continue
   ;

interpolation
   : '${' complete_expression '}'
   ;

text_literal
   : (double_quote_literal | single_quote_literal)
   ;

bytes_literal
   :
   // Hexadecimal with '0x' prefix
   
   // '\u0022' : '"'
   '0' '\u0078' '\u0022' (HEXDIG HEXDIG)* '\u0022'
   // RFC 5234 interprets string literals as case_insensitive and recommends using
   
   // hex instead for case_sensitive strings
   
   //
   
   // If you don't feel like reading hex, these are all the same as the rule name.
   
   // Keywords that should never be parsed as identifiers
   
   ;

IF
   : 'if'
   ;

THEN
   : 'then'
   ;

ELSE
   : 'else'
   ;

LET
   : 'let'
   ;

IN
   : 'in'
   ;

AS
   : 'as'
   ;

USING
   : 'using'
   ;

MERGE
   : 'merge'
   ;

MISSING
   : 'missing'
   ;

INFINITY
   : 'Infinity'
   ;

NAN
   : 'NaN'
   ;

KEYWORDSOME
   : 'Some'
   ;

TOMAP
   : 'toMap'
   ;

ASSERT
   : 'assert'
   ;

FORALL_KEYWORD
   : 'forall'
   ;

FORALL_SYMBOL
   : '\u2200' // Unicode FOR ALL
   
   ;

FORALL
   : FORALL_SYMBOL
   | FORALL_KEYWORD
   ;

WITH
   : 'with'
   ;

SHOW_CONSTRUCTOR
   : 'showConstructor'
   // Unused rule that could be used as negative lookahead in the
   
   // `simple_label` rule for parsers that support this.
   
   ;

keyword
   : IF
   | THEN
   | ELSE
   | LET
   | IN
   | USING
   | MISSING
   | ASSERT
   | AS
   | INFINITY
   | NAN
   | MERGE
   | SOME
   | TOMAP
   | FORALL_KEYWORD
   | WITH
   | SHOW_CONSTRUCTOR
   // Note that there is a corresponding parser test in
   
   // `tests/parser/success/builtinsA.dhall`. Please update it when
   
   // you modify this `builtin` rule.
   
   ;

builtin
   : bNatural_fold
   | bNatural_build
   | bNatural_isZero
   | bNatural_even
   | bNatural_odd
   | bNatural_toInteger
   | bNatural_show
   | bInteger_toDouble
   | bInteger_show
   | bInteger_negate
   | bInteger_clamp
   | bNatural_subtract
   | bDouble_show
   | bList_build
   | bList_fold
   | bList_length
   | bList_head
   | bList_last
   | bList_indexed
   | bList_reverse
   | bText_show
   | bText_replace
   | bDate_show
   | bTime_show
   | bTimeZone_show
   | bBool
   | bTrue
   | bFalse
   | bOptional
   | bNone
   | bNatural
   | bInteger
   | bDouble
   | bText
   | bDate
   | bTime
   | bTimeZone
   | bList
   | cType
   | cKind
   | cSort
   // Reserved identifiers, needed for some special cases of parsing
   
   ;

bOptional
   : 'Optional'
   ;

bText
   : 'Text'
   ;

bList
   : 'List'
   ;

bLocation
   : 'Location'
   ;

bBytes
   : 'Bytes'
   // Remainder of the reserved identifiers, needed for the `builtin` rule
   
   ;

bBool
   : 'Bool'
   ;

bTrue
   : 'True'
   ;

bFalse
   : 'False'
   ;

bNone
   : 'None'
   ;

bNatural
   : 'Natural'
   ;

bInteger
   : 'Integer'
   ;

bDouble
   : 'Double'
   ;

bDate
   : 'Date'
   ;

bTime
   : 'Time'
   ;

bTimeZone
   : 'TimeZone'
   ;

cType
   : 'Type'
   ;

cKind
   : 'Kind'
   ;

cSort
   : 'Sort'
   ;

bNatural_fold
   : 'Natural/fold'
   ;

bNatural_build
   : 'Natural/build'
   ;

bNatural_isZero
   : 'Natural/isZero'
   ;

bNatural_even
   : 'Natural/even'
   ;

bNatural_odd
   : 'Natural/odd'
   ;

bNatural_toInteger
   : 'Natural/toInteger'
   ;

bNatural_show
   : 'Natural/show'
   ;

bNatural_subtract
   : 'Natural/subtract'
   ;

bInteger_toDouble
   : 'Integer/toDouble'
   ;

bInteger_show
   : 'Integer/show'
   ;

bInteger_negate
   : 'Integer/negate'
   ;

bInteger_clamp
   : 'Integer/clamp'
   ;

bDouble_show
   : 'Double/show'
   ;

bList_build
   : 'List/build'
   ;

bList_fold
   : 'List/fold'
   ;

bList_length
   : 'List/length'
   ;

bList_head
   : 'List/head'
   ;

bList_last
   : 'List/last'
   ;

bList_indexed
   : 'List/indexed'
   ;

bList_reverse
   : 'List/reverse'
   ;

bText_show
   : 'Text/show'
   ;

bText_replace
   : 'Text/replace'
   ;

bDate_show
   : 'Date/show'
   ;

bTime_show
   : 'Time/show'
   ;

bTimeZone_show
   : 'TimeZone/show'
   // Operators
   
   ;

combine
   : '\u2227'
   | '/\\'
   ;

combine_types
   : '\u2A53'
   | '//\\\\'
   ;

equivalent
   : '\u2261'
   | '==='
   ;

prefer
   : '\u2AFD'
   | '//'
   ;

lambda
   : '\u03BB'
   | '\\'
   ;

arrow
   : '\u2192'
   | '->'
   ;

complete
   : '::'
   ;

exponent
   : 'e' ('+' | '-')? DIGIT+
   ;

numeric_double_literal
   : ('+' | '-')? DIGIT+ ('.' DIGIT+ exponent? | exponent)
   ;

minus_infinity_literal
   : '-' Infinity
   ;

plus_infinity_literal
   : Infinity
   ;

double_literal
   :
   // '-Infinity'
   minus_infinity_literal
   // 'Infinity'
   | plus_infinity_literal
   // 'NaN'
   | NaN
   // '2.0'
   | numeric_double_literal
   ;

natural_literal
   :
   // Binary with '0b' prefix
   '0' '\u0062' BIT+
   // Hexadecimal with '0x' prefix
   | '0' '\u0078' HEXDIG+
   // Decimal; leading 0 digits are not allowed
   | ('1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9') DIGIT*
   // ... except for 0 itself
   | '0'
   ;
   // Sign is mandatory for integer literals.
   
integer_literal
   : ('+' | '-') natural_literal
   // All temporal literals need to be valid dates according to RFC3339, meaning
   
   // that:
   
   //
   
   // * Months must be in the range 1-12
   
   // * The day of the month must be valid according to the corresponding month
   
   // * February 29 is only permitted on leap years
   
   //
   
   // The only exception to this is leap seconds, which are not supported because we
   
   // treat dates and times separately.  In other words, the valid range of the
   
   // seconds field is always 0-59.
   
   ; //
   
temporal_literal
   :
   // 'YYYY_MM_DDThh:mm:ss[+-]HH:MM', parsed as a `{ date : Date, time : Time, timeZone : TimeZone }`
   full_date 'T' partial_time time_offset
   // 'YYYY_MM_DDThh:mm:ss', parsed as a `{ date : Date, time : Time }`
   | full_date 'T' partial_time
   // 'hh:mm:ss[+-]HH:MM', parsed as a `{ time : Time, timeZone, TimeZone }`
   | partial_time time_offset
   // 'YYYY_MM_DD', parsed as a `Date`
   | full_date
   // 'hh:mm:ss', parsed as a `Time`
   | partial_time
   // '[+-]HH:MM', parsed as a `TimeZone`
   
   //
   
   // Carefully note that this `time_numoffset` and not `time_offset`, meaning
   
   // that a standalone `Z` is not a valid Dhall literal for a `TimeZone`
   | time_numoffset
   // Taken from RFC 3339 with some differences
   
   ;

date_fullyear
   : DIGIT DIGIT DIGIT DIGIT
   ;

date_month
   : DIGIT DIGIT // 01-12
   
   ;

date_mday
   : DIGIT DIGIT // 01-28, 01-29, 01-30, 01-31 based on
   
   // month/year
   
   ;

time_hour
   : DIGIT DIGIT // 00-23
   
   ;

time_minute
   : DIGIT DIGIT // 00-59
   
   ;

time_second
   : DIGIT DIGIT // 00-59 (**UNLIKE** RFC 3339, we don't support leap
   
   // seconds)
   
   // Like RFC 3339, we require an implementation to support *parsing* an arbitrary
   
   // time precision, but an implementation only needs to support storing/encoding
   
   // at least nanosecond precision.  In other words an implementation only needs to
   
   // preserve 9 digits after the decimal point.
   
   ;

time_secfrac
   : '.' DIGIT+ // RFC 3339
   
   ;

time_numoffset
   : ('+' | '-') time_hour ':' time_minute
   ;

time_offset
   : 'Z'
   | time_numoffset // 'Z' desugars to '+00:00'
   
   ;

partial_time
   : time_hour ':' time_minute ':' time_second time_secfrac?
   ;

full_date
   : date_fullyear '-' date_month '-' date_mday
   // If the identifier matches one of the names in the `builtin` rule, then it is a
   
   // builtin, and should be treated as the corresponding item in the list of
   
   // 'Reserved identifiers for builtins' specified in the `standard/README.md` document.
   
   // It is a syntax error to specify a de Bruijn index in this case.
   
   // Otherwise, this is a variable with name and index matching the label and index.
   
   ;

identifier
   : variable
   | builtin
   ;

variable
   : nonreserved_label (whsp '@' whsp natural_literal)?
   // Printable characters other than ' ()[]{}<>/\,'
   
   //
   
   // Excluding those characters ensures that paths don't have to end with trailing
   
   // whitespace most of the time
   
   ;

path_character
   :
   // '\u0020' : ' '
   '\u0021'
   // '\u0022' : '\''
   
   // '\u0023' : '#'
   | '\u0024-\u0027'
   // '\u0028' : '('
   
   // '\u0029' : ')'
   | '\u002A-\u002B'
   // '\u002C' : ','
   | '\u002D-\u002E'
   // '\u002F' : '/'
   | '\u0030-\u003B'
   // '\u003C' : '<'
   | '\u003D'
   // '\u003E' : '>'
   
   // '\u003F' : '?'
   | '\u0040-\u005A'
   // '\u005B' : '['
   
   // '\u005C' : '\'
   
   // '\u005D' : ']'
   | '\u005E-\u007A'
   // '\u007B' : '{'
   | '\u007C'
   // '\u007D' : '}'
   | '\u007E'
   ;

quoted_path_character
   : '\u0020-\u0021'
   // '\u0022' : '\''
   | '\u0023-\u002E'
   // '\u002F' : '/'
   | '\u0030-\u007F'
   | VALID_NON_ASCII
   ;

unquoted_path_component
   : path_character+
   ;

quoted_path_component
   : quoted_path_character+
   ;

path_component
   : '/' (unquoted_path_component | '\u0022' quoted_path_component '\u0022')
   // The last path_component matched by this rule is referred to as 'file' in the semantics,
   
   // and the other path_components as 'directory'.
   
   ;

path
   : path_component+
   ;

local
   : parent_path
   | here_path
   | home_path
   // NOTE: Backtrack if parsing this alternative fails
   
   //
   
   // This is because the first character of this alternative will be '/', but
   
   // if the second character is '/' or '\' then this should have been parsed
   
   // as an operator instead of a path
   | absolute_path
   ;

parent_path
   : '..' path // Relative path
   
   ;

here_path
   : '.' path // Relative path
   
   ;

home_path
   : '~' path // Home_anchored path
   
   ;

absolute_path
   : path // Absolute path
   
   // `http[s]` URI grammar based on RFC7230 and RFC 3986 with some differences
   
   // noted below
   
   ;

scheme
   : 'http' 's'?
   // NOTE: This does not match the official grammar for a URI.  Specifically:
   
   //
   
   // * this does not support fragment identifiers, which have no meaning within
   
   //   Dhall expressions and do not affect import resolution
   
   // * the characters '(' ')' and ',' are not included in the `sub_delims` rule:
   
   //   in particular, these characters can't be used in authority, path or query
   
   //   strings.  This is because those characters have other meaning in Dhall
   
   //   and it would be confusing for the comma in
   
   //       [http://example.com/foo, bar]
   
   //   to be part of the URL instead of part of the list.  If you need a URL
   
   //   which contains parens or a comma, you must percent_encode them.
   
   //
   
   // Reserved characters in quoted path components should be percent_encoded
   
   // according to https://tools.ietf.org/html/rfc3986#section-2
   
   ;

http_raw
   : scheme '://' authority path_abempty ('?' query)?
   ;

path_abempty
   : ('/' segment)*
   // NOTE: Backtrack if parsing the optional user info prefix fails
   
   ;

authority
   : (userinfo '@')? host (':' port)?
   ;

userinfo
   : (UNRESERVED | pct_encoded | SUB_DELIMS | ':')*
   ;

host
   : IP_literal
   | IPv4address
   | domain
   ;

port
   : DIGIT*
   ;

IP_literal
   : '[' (IPv6address | IPvFuture) ']'
   ;

IPvFuture
   : 'v' HEXDIG+ '.' (UNRESERVED | SUB_DELIMS | ':')+
   // NOTE: Backtrack when parsing each alternative
   
   ;

IPv6address
   : H16 ':' H16 ':' H16 ':' H16 ':' H16 ':' H16 ':' LS32
   | '::' H16 ':' H16 ':' H16 ':' H16 ':' H16 ':' LS32
   | H16? '::' H16 ':' H16 ':' H16 ':' H16 ':' LS32
   | (H16 (':' H16)?)? '::' H16 ':' H16 ':' H16 ':' LS32
   | (H16 (':' H16 (':' H16)?)?)? '::' H16 ':' H16 ':' LS32
   | (H16 (':' H16 (':' H16 (':' H16)?)?)?)? '::' H16 ':' LS32
   | (H16 (H16 (':' H16 (':' H16 (':' H16)?)?)?)?)? '::' LS32
   | (H16 (H16 (H16 (':' H16 (':' H16 (':' H16)?)?)?)?)?)? '::' H16
   | (H16 (H16 (H16 (H16 (':' H16 (':' H16 (':' H16)?)?)?)?)?)?)? '::'
   ;

H16
   : HEXDIG (HEXDIG (HEXDIG HEXDIG?)?)?
   ;

LS32
   : H16 ':' H16
   | IPv4address
   ;

IPv4address
   : DEC_OCTET '.' DEC_OCTET '.' DEC_OCTET '.' DEC_OCTET
   // NOTE: Backtrack when parsing these alternatives
   
   ;

DEC_OCTET
   : '25' '\u0030-\u0035' // 250-255
   | '2' '\u0030-\u0034' DIGIT // 200-249
   | '1' DIGIT DIGIT // 100-199
   | '\u0031-\u0039' DIGIT // 10-99
   | DIGIT // 0-9
   
   // Look in RFC3986 3.2.2 for
   
   // 'A registered name intended for lookup in the DNS'
   
   ;

domain
   : domainlabel ('.' domainlabel)* '.'?
   ;

domainlabel
   : ALPHANUM+ ('-'+ ALPHANUM+)*
   ;

segment
   : pchar*
   ;

pchar
   : UNRESERVED
   | pct_encoded
   | SUB_DELIMS
   | ':'
   | '@'
   ;

query
   : (pchar | '/' | '?')*
   ;

pct_encoded
   : '%' HEXDIG HEXDIG
   ;

UNRESERVED
   : ALPHANUM
   | '-'
   | '.'
   | '_'
   | '~'
   // this is the RFC3986 sub_delims rule, without '(', ')' or ','
   
   // see comments above the `http_raw` rule above
   
   ;

SUB_DELIMS
   : '!'
   | '$'
   | '&'
   | '\' '
   | '* '
   | '+ '
   | ' //'
   | '='
   ;

http
   : http_raw (whsp1 USING whsp1 import_expression)?
   // Dhall supports unquoted environment variables that are Bash_compliant or
   
   // quoted environment variables that are POSIX_compliant
   
   ;

env
   : 'env:' (bash_environment_variable | '\u0022' posix_environment_variable '\u0022')
   // Bash supports a restricted subset of POSIX environment variables.  From the
   
   // Bash `man` page, an environment variable name is:
   
   //
   
   // > A word consisting only of  alphanumeric  characters  and  under_scores,  and
   
   // > beginning with an alphabetic character or an under_score
   
   ;

bash_environment_variable
   : (ALPHA | '_') (ALPHANUM | '_')*
   // The POSIX standard is significantly more flexible about legal environment
   
   // variable names, which can contain alerts (i.e. '\a'), whitespace, or
   
   // punctuation, for example.  The POSIX standard says about environment variable
   
   // names:
   
   //
   
   // > The value of an environment variable is a string of characters. For a
   
   // > C_language program, an array of strings called the environment shall be made
   
   // > available when a process begins. The array is pointed to by the external
   
   // > variable environ, which is defined as:
   
   // >
   
   // >     extern char **environ//
   
   // >
   
   // > These strings have the form name=value// names shall not contain the
   
   // > character '='. For values to be portable across systems conforming to IEEE
   
   // > Std 1003.1-2001, the value shall be composed of characters from the portable
   
   // > character set (except NUL and as indicated below).
   
   //
   
   // Note that the standard does not explicitly state that the name must have at
   
   // least one character, but `env` does not appear to support this and `env`
   
   // claims to be POSIX_compliant.  To be safe, Dhall requires at least one
   
   // character like `env`
   
   ;

posix_environment_variable
   : posix_environment_variable_character+
   // These are all the characters from the POSIX Portable Character Set except for
   
   // '\0' (NUL) and '='.  Note that the POSIX standard does not explicitly state
   
   // that environment variable names cannot have NUL.  However, this is implicit
   
   // in the fact that environment variables are passed to the program as
   
   // NUL_terminated `name=value` strings, which implies that the `name` portion of
   
   // the string cannot have NUL characters
   
   ;

posix_environment_variable_character
   : '\u005C' // '\'    Beginning of escape sequence
   ('\u0022' // '''    quotation mark  U+0022
   | '\u005C' // '\'    reverse solidus U+005C
   | '\u0061' // 'a'    alert           U+0007
   | '\u0062' // 'b'    backspace       U+0008
   | '\u0066' // 'f'    form feed       U+000C
   | '\u006E' // 'n'    line feed       U+000A
   | '\u0072' // 'r'    carriage return U+000D
   | '\u0074' // 't'    tab             U+0009
   | '\u0076' // 'v'    vertical tab    U+000B
   )
   // Printable characters except double quote, backslash and equals
   | '\u0020-\u0021'
   // '\u0022' : '''
   | '\u0023-\u003C'
   // '\u003D' : '='
   | '\u003E-\u005B'
   // '\u005C' : '\'
   | '\u005D-\u007E'
   ;

import_type
   : MISSING
   | local
   | http
   | env
   ;

hash
   : 'sha256:' HEXDIG64 // 'sha256:XXX...XXX'
   
   ;

HEXDIG64
   : HEXDIG16 HEXDIG16 HEXDIG16 HEXDIG16
   ;

HEXDIG16
   : HEXDIG4 HEXDIG4 HEXDIG4 HEXDIG4
   ;

HEXDIG4
   : HEXDIG HEXDIG HEXDIG HEXDIG
   ;

import_hashed
   : import_type (whsp1 hash)?
   // 'http://example.com'
   
   // './foo/bar'
   
   // 'env:FOO'
   
   ;

import_
   : import_hashed (whsp1 AS whsp1 (Text | Location | Bytes))?
   ;

expression
   :
   // '\(x : a) -> b'
   lambda whsp '(' whsp nonreserved_label whsp ':' whsp1 expression whsp ')' whsp arrow whsp expression
   //
   
   // 'if a then b else c'
   | IF whsp1 expression whsp THEN whsp1 expression whsp ELSE whsp1 expression
   //
   
   // 'let x : t : e1 in e2'
   
   // 'let x     : e1 in e2'
   
   // We allow dropping the `in` between adjacent let_expressions// the following are equivalent:
   
   // 'let x : e1 let y : e2 in e3'
   
   // 'let x : e1 in let y : e2 in e3'
   | let_binding IN whsp1 expression+
   //
   
   // 'forall (x : a) -> b'
   | FORALL whsp '(' whsp nonreserved_label whsp ':' whsp1 expression whsp ')' whsp arrow whsp expression
   //
   
   // 'a -> b'
   
   //
   
   // NOTE: Backtrack if parsing this alternative fails
   | operator_expression whsp arrow whsp expression
   //
   
   // 'a with x : b'
   
   //
   
   // NOTE: Backtrack if parsing this alternative fails
   | with_expression
   //
   
   // 'merge e1 e2 : t'
   
   //
   
   // NOTE: Backtrack if parsing this alternative fails since we can't tell
   
   // from the keyword whether there will be a type annotation or not
   | MERGE whsp1 import_expression whsp1 import_expression whsp ':' whsp1 expression
   //
   
   // '[] : t'
   
   //
   
   // NOTE: Backtrack if parsing this alternative fails since we can't tell
   
   // from the opening bracket whether or not this will be an empty list or
   
   // a non_empty list
   | empty_list_literal
   //
   
   // 'toMap e : t'
   
   //
   
   // NOTE: Backtrack if parsing this alternative fails since we can't tell
   
   // from the keyword whether there will be a type annotation or not
   | TOMAP whsp1 import_expression whsp ':' whsp1 expression
   //
   
   // 'assert : Natural/even 1 :== False'
   | ASSERT whsp ':' whsp1 expression
   //
   
   // 'x : t'
   | annotated_expression
   // Nonempty_whitespace to disambiguate `env:VARIABLE` from type annotations
   
   ;

annotated_expression
   : operator_expression (whsp ':' whsp1 expression)?
   // 'let x : e1'
   
   ;

let_binding
   : LET whsp1 nonreserved_label whsp (':' whsp1 expression whsp)? '=' whsp expression whsp1
   // '[] : t'
   
   ;

empty_list_literal
   : '[' whsp (',' whsp)? ']' whsp ':' whsp1 expression
   ;

with_expression
   : import_expression (whsp1 WITH whsp1 with_clause)+
   ;

with_clause
   : with_component (whsp '.' whsp with_component)* whsp '=' whsp operator_expression
   ;

operator_expression
   : equivalent_expression
   ;

equivalent_expression
   : import_alt_expression (whsp equivalent whsp import_alt_expression)* // Nonempty_whitespace to disambiguate `http://a/a?a`
   
   ;

import_alt_expression
   : or_expression (whsp '?' whsp1 or_expression)*
   ;

or_expression
   : plus_expression (whsp '||' whsp plus_expression)*
   ;

plus_expression
   : text_append_expression (whsp '+' whsp1 text_append_expression)* // Nonempty_whitespace to disambiguate `f +2`
   
   ;

text_append_expression
   : list_append_expression (whsp '++' whsp list_append_expression)*
   ;

list_append_expression
   : and_expression (whsp '#' whsp and_expression)*
   ;

and_expression
   : combine_expression (whsp '&&' whsp combine_expression)*
   ;

combine_expression
   : prefer_expression (whsp combine whsp prefer_expression)*
   ;

prefer_expression
   : combine_types_expression (whsp prefer whsp combine_types_expression)*
   ;

combine_types_expression
   : times_expression (whsp combine_types whsp times_expression)*
   ;

times_expression
   : equal_expression (whsp '*' whsp equal_expression)*
   ;

equal_expression
   : not_equal_expression (whsp '==' whsp not_equal_expression)*
   ;

not_equal_expression
   : application_expression (whsp '!=' whsp application_expression)*
   // Import expressions need to be separated by some whitespace, otherwise there
   
   // would be ambiguity: `./ab` could be interpreted as 'import the file `./ab`',
   
   // or 'apply the import `./a` to label `b`'
   
   ;

application_expression
   : first_application_expression (whsp1 import_expression)*
   ;

first_application_expression
   :
   // 'merge e1 e2'
   MERGE whsp1 import_expression whsp1 import_expression
   //
   
   // 'Some e'
   | KEYWORDSOME whsp1 import_expression
   //
   
   // 'toMap e'
   | TOMAP whsp1 import_expression
   //
   
   // 'showConstructor e'
   | SHOW_CONSTRUCTOR whsp1 import_expression
   //
   | import_expression
   ;

import_expression
   : import_
   | completion_expression
   ;

completion_expression
   : selector_expression (whsp complete whsp selector_expression)?
   // `record.field` extracts one field of a record
   
   //
   
   // `record.{ field0, field1, field2 }` projects out several fields of a record
   
   //
   
   // NOTE: Backtrack when parsing the `*('.' ...)`.  The reason why is that you
   
   // can't tell from parsing just the period whether 'foo.' will become 'foo.bar'
   
   // (i.e. accessing field `bar` of the record `foo`) or `foo./bar` (i.e. applying
   
   // the function `foo` to the relative path `./bar`)
   
   ;

selector_expression
   : primitive_expression (whsp '.' whsp selector)*
   ;

selector
   : any_label
   | labels
   | type_selector
   ;

labels
   : '{' whsp (',' whsp)? (any_label_or_some whsp (',' whsp any_label_or_some whsp)* (',' whsp)?)? '}'
   ;

type_selector
   : '(' whsp expression whsp ')'
   // NOTE: Backtrack when parsing the first three alternatives (i.e. the numeric
   
   // literals).  This is because they share leading characters in common
   
   ;

primitive_expression
   : temporal_literal
   //
   
   // '2.0'
   | double_literal
   //
   
   // '2'
   | natural_literal
   //
   
   // '+2'
   | integer_literal
   //
   
   // ''ABC''
   | text_literal
   //
   
   // '0x'01234567689abcdef''
   | bytes_literal
   //
   
   // '{ foo : 1      , bar : True }'
   
   // '{ foo : Integer, bar : Bool }'
   | '{' whsp (',' whsp)? record_type_or_literal whsp '}'
   //
   
   // '< Foo : Integer | Bar : Bool >'
   
   // '< Foo | Bar : Bool >'
   | '<' whsp ('|' whsp)? union_type whsp '>'
   //
   
   // '[1, 2, 3]'
   | non_empty_list_literal
   //
   
   // 'x'
   
   // 'x@2'
   | identifier
   //
   
   // '( e )'
   | '(' complete_expression ')'
   ;

record_type_or_literal
   : empty_record_literal
   | non_empty_record_type_or_literal?
   ;

empty_record_literal
   : '=' (whsp ',')?
   ;

non_empty_record_type_or_literal
   : (non_empty_record_type | non_empty_record_literal)
   ;

non_empty_record_type
   : record_type_entry (whsp ',' whsp record_type_entry)* (whsp ',')?
   ;

record_type_entry
   : any_label_or_some whsp ':' whsp1 expression
   ;

non_empty_record_literal
   : record_literal_entry (whsp ',' whsp record_literal_entry)* (whsp ',')?
   // If the `record_literal_normal_entry` is absent, that represents a punned
   
   // record entry, such as in `{ x }`, which is a short_hand for `{ x : x }`
   
   ;

record_literal_entry
   : any_label_or_some (record_literal_normal_entry)?
   ;

record_literal_normal_entry
   : (whsp '.' whsp any_label_or_some)* whsp '=' whsp expression
   // If the `union_type_entry` is absent, that represents an empty union
   
   // alternative, such as in `< Heads | Tails >`
   
   ;

union_type
   : (union_type_entry (whsp '|' whsp union_type_entry)* (whsp '|')?)?
   // x : Natural
   
   // x
   
   ;

union_type_entry
   : any_label_or_some (whsp ':' whsp1 expression)?
   ;

non_empty_list_literal
   : '[' whsp (',' whsp)? expression whsp (',' whsp expression whsp)* (',' whsp)? ']'
   // We provide special support for the Unix shebang convention, by permitting
   
   // `#!` as a line comment only on the first lines
   
   ;

shebang
   : '#!' NOT_END_OF_LINE* END_OF_LINE
   ;

complete_expression
   : whsp expression whsp
   // This just adds surrounding whitespace for the top_level of the program in a Dhall file.
   
   ;

complete_dhall_file
   : shebang* complete_expression line_comment_prefix?
   ;

