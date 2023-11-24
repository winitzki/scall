package io.chymyst.dhall

import fastparse.Parsed
import geny.Generator.from
import io.chymyst.dhall.CBORmodel.CBytes
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.ExpressionScheme.{BytesLiteral, Import, TextLiteral}
import io.chymyst.dhall.SyntaxConstants.Builtin.Text
import io.chymyst.dhall.SyntaxConstants.FilePrefix.Here
import io.chymyst.dhall.SyntaxConstants.ImportMode.{Code, Location}
import io.chymyst.dhall.SyntaxConstants.Operator.Alternative
import io.chymyst.dhall.SyntaxConstants.{Builtin, FieldName, FilePrefix, ImportMode, ImportType}
import io.chymyst.dhall.CBORmodel.CBytes
import io.chymyst.dhall.ImportResolution.ImportContext
import io.chymyst.dhall.ImportResolutionResult._
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.SyntaxConstants.ImportType.{Path, Remote}
import io.chymyst.dhall.SyntaxConstants._

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.chaining.scalaUtilChainingOps
import scala.util.{Failure, Success, Try}

/*
## Import resolution judgment

The import resolution phase replaces all imports with the expression located
at that import, transitively resolving imports within the imported expression if
necessary.

Import resolution is a function of the following form:

    (Δ, here) × Γ₀ ⊢ e₀ ⇒ e₁ ⊢ Γ₁

... where

 * `(Δ, here)` (an input) is an ordered non-empty list of visited imports used to
  detect import cycles
 * `here` is the current import
 * `Δ` is the ordered history of 0 or more imports in the visited set that
      the interpreter visited along the way to `here`
 * `Γ₀` (an input) is an unordered map from imports to expressions representing
  the state of the filesystem/environment/web before the import
 * `Γ₀(import)` means to retrieve the expression located at `import`
 * `e₀` (an input) is the expression to resolve
 * `e₁` (an output) is the import-free resolved expression
 * `Γ₁` (an output) is an unordered map from imports to expressions representing
  the state of the filesystem/environment/web after the import
 * `Γ₁, import = x` means to save `x` to the resource identified by `import`

If an expression is an import (i.e. a URL, file path, or environment variable),
then you retrieve the expression from the canonicalized path and transitively
resolve imports within the retrieved expression:


    headersPath = env:DHALL_HEADERS ? "${XDG_CONFIG_HOME}/dhall/headers.dhall" ? ~/.config/dhall/headers.dhall ? []
    Γ(headersPath) = userHeadersExpr
    (Δ, parent, headersPath) × Γ₀ ⊢ userHeadersExpr ⇒ userHeaders ⊢ Γ₁
    getKey(userHeaders, origin, []) = headers  ; Extract the first `mapValue` from `userHeaders`
                                               ; with a `mapValue` equal to `origin`,
                                               ; falling back to `[]` if no such key is found.
    parent </> https://authority directory file using headers = import₁
    canonicalize(import₁) = child
    referentiallySane(parent, child)
    Γ(child) = e₀ using responseHeaders  ; Retrieve the expression, possibly
                                         ; binding any response headers to
                                         ; `responseHeaders` if child was a
                                         ; remote import
    corsCompliant(parent, child, responseHeaders)  ; If `child` was not a remote
                                                   ; import and therefore had no
                                                   ; response headers then skip
                                                   ; this CORS check
    (Δ, parent, child) × Γ₀ ⊢ e₀ ⇒ e₁ ⊢ Γ₁
    ε ⊢ e₁ : T
    ────────────────────────────────────  ; * child ∉ (Δ, parent)
    (Δ, parent) × Γ₀ ⊢ https://authority directory file ⇒ e₁ ⊢ Γ₁  ; * import₀ ≠ missing

    parent </> import₀ = import₁
    canonicalize(import₁) = child
    referentiallySane(parent, child)
    Γ(child) = e₀ using responseHeaders  ; Retrieve the expression, possibly
                                         ; binding any response headers to
                                         ; `responseHeaders` if child was a
                                         ; remote import
    corsCompliant(parent, child, responseHeaders)  ; If `child` was not a remote
                                                   ; import and therefore had no
                                                   ; response headers then skip
                                                   ; this CORS check
    (Δ, parent, child) × Γ₀ ⊢ e₀ ⇒ e₁ ⊢ Γ₁
    ε ⊢ e₁ : T
    ────────────────────────────────────  ; * child ∉ (Δ, parent)
    (Δ, parent) × Γ₀ ⊢ import₀ ⇒ e₁ ⊢ Γ₁  ; * import₀ ≠ missing


Carefully note that the fully resolved import must successfully type-check with
an empty context.  Imported expressions may not contain any free variables.

Also note that the `child ∉ Δ` forbids cyclic imports to prevent
non-termination from being (trivially) introduced via the import system.  An
import cycle is an import resolution error.

If an import ends with `as Text`, import the raw contents of the file as a
`Text` value instead of importing the file a Dhall expression:


    parent </> import₀ = import₁
    canonicalize(import₁) = child
    referentiallySane(parent, child)
    Γ(child) = "s" using responseHeaders  ; Read the raw contents of the file
    corsCompliant(parent, child, responseHeaders)
    ───────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Text ⇒ "s" ⊢ Γ


Carefully note that `"s"` in the above judgment is a Dhall `Text` literal.  This
implies that if you import an expression as `Text` and you also protect the
import with a semantic integrity check then you encode the string literal
as a Dhall expression and hash that.  The semantic integrity check is not a
hash of the raw underlying text.

If an import ends with `as Bytes`, import the raw contents of the file as a
`Bytes` value instead of importing the file a Dhall expression:


    parent </> import₀ = import₁
    canonicalize(import₁) = child
    referentiallySane(parent, child)
    Γ(child) = 0x"0123456789abcdef" using responseHeaders  ; Read the raw contents of the file
    corsCompliant(parent, child, responseHeaders)
    ─────────────────────────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Bytes ⇒ 0x"0123456789abcdef" ⊢ Γ


Similar to the `as Text` import, note that `0x"0123456789abcdef"` in the above
judgment is a Dhall `Bytes` literal and the same observation concerning semantic
integrity checks applies: The semantic integrity check is not a hash of the raw
underlying byte string but the one of the encoded Dhall expression.

If an import ends with `as Location`, import its location as a value of type
`< Local : Text | Remote : Text | Environment : Text | Missing >` instead of
importing the file a Dhall expression:


    parent </> import₀ = import₁
    canonicalize(import₁) = ./relative/path
    ───────────────────────────────────────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Location "./relative/path" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = ../parent/path
    ───────────────────────────────────────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Location "../parent/path" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = /absolute/path
    ───────────────────────────────────────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Location "/absolute/path" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = ~/home/path
    ───────────────────────────────────────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Location "~/home/path" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = https://example.com/path
    ───────────────────────────────────────────────────────────────────────────
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Remote "https://example.com/path" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = https://example.com/path using headers
    ───────────────────────────────────────────────────────────────────────────  ; Headers are not included in the path
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Remote "https://example.com/path" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = env:FOO
    ───────────────────────────────────────────────────────────────────────────  ; Headers are not included in the path
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Environment "FOO" ⊢ Γ


    parent </> import₀ = import₁
    canonicalize(import₁) = missing
    ───────────────────────────────────────────────────────────────────────────  ; Headers are not included in the path
    (Δ, parent) × Γ ⊢ import₀ as Location ⇒ < Local : Text | Remote : Text | Environment : Text | Missing >.Missing ⊢ Γ


Also note that since the expression is not resolved in any way - that is, we
only read in its location - there's no need to check if the path exists, if it's
referentially transparent, if it honours CORS, no header forwarding necessary,
etc.  Canonicalization and chaining are the only transformations applied to the
import.

When requesting a remote resource, include headers according to the user's
configuration. This configuration has the type:

```
List { mapKey : Text, mapValue : List { mapKey : Text, mapValue : Text } }
```

(i.e. "Map Text (Map Text Text)" using the prelude `Map` type constructor)

The toplevel map is known as the "origin header configuration", and the individual maps
which make up the keys of the toplevel map are known as the "per-origin headers".

The key of this expression represents an HTTP(s) origin, including
port - e.g. "github.com:443".

When importing from an origin which appears as a key in the origin header configuration,
an implementation must add the corresponding per-origin headers to each request.

The configuration is loaded from either the environment or a configuration file:

1. If the DHALL_HEADERS environment variable is set, interpret it as a dhall expression
2. Otherwise:
   a. If $XDG_CONFIG_HOME is set, load "$XDG_CONFIG_HOME/dhall/headers.dhall"
   b. Otherwise, load "~/.config/dhall/headers.dhall"

This file is optional. If the above steps attempt to load a path that doesn't exist,
it's treated as an empty list, not an error.

When importing the origin header configuration, local imports are resolved as normal.
Note that remote imports will not succeed, as that would implicitly require the origin
header configuration to be imported, which is rejected as a cyclic import.

If an import ends with `using headers`, resolve the `headers` import and use
the resolved expression as additional headers supplied to the HTTP request:


    headersPath = env:DHALL_HEADERS ? "${XDG_CONFIG_HOME}/dhall/headers.dhall" ? ~/.config/dhall/headers.dhall ? []
    Γ(headersPath) = userHeadersExpr
    (Δ, parent, headersPath) × Γ₀ ⊢ userHeadersExpr ⇒ userHeaders ⊢ Γ₁
    getKey(userHeaders, origin, []) = headers  ; Extract the first `mapValue` from `userHeaders`
                                               ; with a `mapValue` equal to `origin`,
                                               ; falling back to `[]` if no such key is found.
    ε ⊢ headers : List { mapKey : Text, mapValue : Text }
    (Δ, parent) × Γ₀ ⊢ requestHeaders ⇒ resolvedRequestHeaders ⊢ Γ₁
    ε ⊢ resolvedRequestHeaders : H
    H ∈ { List { mapKey : Text, mapValue : Text }, List { header : Text, value : Text } }
    resolvedRequestHeaders # headers ⇥ normalizedRequestHeaders
    parent </> https://authority directory file using normalizedRequestHeaders = import
    canonicalize(import) = child
    referentiallySane(parent, child)
    Γ₁(child) = e₀ using responseHeaders
      ; Append normalizedRequestHeaders to the above request's headers
    corsCompliant(parent, child, responseHeaders)
    (Δ, parent, child) × Γ₁ ⊢ e₀ ⇒ e₁ ⊢ Γ₂
    ε ⊢ e₁ : T
    ──────────────────────────────────────────────────────────────────────────  ; * child ∉ Δ
    (Δ, parent) × Γ₀ ⊢ https://authority directory file using requestHeaders ⇒ e₁ ⊢ Γ₂


For example, if `normalizedRequestHeaders` in the above judgment was:

    [ { mapKey = "Authorization", mapValue = "token 5199831f4dd3b79e7c5b7e0ebe75d67aa66e79d4" }
    ]

... then the HTTPS request for `https://authority directory file` would
include the following header line:

    Authorization: token 5199831f4dd3b79e7c5b7e0ebe75d67aa66e79d4


If headers appear both in the user configuration for a given origin and inline
(`using headers`), they are merged. If a header is specified in both locations,
the user configuration takes precedence over the inline headers. This allows
inline headers to be used as a fallback for compatibility with previous
versions of dhall or users without custom configuration.

If the import is protected with a `sha256:base16Hash` integrity check, then:

 * the import's normal form is encoded to a binary representation
 * the binary representation is hashed using SHA-256
 * the SHA-256 hash is base16-encoded
 * the base16-encoded result has to match the integrity check

An implementation MUST attempt to cache imports protected with an integrity
check using the hash as the lookup key.  An implementation that caches imports
in this way so MUST:

 * Cache the fully resolved, αβ-normalized expression, and encoded expression
 * Store the cached expression in `"${XDG_CACHE_HOME}/dhall/1220${base16Hash}"` if
  the `$XDG_CACHE_HOME` environment variable is defined and the path is readable
  and writeable
 * Otherwise, store the cached expression in
  `"${HOME}/.cache/dhall/1220${base16Hash}"` (`${LOCALAPPDATA}/dhall/1220${base16Hash}` on Windows) if the `$HOME` (`$LOCALAPPDATA` on Windows) environment variable is
  defined and the path is readable and writeable
 * Otherwise, not cache the expression at all

Cache filenames are prefixed with `1220` so that the filename is a valid
[multihash][] SHA-256 value.

An implementation SHOULD warn the user if the interpreter is unable to cache the
expression due to the environment variables being unset or the filesystem paths
not being readable or writeable.

Similarly, an implementation MUST follow these steps when importing an
expression protected by a semantic integrity check:

 * Check if there is a Dhall expression stored at either
  `"${XDG_CACHE_HOME}/dhall/1220${base16Hash}"` or
  `"${HOME}/.cache/dhall/1220${base16Hash}"`
 * If the file exists and is readable, verify the file's byte contents match the
  hash and then decode the expression from the bytes using the `decode` judgment
  instead of importing the expression
 * Otherwise, import the expression as normal

An implementation MUST fail and alert the user if hash verification fails,
either when importing an expression for the first time or importing from the
local cache.

Or in judgment form:


    Γ("${XDG_CACHE_HOME}/dhall/1220${base16Hash}") = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash                ; Verify the hash
    decode(binary) = e
    ─────────────────────────────────────────────────  ; Import is already cached under `$XDG_CACHE_HOME`
    (Δ, here) × Γ ⊢ import₀ sha256:base16Hash ⇒ e ⊢ Γ


    Γ("${LOCALAPPDATA}/dhall/1220${base16Hash}") = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash                ; Verify the hash
    decode(binary) = e
    ─────────────────────────────────────────────────  ; Otherwise, import is cached under `$LOCALAPPDATA`
    (Δ, here) × Γ ⊢ import₀ sha256:base16Hash ⇒ e ⊢ Γ


    Γ("${HOME}/.cache/dhall/1220${base16Hash}") = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash                ; Verify the hash
    decode(binary) = e
    ─────────────────────────────────────────────────  ; Otherwise, import is cached under `$HOME`
    (Δ, here) × Γ ⊢ import₀ sha256:base16Hash ⇒ e ⊢ Γ


    (Δ, here) × Γ₀ ⊢ import₀ ⇒ e₁ ⊢ Γ₁
    ε ⊢ e₁ : T
    e₁ ⇥ e₂
    e₂ ↦ e₃
    encode(e₃) = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash  ; Verify the hash
    ──────────────────────────────────────────────────────────────────────────────────────────────────────  ; Import is not cached, try to save under `$XDG_CACHE_HOME`
    (Δ, here) × Γ₀ ⊢ import₀ sha256:base16Hash ⇒ e₁ ⊢ Γ₁, "${XDG_CACHE_HOME}/dhall/1220${base16Hash}" = binary


    (Δ, here) × Γ₀ ⊢ import₀ ⇒ e₁ ⊢ Γ₁
    ε ⊢ e₁ : T
    e₁ ⇥ e₂
    e₂ ↦ e₃
    encode(e₃) = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash  ; Verify the hash
    ──────────────────────────────────────────────────────────────────────────────────────────────────────────  ; Otherwise, try `LOCALAPPDATA`
    (Δ, here) × Γ₀ ⊢ import₀ sha256:base16Hash ⇒ e₁ ⊢ Γ₁, "${LOCALAPPDATA}/dhall/1220${base16Hash}" = binary


    (Δ, here) × Γ₀ ⊢ import₀ ⇒ e₁ ⊢ Γ₁
    ε ⊢ e₁ : T
    e₁ ⇥ e₂
    e₂ ↦ e₃
    encode(e₃) = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash  ; Verify the hash
    ───────────────────────────────────────────────────────────────────────────────────────────────────  ; Otherwise, try `HOME`
    (Δ, here) × Γ₀ ⊢ import₀ sha256:base16Hash ⇒ e₁ ⊢ Γ₁, "${HOME}/.cache/dhall/1220${base16Hash}" = binary


    (Δ, here) × Γ₀ ⊢ import₀ ⇒ e₁ ⊢ Γ₁
    ε ⊢ e₁ : T
    e₁ ⇥ e₂
    e₂ ↦ e₃
    encode(e₃) = binary
    sha256(binary) = byteHash
    base16Encode(byteHash) = base16Hash                  ; Verify the hash
    ──────────────────────────────────────────────────── ; Otherwise, don't cache
    (Δ, here) × Γ₀ ⊢ import₀ sha256:base16Hash ⇒ e₁ ⊢ Γ₁


... where:

 * The `sha256` judgment stands in for the SHA-256 hashing algorithm
  specified in
  [RFC4634 - Section 8.2.2](https://tools.ietf.org/html/rfc4634#section-8.2.2),
  treated as a pure function from an arbitrary byte array to a 64-byte array.
 * The `base16Encode` judgement stands in for the base-16 encoding algorithm
  specified in
  [RFC4648 - Section 8](https://tools.ietf.org/html/rfc4648#section-8), treated
  as a pure function from a byte array to text.

The `?` operator lets you recover from some (but not all) import resolution
failures.

Specifically, `e₀ ? e₁` is equivalent to `e₁` if `e₀` contains any imports that
are *not cached* and *absent*, where an import is *not cached* if:

 * the import is not protected by an integrity check, or:
 * the import is protected by an integrity check but not available from the cache

… and an import is *absent* if:

 * the import references an environment variable that is not defined,
 * the import references a file that does not exist,
 * the import references URL that cannot be retrieved, or:
 * the import is the `missing` keyword, which is treated as an absent import

In other words, if any import cannot be retrieved or fetched from cache then the
`?` fallback is applied.

In contrast, `e₀ ? e₁` is equivalent to `e₀` if `e₀` successfully resolves or
`e₀` fails to resolve a sub-expression for any of the following reasons:

 * `e₀` imports an expression that fails to parse
 * `e₀` imports an expression that fails to type-check
 * `e₀` imports an expression that fails an integrity check
 * `e₀` imports an expression that fails due to a cyclic import

In other words, the fallback expression is ignored if the import is present but
fails for other reasons.

For example:

 * `e₀ sha256:… ? e₁` is equivalent to `e₀ sha256:…` if `e₀ sha256:…` is cached
 * `e₀ sha256:… ? e₁` is equivalent to `e₀ sha256:…` if `e₀ sha256:…` is not
  cached, but `e₀` is present and matches the integrity check
 * `e₀ sha256:… ? e₁` is equivalent to `e₀ sha256:…` if `e₀ sha256:…` is not
  cached, but `e₀` is present and does not match the integrity check (meaning
  that the expression as a whole is rejected without falling back to resolving
  `e₁`)
 * `e₀ sha256:… ? e₁` is equivalent to `e₁` if `e₀ sha256:…` is not cached and
  `e₀` is absent

Formally:


    (Δ, here) × Γ₀ ⊢ e₁ ⇒ e₂ ⊢ Γ₁
    ────────────────────────────────────  ; if `e₀` fails to resolve due to an
    (Δ, here) × Γ₀ ⊢ (e₀ ? e₁) ⇒ e₂ ⊢ Γ₁  ; import that is not cached and absent


    (Δ, here) × Γ₀ ⊢ e₀ ⇒ e₂ ⊢ Γ₁
    ────────────────────────────────────  ; if `e₀` successfully resolves or
    (Δ, here) × Γ₀ ⊢ (e₀ ? e₁) ⇒ e₂ ⊢ Γ₁  ; fails for any other reason


For all other cases, recursively descend into sub-expressions:


    ───────────────────────────────
    (Δ, here) × Γ₀ ⊢ x@n ⇒ x@n ⊢ Γ₁


    (Δ, here) × Γ₀ ⊢ A₀ ⇒ A₁ ⊢ Γ₁   (Δ, here) × Γ₁ ⊢ b₀ ⇒ b₁ ⊢ Γ₂
    ─────────────────────────────────────────────────────────────
    (Δ, here) × Γ₀ ⊢ λ(x : A₀) → b₀ ⇒ λ(x : A₁) → b₁ ⊢ Γ₂


    …


    ────────────────────────────────
    (Δ, here) × Γ₀ ⊢ Kind ⇒ Kind ⊢ Γ₁

 */
