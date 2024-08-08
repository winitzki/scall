# Specification and implementation of "Nano-Dhall"

The "Nano-Dhall" project illustrates how a (very small) functional programming language can be specified and implemented.
Nano-Dhall is a subset of Dhall limited to:

- Function types `∀(a : t1) → t2` and function values `λ(a : t) → b`.
- Function application: `f x`, grouping to the left, so that `f x y = (f x) y`.
- Built-in type `Natural` with operations `a + b`, `a * b`, and `Natural/fold`
- Built-in type `Text` with the concatenation operation (`x ++ y`).
- Type universes `Type` and `Kind`, so that we have typing judgments `Nat : Type` and `Type : Kind`. (The symbol `Kind` has no type.)

We have omitted records, union types, imports and many other features of Dhall.
But all of those features (except imports) can be encoded in Nano-Dhall using just functions and types.

The syntax of Nano-Dhall is simplified so that there are fewer keywords and fewer equivalent ways of writing the same things.

We kept just two built-in types (`Natural` and `Text`) with some built-in operations, in order to show how such features are implemented.

For clarity, the impementation will be as simple and straightforward as possible.

### Encoding various features in Nano-Dhall

Here we will briefly show examples of Nano-Dhall code that reproduce some of the missing features.

- Void type: `∀(a : Type) → a` (instead of Dhall's `<>`)
- Unit type:  `∀(a : Type) → ∀(x : a) → a` instead of Dhall's `{}`
- Unit value: `λ(a : Type) → λ(x : a) → x` instead of Dhall's `{=}`
- "Let"-expression: `(λ(a : t) → b) x` instead of Dhall's `let a : t = x in b`. We will replace "let"-expressions by "lambda"-expressions at parsing time.
- Pair type: `λ(a : Type) → λ(b : Type) → ∀(r : Type) → ∀(k : a → b → r) → r` instead of Dhall's `λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b}`
- Pair value: `λ(a : Type) → λ(b : Type) → λ(r : Type) → λ(k : a → b → r) → r`

### Syntax and parsing into the syntax tree

The syntax of Nano-Dhall is described in the ABNF format.
This is a greatly simplified version of `dhall.abnf`.

The main entry point is `complete-expression`.

```
complete-expression = whsp expression whsp

; Optional whitespace: zero or more whitespace characters.
whsp = *whitespace-chunk

; Non-empty whitespace: one or more whitespace characters.
whsp1 = 1*whitespace-chunk

whitespace-chunk =
      " "
    / tab
    / end-of-line


expression =
  lambda whsp "(" whsp identifier whsp ":" whsp1 expression whsp ")" whsp arrow whsp expression
  / forall whsp "(" whsp identifier whsp ":" whsp1 expression whsp ")" whsp arrow whsp expression
  / let whsp1 identifier whsp ":" whsp1 expression whsp "=" whsp expression whsp1 "in" whsp1 expression
  / expression0

lambda        = %x3BB  / "\"
arrow         = %x2192 / "->"
forall        = "forall" / %x2200

keyword = forall / "let" / "in" ; We have just these keywords, for now.

; Precedence tower.
expression0 = expression1 [ whsp ":" whsp1 expression ] ; Type annotation.

expression1 = expression2 *(whsp "+" whsp expression2) ; Nat + Nat

expression2 = expression3 *(whsp "++" whsp expression3) ; Text ++ Text

expression3 = expression4 *(whsp "*" whsp expression4) ; Nat * Nat

expression4 = expression5 *(whsp1 expression5) ; Function application.
; End of precedence tower.
expression5 = primitive-expression

primitive-expression = natural-literal / text-literal / builtin / identifier / "(" complete-expression ")"

natural-literal = NONZERODIGIT *DIGIT / "0"

builtin =  "Natural/fold" / "Natural" / "Text" / "Type" / "Kind"

identifier = builtin 1*label-next-char / !builtin label

label =
       keyword 1*label-next-char
     / !keyword (label-first-char *label-next-char)
label-first-char = ALPHA / "_"
label-next-char = ALPHANUM / "-" / "/" / "_"

; Uppercase or lowercase ASCII letter.
ALPHA = %x41-5A / %x61-7A

; ASCII digit
DIGIT = %x30-39  ; 0-9

NONZERODIGIT = %x31-39

ALPHANUM = ALPHA / DIGIT

text-literal = %x22 *inside-double-quote %x22

; Printable characters except double quote and backslash.
inside-double-quote =
      %x20-21
        ; %x22 = '"'
    / %x23-5B
        ; %x5C = "\"
    / %x5D-7F
    / valid-non-ascii

valid-non-ascii =    ; Let us just support some Unicode.
      %x80-D7FF
    ; %xD800-DFFF = surrogate pairs
    / %xE000-FFFD
    ; %xFFFE-FFFF = non-characters
    / %x10000-1FFFD
```

The result of parsing is a syntax tree. The data type for the tree could look like this (Haskell) code:

```haskell
type Ident = String
type Operator = Plus | Concat | Times
data Expr = Lambda Ident Expr Expr  -- λ(ident : expr1) → expr2
   | Forall Ident Expr Expr         -- ∀(ident : expr1) → expr2
   | Annot Expr Expr                -- expr1 : expr2
   | Op Expr Operator Expr          -- expr1 operator expr2
   | App Expr Expr                  -- expr1 expr2
   | NaturalLit Int                 -- 123
   | TextLit String                 -- "abc"
   | Var Ident Int   -- A variable with a de Bruijn index. In Nano-Dhall, `x@1`.
   | Builtin String  -- One of the built-in symbols (`Natural`, `Type`, etc.).
```

Remarks:

- Types and values are both represented by an expression (`Expr`).
  Later, the type-checking procedure will figure out which is which.
  This allows us to write code like `λ(a : Type) → λ(x : a) → ...`

- Multiple-arity operations are parsed into left-associative, nested binary operations.
  For example, `1 + 2 + 3` is parsed into the following expression tree:

```haskell
Op (Op (NaturalLit 1) Plus (NaturalLit 2)) Plus (NaturalLit 3)
```

- All "let"-expressions are immediately rewritten as `App` terms.

- All built-in operations are associative (as in Dhall).

- Nano-Dhall does not support explicit de Bruijn indices in the syntax; but internally, all variables must have a de Bruijn index.
  Parsing `x` will produce `Var "x" 0`, with the de Bruijn index equal to `0`.

Any parsing library can be used to implement parsing.
The parsing procedure uses negative lookahead in some places, to prevent variable names from being keywords or built-in symbols.

### Type checking

Type checking in Nano-Dhall is a function from an input pair `(context, expression)` to an output .

The input `context` is a set of `(variable, expression)` pairs, showing all the variables whose types are already known.
The input `expression` is a Nano-Dhall expression with or without existing type annotations.

The output of typechecking is either a success with an output `expression` or a failure with a message describing a type error.
In case of success, the output `expression` has a type annotation indicating the type of the expression.

For example, the typechecking of `(context = [y : Natural], λ(x : Natural) → x + 1) ` succeeds and outputs:

`(λ(x : Natural) → x + 1) : ∀(x : Natural) → Natural`

This typechecking function is different from what is described in the Dhall standard.
There, typechecking does not modify the original expression but merely computes its type (or determines that there is a type error).

Making the typechecker return a different expression opens further possibilities for improving the language's usability and tooling:

- One could return partial type-checking results (with type annotations at valid sub-expressions) even when there are type errors in the input.
- The evaluator can do more simplification if type information is embedded into the expression being evaluated. Currently, Dhall's evaluator (beta-normalization) is designed to work entirely without any type information. This limits its ability to perform certain simplifications that are possible only if types of certain sub-expressions are known.
- Type inference and/or term inference can be performed at the type-checking stage. The output of type checking can be an expression not only with additional type annotations, but also with some additional terms; for instance, automatically inferred type parameters or other inferrable terms.

The type-checking algorithm proceeds recursively through the structure of the given expression.
When a sub-expression is a function or a function type, a new variable is bound.
So, the type-checking for the function body will proceed with the context that contains the new variable.
To avoid name clashes, the body needs to be modified if it already contains a variable with the same name.



### Alpha-normalization

### Beta-normalization

### One-place type inference



