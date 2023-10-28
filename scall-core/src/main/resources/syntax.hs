-- | Top-level type representing a Dhall expression
data Expression
    = Variable Text Natural
      -- ^ > x@n
    | Lambda Text Expression Expression
      -- ^ > λ(x : A) → b
    | Forall Text Expression Expression
      -- ^ > ∀(x : A) → B
    | Let Text (Maybe Expression) Expression Expression
      -- ^ > let x : A = a in b
      --   > let x     = a in b
    | If Expression Expression Expression
      -- ^ > if t then l else r
    | Merge Expression Expression (Maybe Expression)
      -- ^ > merge t u : T
      -- ^ > merge t u
    | ToMap Expression (Maybe Expression)
      -- ^ > toMap t : T
      -- ^ > toMap t
    | EmptyList Expression
      -- ^ > [] : T
    | NonEmptyList (NonEmpty Expression)
      -- ^ > [ t, ts… ]
    | Annotation Expression Expression
      -- ^ > t : T
    | Operator Expression Operator Expression
      -- ^ > l □ r
    | Application Expression Expression
      -- ^ > f a
    | Field Expression Text
      -- ^ > t.x
    | ProjectByLabels Expression [Text]
      -- ^ > t.{ xs… }
    | ProjectByType Expression Expression
      -- ^ > t.(s)
    | Completion Expression Expression
      -- ^ > T::r
    | Assert Expression
      -- ^ > assert : T
    | With Expression (NonEmpty PathComponent) Expression
      -- ^ > e with k.ks… = v
    | DoubleLiteral Double
      -- ^ > n.n
    | NaturalLiteral Natural
      -- ^ > n
    | IntegerLiteral Integer
      -- ^ > ±n
    | TextLiteral TextLiteral
      -- ^ > "s"
      --   > "s${t}ss…"
    | BytesLiteral ByteString
      -- ^ > 0x"abcdef0123456789"
    | DateLiteral Time.Day
    | TimeLiteral
        Time.TimeOfDay
        Int
        -- ^ Precision
    | TimeZoneLiteral Time.TimeZone
    | RecordType [(Text, Expression)]
      -- ^ > {}
      --   > { k : T, ks… }
    | RecordLiteral [(Text, Expression)]
      -- ^ > {=}
      --   > { k = t, ks… }
    | UnionType [(Text, Maybe Expression)]
      -- ^ > <>
      --   > < k : T | ks… >
      --   > < k | ks… >
    | ShowConstructor Expression
      -- ^ > showConstructor t
    | Import ImportType ImportMode (Maybe (Digest SHA256))
    | Some Expression
      -- ^ > Some s
    | Builtin Builtin
    | Constant Constant
    deriving (Show)

-- | Associative binary operators
data Operator
    = Or                  -- ^ > ||
    | Plus                -- ^ > +
    | TextAppend          -- ^ > ++
    | ListAppend          -- ^ > #
    | And                 -- ^ > &&
    | CombineRecordTerms  -- ^ > ∧
    | Prefer              -- ^ > ⫽
    | CombineRecordTypes  -- ^ > ⩓
    | Times               -- ^ > *
    | Equal               -- ^ > ==
    | NotEqual            -- ^ > !=
    | Equivalent          -- ^ > ===
    | Alternative         -- ^ > ?
    deriving (Show)

{-| Data structure used to represent an interpolated @Text@ literal

    A @Text@ literal without any interpolations has an empty list.  For example,
    the @Text@ literal @\"foo\"@ is represented as:

    > TextLiteral [] "foo"

    A @Text@ literal with interpolations has one list element per interpolation.
    For example, the @Text@ literal @\"foo${x}bar${y}baz\"@ is represented as:

    > TextLiteral [("foo", Variable "x" 0), ("bar", Variable "y" 0)] "baz"
-}
data TextLiteral = Chunks [(Text, Expression)] Text
    deriving (Show)

-- | This instance comes in handy for implementing @Text@-related operations
instance Semigroup TextLiteral where
    Chunks xys₀ z₀ <> Chunks [] z₁ =
        Chunks xys₀ (z₀ <> z₁)
    Chunks xys₀ z₀ <> Chunks ((x₁, y₁) : xys₁) z₁ =
        Chunks (xys₀ <> ((z₀ <> x₁, y₁) : xys₁)) z₁

-- | This instance comes in handy for implementing @Text@-related operations
instance Monoid TextLiteral where
    mempty = Chunks [] ""

-- | Builtin values
data Builtin
    = DateShow
    | DoubleShow
    | IntegerClamp
    | IntegerNegate
    | IntegerShow
    | IntegerToDouble
    | ListBuild
    | ListFold
    | ListHead
    | ListIndexed
    | ListLast
    | ListLength
    | ListReverse
    | NaturalBuild
    | NaturalEven
    | NaturalFold
    | NaturalIsZero
    | NaturalOdd
    | NaturalShow
    | NaturalSubtract
    | NaturalToInteger
    | TextReplace
    | TextShow
    | TimeShow
    | TimeZoneShow
    | Bool
    | Bytes
    | Date
    | Double
    | False
    | Integer
    | List
    | Natural
    | None
    | Optional
    | Text
    | Time
    | TimeZone
    | True
    deriving (Show)

-- | Type-checking constants
data Constant
    = Type
    | Kind
    | Sort
    deriving (Eq, Ord, Show)

-- | How to interpret the path to the import
data ImportMode
    = Code      -- ^ The default behavior: import the path as code to interpret
    | RawBytes  -- ^ @as Bytes@: import the path as raw bytes
    | RawText   -- ^ @as Text@: import the path as raw text
    | Location  -- ^ @as Location@: don't import and instead represent the path
                --   as a Dhall expression
    deriving (Show)

-- | Where to locate the import
data ImportType
    = Missing
        -- ^ > missing
    | Remote URL (Maybe Expression)
        -- ^ > https://authority directory file using headers
    | Path FilePrefix File
        -- ^ > /directory/file
        --   > ./directory/file
        --   > ../directory/file
        --   > ~/directory/file
    | Env Text
        -- ^ > env:x
    deriving (Show)

-- | Structured representation of an HTTP(S) URL
data URL = URL
    { scheme    :: Scheme
    , authority :: Text
    , path      :: File
    , query     :: Maybe Text
    }
    deriving (Show)

-- | The URL scheme
data Scheme
    = HTTP  -- ^ > http:\/\/
    | HTTPS -- ^ > https:\/\/
    deriving (Show)

-- | The anchor for a local filepath
data FilePrefix
    = Absolute  -- ^ @/@, an absolute path
    | Here      -- ^ @.@, a path relative to the current working directory
    | Parent    -- ^ @..@, a path relative to the parent working directory
    | Home      -- ^ @~@, a path relative to the user's home directory
    deriving (Show)

{-| Structured representation of a file path

    Note that the directory path components are stored in reverse order,
    meaning that the path @/foo\/bar\/baz@ is represented as:

    > File{ directory = [ "bar", "foo" ], file = "baz" }
-}
data File = File
    { directory :: [Text]  -- ^ Directory path components (in reverse order)
    , file :: Text         -- ^ File name
    }
    deriving (Show)

data PathComponent
    = Label Text
    | DescendOptional
    deriving (Show)