object ImportResolution {

  def chainWith[E](parent: ImportType[E], child: ImportType[E]): ImportType[E] = (parent, child) match {
    case (Remote(ImportURL(scheme1, authority1, path1, query1), headers1), Path(Here, path2))              =>
      Remote(ImportURL(scheme1, authority1, path1 chain path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Here, path2))                                           => Path(filePrefix, path1 chain path2)
    case (Remote(ImportURL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Parent, path2)) =>
      Remote(ImportURL(scheme1, authority1, path1 chainToParent path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Parent, path2))                                         => Path(filePrefix, path1 chainToParent path2)
    case _                                                                                                 => child
  }

  val corsHeader = "Access-Control-Allow-Origin".toLowerCase

  // This function returns `None` if there is no error in CORS compliance.
  def corsComplianceError(parent: ImportType[Expression], child: ImportType[Expression], responseHeaders: Map[String, Seq[String]]): Option[String] =
    (parent, child) match {
      // TODO: report issue: what if parent = Remote but child = Path, does the cors judgment then always succeed?
      case (Remote(ImportURL(scheme1, authority1, path1, query1), headers1), Remote(ImportURL(scheme2, authority2, path2, query2), headers2)) =>
        if (scheme1 == scheme2 && authority1 == authority2) None
        else
          responseHeaders.map { case (k, v) => (k.toLowerCase, v) }.get(corsHeader) match {
            case Some(Seq("*"))                                                                 => None
            case Some(Seq(other)) if other.toLowerCase == s"$scheme1://$authority1".toLowerCase => None
            case Some(other)                                                                    =>
              Some(
                s"Scheme or authority differs from parent $parent but response headers when fetching child $child are $responseHeaders, the CORS header is $other, importing is prohibited"
              )
            case None                                                                           => Some(s"Scheme or authority differs from parent $parent but no CORS header in child $child, headers $responseHeaders")
          }
      case (Remote(ImportURL(_, _, _, _), _), _)                                                                                              => Some(s"Remote parent $parent may not import a non-remote $child")
      case _                                                                                                                                  => None
    }

  lazy val typeOfImportAsLocation: Expression = UnionType(
    Seq(
      (ConstructorName("Local"), Some(~Builtin.Text)),
      (ConstructorName("Remote"), Some(~Builtin.Text)),
      (ConstructorName("Environment"), Some(~Builtin.Text)),
      (ConstructorName("Missing"), None),
    )
  ).sorted

  lazy val typeOfGenericHeadersForHost: Expression =
    Application(~Builtin.List, Expression(RecordType(Seq((FieldName("mapKey"), ~Builtin.Text), (FieldName("mapValue"), ~Builtin.Text)))))

  lazy val typeOfUserDefinedAlternativeHeadersForHost: Expression =
    Application(~Builtin.List, Expression(RecordType(Seq((FieldName("header"), ~Builtin.Text), (FieldName("value"), ~Builtin.Text)))))

  lazy val typeOfGenericHeadersForAllHosts: Expression =
    Application(~Builtin.List, Expression(RecordType(Seq((FieldName("mapKey"), ~Builtin.Text), (FieldName("mapValue"), typeOfGenericHeadersForHost)))))

  lazy val emptyHeadersForHost: Iterable[(String, String)] = Seq()

  private def readCached(cacheRoot: java.nio.file.Path, digest: BytesLiteral): Try[Expression] = {
    val digestHex  = digest.hex.toLowerCase
    val cachedPath = cacheRoot.resolve("1220" + digestHex)
    for {
      bytes   <- Try(Files.readAllBytes(cachedPath))
      ourHash <- Try(Semantics.computeHash(bytes))
      _       <- if (ourHash == digestHex) Success(())
                 else Failure(new Exception(s"SHA256 mismatch: cached at $cachedPath has a different hash ($ourHash)"))
      expr    <- Try(CBORmodel.decodeCbor1(bytes).toScheme: Expression)
    } yield expr
  }

  def readFirstCached(digest: BytesLiteral): Option[Expression] =
    dhallCacheRoots
      .map(readCached(_, digest))
      .map(_.tap { t =>
        if (t.isFailure) println(s"Warning: failure reading from cache: ${t.failed.get}")
      })                                          // TODO: print this failure only when the error is important (hash mismatch)
      .filter(_.isSuccess)
      .take(1).map(_.toOption).headOption.flatten // Force evaluation of the first valid operation over all candidate cache roots.

  def validateHashAndCacheResolved(expr: Expression, digest: Option[BytesLiteral]): ImportResolutionResult[Expression] = digest match {
    case None => Resolved(expr)

    case Some(BytesLiteral(hex)) =>
      val ourBytes = expr.alphaNormalized.betaNormalized.toCBORmodel.encodeCbor1
      val ourHash  = Semantics.computeHash(ourBytes).toLowerCase
      if (hex.toLowerCase == ourHash) {
        dhallCacheRoots
          .map { cachePath =>
            Try(Files.write(cachePath.resolve("1220" + ourHash), ourBytes))
            // TODO: log errors while writing the cache file
            // TODO verify that we will attempt to read the cached file from any of the locations, not just from the first one.
          }.filter(_.isSuccess)
          .take(1).headOption // Force evaluation of the first valid operation over all candidate cache roots.
        Resolved(expr)
      } else {
        val message = s"sha-256 mismatch: found $ourHash instead of specified $hex from expression ${expr.toDhall}"
        println(s"Error: $message")
        PermanentFailure(Seq(message))
      }
  }

  lazy val isWindowsOS: Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

  private def createAndCheckReadableWritable(path: java.nio.file.Path): Try[java.nio.file.Path] = Try {
    Files.createDirectories(path)
    if (Files.isReadable(path) && Files.isWritable(path)) path else throw new Exception(s"Path $path is not readable or not writable")
  }

  private def dhallCacheRoots: Iterator[java.nio.file.Path] = Seq(
    Try(Paths.get(scala.sys.env("XDG_CACHE_HOME")).resolve("dhall")),
    Try(
      if (isWindowsOS) Paths.get(scala.sys.env("LOCALAPPDATA")).resolve("dhall")
      else Paths.get(System.getProperty("user.home")).resolve(".cache").resolve("dhall")
    ),
  ).iterator.map(_.flatMap(createAndCheckReadableWritable)).collect { case Success(path) => path }

  final case class ImportContext(resolved: Map[Import[Expression], Expression]) {
    override def toString: String = resolved
      .map { case (k, v) =>
        k.toDhall.take(160) + (if (k.toDhall.length > 160) "..." else "") + " -> " + v.toDhall.take(160) + (if (v.toDhall.length > 160) "..." else "")
      }.mkString("Map(\n\t", "\n\t", "\n)")
  }

  // TODO report issue - imports.md does not say how to bootstrap reading a dhall expression from string, what is the initial "parent" import?
  // TODO workaround: allow the "visited" list to be empty initially? Or make the initial import "."?
  def resolveAllImports(expr: Expression, currentImport: Import[Expression]): Expression = {
    val initialVisited = currentImport

    val initState = ImportContext(Map())

    resolveImportsStep(expr, Seq(initialVisited), currentImport).run(initState) match {
      case (resolved, finalState) =>
        resolved match {
          case TransientFailure(messages) =>
            throw new Exception(s"Transient failure resolving ${expr.toDhall}: ${ImportResolutionResult.printFailures(messages)}")
          case PermanentFailure(messages) =>
            throw new Exception(s"Permanent failure resolving ${expr.toDhall}: ${ImportResolutionResult.printFailures(messages)}")
          case Resolved(r)                => r
        }
    }
  }

  def printVisited(visited: Seq[Import[Expression]]): String = visited.map(_.toDhall).mkString("[", ", ", "]")

  private def extractHeaders(h: Expression, keyName: String, valueName: String): Iterable[(String, String)] = h.betaNormalized.scheme match {
    case EmptyList(_)        => emptyHeadersForHost
    case NonEmptyList(exprs) =>
      exprs.map(_.scheme).map { case d @ RecordLiteral(_) =>
        (
          d.lookup(FieldName(keyName)).get.toPrimitiveValue.get.asInstanceOf[String],
          d.lookup(FieldName(valueName)).get.toPrimitiveValue.get.asInstanceOf[String],
        )
      }
    case other               =>
      throw new Exception(s"ERROR: internal error - headers must be of type ${typeOfGenericHeadersForHost.toDhall} but instead have ${other.toDhall}")
  }
  // Recursively resolve imports. See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
  // We will use `traverse` on `ExpressionScheme` with this Kleisli function, in order to track changes in the resolution context.
  // TODO: report issue to mention in imports.md (at the end) that the updates of the resolution context must be threaded through all resolved subexpressions.

  /** Perform one step of import resolution. This function may call itself on sub-expressions.
    *
    * Example:
    *
    * Suppose a file `./program` contains the URL string `http://a.org/lib/func1`.
    *
    * When we fetch that URL, we get the string `./func2`. When we fetch `http://a.org/lib/func2`, we get the string `0`.
    *
    * These imports are resolved like this:
    *
    * After parsing the file `./program`, we get an expression of type [[Import]]:
    *
    * {{{
    *   "http://a.org/lib/func1".dhall
    * }}}
    *
    * To resolve that, we will call:
    *
    * {{{
    *    resolveImportsStep(expr = "http://a.org/lib/func1".dhall, visited = Seq(), currentImport = "./program".dhall)
    * }}}
    *
    * Here, `visited` is empty because there are no previous imports before `./program`.
    *
    * This call will read the URL `http://a.org/lib/func1` and obtain the text `"./func2"`. Parsing that text will give an expression of type [[Import]].
    *
    * So, this is a nested import. To resolve this, we need to call:
    *
    * {{{
    *    resolveImportsStep(expr = "./func2".dhall, visited = Seq("./program".dhall), currentImport = "http://a.org/lib/func1".dhall)
    * }}}
    *
    * Because `"./func2".dhall` is again an expression of type [[Import]], we continue the import resolution.
    *
    * We chain the current import with `./func2` and obtain the new child import expression: `http://a.org/lib/func2`. We fetch that URL and parse the string
    * `0`. Then we call:
    *
    * {{{
    *   resolveImportsStep(expr = "0".dhall, visited = Seq("./program".dhall, "http://a.org/lib/func1".dhall), currentImport = "http://a.org/lib/func2".dhall)
    * }}}
    *
    * At this point the import resolution is finished and the final expression is computed as `0`.
    *
    * @param expr
    *   An expression in which imports need to be resolved.
    * @param visited
    *   List of nested import expressions that have been resolved before encountering this one. This may be empty.
    * @param parent
    *   The parent import expression that has been already resolved, its contents was fetched and parsed as `expr`.
    * @return
    *   A function that updates the import context and returns an [[ImportResolutionResult]].
    */
  def resolveImportsStep(expr: Expression, visited: Seq[Import[Expression]], parent: Import[Expression]): ImportResolutionStep[Expression] =
    ImportResolutionStep[Expression] { case stateGamma0 @ ImportContext(gamma) =>
      val (importResolutionResult, finalState) = expr.scheme match {
        // If `expr` is not an Import, we will defer to other `case` clauses to iterate over its subexpressions.
        case i @ Import(_, _, _)                 =>
          val child = Import.chainWith(parent, i).canonicalize

          val cyclicImportCheck =
            if (visited contains child)
              Left(PermanentFailure(Seq(s"Cyclic import of $child not allowed, imports already visited: ${visited.map(_.toDhall).mkString("; ")}")))
            else Right(())

          val referentialCheck =
            if (parent.importType allowedToImportAnother child.importType) Right(())
            else if (child.importMode == ImportMode.Location) Right(()) // Corner case: import as `Location` does not need the referential check.
            else Left(PermanentFailure(Seq(s"parent import expression ${parent.toDhall} may not import child ${child.toDhall} due to the referential check")))

          // println(s"DEBUG 1 got parent = ${parent.toDhall} and child = ${child.toDhall}")
          lazy val resolveIfAlreadyResolved = gamma.get(child) match {
            case Some(r) => Left(ImportResolutionResult.Resolved(r))
            case None    => Right(())
          }
          // TODO report issue - imports.md does not clearly explain `Γ(headersPath) = userHeadersExpr` and also whether Γ1 is being reused

          // val xdgOption = Option(System.getenv("XDG_CONFIG_HOME")).map(xdg => s""" ? "$xdg/dhall/headers.dhall"""").getOrElse("")

          lazy val defaultHeadersLocation =
            """env:DHALL_HEADERS ? "${env:XDG_CONFIG_HOME as Text}/dhall/headers.dhall" ? ~/.config/dhall/headers.dhall""".dhall

          lazy val (defaultHeadersForHost, stateGamma1) = child.importType.remoteOrigin match {
            case None                                  => (emptyHeadersForHost, stateGamma0)
            case Some((remoteScheme, remoteAuthority)) =>
              val (result, state01) = resolveImportsStep(defaultHeadersLocation, visited, parent).run(stateGamma0)
              result match {
                case Resolved(expr)                           =>
                  val headersForOrigin: Iterable[(String, String)] = (expr | typeOfGenericHeadersForAllHosts).inferType match {
                    case TypecheckResult.Valid(_)        =>
                      expr.betaNormalized.scheme match { // TODO report issue: imports.md does not say that headers must be beta-normalized before use
                        case NonEmptyList(exprs) =>
                          exprs.find {
                            case Expression(r @ RecordLiteral(_)) =>
                              r.lookup(FieldName("mapKey")) match {
                                case Some(
                                      Expression(TextLiteral(List(), originInHeaders))
                                    ) => // remoteOrigin = https://host  may match originInHeaders = host:443 and http://host may match host:80 unless port is specified in remoteOrigin.
                                  (originInHeaders.toLowerCase == remoteAuthority.toLowerCase) ||
                                  (remoteScheme == Scheme.HTTP && originInHeaders.toLowerCase == remoteAuthority.replaceAll(":[0-9]+$", "") + ":80") ||
                                  (remoteScheme == Scheme.HTTPS && originInHeaders.toLowerCase == remoteAuthority.replaceAll(":[0-9]+$", "") + ":443")

                                case None => false
                              } // contains Expression(TextLiteral.ofString(remoteOrigin)) =>

                            case _ => false
                          } match {
                            case Some(Expression(r @ RecordLiteral(_))) => extractHeaders(r.lookup(FieldName("mapValue")).get, "mapKey", "mapValue")
                            case None                                   =>
                              println(s"Warning: headers resolved from ${expr.toDhall} do not contain a map entry for origin '$remoteAuthority'")
                              emptyHeadersForHost
                          }
                        case _                   => emptyHeadersForHost
                      }
                    case TypecheckResult.Invalid(errors) =>
                      println(s"Warning: headers resolved from ${expr.toDhall} have a wrong type: $errors")
                      emptyHeadersForHost
                  }
                  (headersForOrigin, state01)
                case failure: ImportResolutionResult[Nothing] =>
                  println(s"Warning: failed to resolve headers: $failure")
                  (emptyHeadersForHost, stateGamma0)
              }
          }

          // Values of type Either[ImportResolutionResult[Expression], _] are used to report early results as Left().
          // At the end of the computation, we will have Either[ImportResolutionResult[Expression], ImportResolutionResult[Expression]] and we will `merge` that.
          lazy val resolveIfLocation: Either[ImportResolutionResult[Expression], Array[Byte] => ImportResolutionResult[Expression]] = child.importMode match {
            case Location =>
              val canonical                               = child.canonicalize
              // Need to process this first, because `missing as Location` is not a failure while `missing as` anything else must be a failure.
              val (field: FieldName, arg: Option[String]) = canonical.importType match {
                case ImportType.Missing         => (FieldName("Missing"), None)
                case Remote(url, _)             => (FieldName("Remote"), Some(url.toString))
                case p @ Path(_, _)             => (FieldName("Local"), Some(p.toString))
                case ImportType.Env(envVarName) => (FieldName("Environment"), Some(envVarName))
              }
              val withField: Expression                   = Field(typeOfImportAsLocation, field)
              val expr: Expression                        = arg match {
                case Some(text) => withField.apply(TextLiteral.ofString(text))
                case None       => withField
              }
              Left(Resolved(expr))

            case ImportMode.Code     =>
              Right(bytes =>
                Parser.parseDhallBytes(bytes) match {
                  case Parsed.Success(DhallFile(_, expr), _) => Resolved(expr)
                  case failure: Parsed.Failure               => PermanentFailure(Seq(s"failed to parse imported file: $failure"))
                }
              )
            case ImportMode.RawBytes => Right(bytes => Resolved(Expression(BytesLiteral(CBytes.byteArrayToHexString(bytes)))))
            case ImportMode.RawText  => Right(bytes => Resolved(Expression(TextLiteral.ofString(new String(bytes)))))
          }

          // Corner case: If the import mode is `Location`, we must not attempt to use the cache.
          lazy val resolveIfCached: Either[ImportResolutionResult[Expression], Unit] =
            if (child.importMode == ImportMode.Location)
              Right(())
            else
              child.digest.flatMap(readFirstCached) match {
                case Some(expr) => Left(Resolved(expr))
                case None       => Right(())
              }

          lazy val missingOrData: Either[ImportResolutionResult[Expression], Array[Byte]] = child.importType match {
            case ImportType.Missing => Left(TransientFailure(Seq("import is `missing` (perhaps not an error)")))

            case Remote(childUrl, childUserHeaders) =>
              // Verify the type of headers.
              val checkHeaderTypeGeneric: Either[ImportResolutionResult[Expression], Iterable[(String, String)]] = childUserHeaders match {
                case Some(headersExpr) =>
                  (headersExpr | typeOfGenericHeadersForHost).inferType match {
                    case TypecheckResult.Valid(_)        => Right(extractHeaders(headersExpr, "mapKey", "mapValue"))
                    case TypecheckResult.Invalid(errors) =>
                      Left(
                        PermanentFailure(Seq(s"import from url $childUrl failed typecheck for headers of type ${typeOfGenericHeadersForHost.toDhall}: $errors"))
                      )
                  }
                case None              => Right(emptyHeadersForHost)
              }
              val checkHeaderTypeSpecial: Either[ImportResolutionResult[Expression], Iterable[(String, String)]] = childUserHeaders match {
                case Some(headersExpr) =>
                  (headersExpr | typeOfUserDefinedAlternativeHeadersForHost).inferType match {
                    case TypecheckResult.Valid(_)        => Right(extractHeaders(headersExpr, "header", "value"))
                    case TypecheckResult.Invalid(errors) =>
                      Left(
                        PermanentFailure(
                          Seq(s"import from url $childUrl failed typecheck for headers of type ${typeOfUserDefinedAlternativeHeadersForHost.toDhall}: $errors")
                        )
                      )
                  }
                case None              => Right(emptyHeadersForHost)
              }
              (checkHeaderTypeGeneric orElse checkHeaderTypeSpecial) flatMap { userHeadersForHost =>
                val combinedHeaders: Iterable[(String, String)] =
                  (userHeadersForHost.toMap ++ defaultHeadersForHost.toMap).to(Iterable) // The default headers must override user headers.
                Try(requests.get(childUrl.toString, headers = combinedHeaders)) match {
                  case Failure(exception) => Left(TransientFailure(Seq(s"import failed from url $childUrl: $exception")))
                  case Success(response)  =>
                    corsComplianceError(parent.importType, child.importType, response.headers) match {
                      case Some(corsError) => Left(PermanentFailure(Seq(s"import from url $childUrl failed CORS check: $corsError")))
                      case None            => Right(response.bytes)
                    }
                }
              }

            case path @ Path(_, _) =>
              (for {
                javaPath <- Try(path.toJavaPath)
                bytes    <- Try(Files.readAllBytes(javaPath))
              } yield bytes) match {
                case Failure(exception) => Left(TransientFailure(Seq(s"Failed to read imported file: $exception")))
                case Success(bytes)     => Right(bytes)
              }

            case ImportType.Env(envVarName) =>
              Option(System.getenv(envVarName)) match {
                case Some(value) =>
                  Try(value.getBytes("UTF-8")) match {
                    case Failure(exception) => Left(PermanentFailure(Seq(s"Env variable '$envVarName' is not a valid UTF-8 string: $exception")))
                    case Success(utf8bytes) => Right(utf8bytes)
                  }
                case None        => Left(TransientFailure(Seq(s"Env variable '$envVarName' is undefined")))
              }
          }

          // Resolve imports in the expression we just parsed.
          val result: Either[ImportResolutionResult[Expression], Expression] = for {
            _                <- resolveIfAlreadyResolved
            _                <- resolveIfCached
            _                <- cyclicImportCheck
            _                <- referentialCheck
            readByImportMode <- resolveIfLocation
            bytes            <- missingOrData
            expr             <- Right(readByImportMode(bytes))
            successfullyRead <- expr match {
                                  case Resolved(x) => Right(x)
                                  case _           => Left(expr)
                                }
          } yield successfullyRead

          val newState: (ImportResolutionResult[Expression], ImportContext) = result match {
            case Left(gotEarlyResult)  => (gotEarlyResult, stateGamma1)
            case Right(readExpression) =>
              resolveImportsStep(readExpression, visited :+ parent, child).run(stateGamma1) match {
                case (result1, stateGamma2) =>
                  // If the expression was successfully imported, we need to type-check and beta-normalize it.
                  val result2: ImportResolutionResult[Expression] = result1.flatMap { r =>
                    r.inferType match { // Note: this type inference is done with empty context because imports may not have any free variables.
                      case TypecheckResult.Valid(_)          =>
                        Resolved(r.betaNormalized)
                      case TypecheckResult.Invalid(messages) =>
                        PermanentFailure(Seq(s"Type error in imported expression ${readExpression.toDhall}:${messages.mkString("\n\t", "\n\t", "\n")}"))
                    }
                  }
                  (result2, stateGamma2)
              }
          }
          // The new expression has been resolved (or failed).
          // Add the new resolved expression to the import context.
          newState match {
            case (result2, state2) =>
              result2.flatMap(validateHashAndCacheResolved(_, child.digest)) match {
                case Resolved(r) => (result2, state2.copy(state2.resolved.updated(child, r)))
                case failure     => (failure, state2)
              }
          }

        // Try resolving `lop`. If failed non-permanently, try resolving `rop`. Accumulate error messages.
        case ExprOperator(lop, Alternative, rop) =>
          resolveImportsStep(lop, visited, parent).run(stateGamma0) match {
            case resolved @ (Resolved(_), _) => resolved

            case failed @ (PermanentFailure(_), _) => failed

            case (TransientFailure(messages1), state1) =>
              resolveImportsStep(rop, visited, parent).run(state1) match {
                case resolved @ (Resolved(_), _)           => resolved
                case (PermanentFailure(messages2), state2) => (PermanentFailure(messages1 ++ messages2), state2)
                case (TransientFailure(messages2), state2) => (TransientFailure(messages1 ++ messages2), state2)
              }
          }

        case _ =>
          expr.scheme.traverse(resolveImportsStep(_, visited, parent)).run(stateGamma0) match {
            case (scheme, state) => (scheme.map(Expression.apply), state)
          }
      }
      val checkDigest                          = importResolutionResult.flatMap {
        case e @ Expression(Import(importType, importMode, digest)) => validateHashAndCacheResolved(e, digest)
        case e @ _                                                  => Resolved(e)
      }
      (checkDigest, finalState)
    }

}

// Import resolution may fail either in a way that may be recovered via `?`, or in a way that disallows further attempts via `?`.
sealed trait ImportResolutionResult[+E] {
  def flatMap[H](f: E => ImportResolutionResult[H]): ImportResolutionResult[H] = this match {
    case Resolved(expr)                           => f(expr)
    case failure: ImportResolutionResult[Nothing] => failure
  }

  def map[H](f: E => H): ImportResolutionResult[H] = this match {
    case Resolved(expr)                           => Resolved(f(expr))
    case failure: ImportResolutionResult[Nothing] => failure
  }
}

object ImportResolutionResult {

  def printFailures(messages: ResolutionErrors): String = messages.mkString("\n\t", "\n\t", "\n")

  type ResolutionErrors = Seq[String]

  final case class TransientFailure(messages: ResolutionErrors) extends ImportResolutionResult[Nothing]

  final case class PermanentFailure(messages: ResolutionErrors) extends ImportResolutionResult[Nothing]

  final case class Resolved[E](expr: E) extends ImportResolutionResult[E]
}

// A State monad-transformed ImportResolutionResult, used as an applicative functor to update the state during import resolution.
final case class ImportResolutionStep[+E](run: ImportContext => (ImportResolutionResult[E], ImportContext))

object ImportResolutionStep {
  implicit val ApplicativeImportResolutionStep: Applicative[ImportResolutionStep] = new Applicative[ImportResolutionStep] {
    override def zip[A, B](fa: ImportResolutionStep[A], fb: ImportResolutionStep[B]): ImportResolutionStep[(A, B)] =
      ImportResolutionStep[(A, B)] { s0 =>
        fa.run(s0) match {
          case (Resolved(a), s1)                              =>
            fb.run(s1) match {
              case (Resolved(b), s2)                              => (Resolved((a, b)), s2)
              case (failure: ImportResolutionResult[Nothing], s2) => (failure, s2)
            }
          case (failure: ImportResolutionResult[Nothing], s1) => (failure, s1)
        }
      }

    override def map[A, B](f: A => B)(fa: ImportResolutionStep[A]): ImportResolutionStep[B] =
      ImportResolutionStep[B](s =>
        fa.run(s) match {
          case (a, s) => (a.map(f), s)
        }
      )

    override def pure[A](a: A): ImportResolutionStep[A] =
      ImportResolutionStep[A](s => (Resolved(a), s))
  }

}
