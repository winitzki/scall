package io.chymyst.dhall

import co.nstant.in.cbor.builder.AbstractBuilder
import co.nstant.in.cbor.model.{Array => Cbor1Array, Map => Cbor1Map, _}
import co.nstant.in.cbor.{CborBuilder, CborDecoder, CborEncoder}
import com.upokecenter.cbor.{CBORObject, CBORType}
import com.upokecenter.numbers.EInteger
import fastparse.ParserInputSource.fromReadable
import io.bullet.borer
import io.bullet.borer.DataItem.Tag
import io.bullet.borer.Tag.Other
import io.bullet.borer.{Cbor, Decoder, Encoder, TaggedValue, Writer}
import io.chymyst.dhall.CBORmodel.CBytes.byteArrayToHexString
import io.chymyst.dhall.CBORmodel._
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, Natural, PathComponent}
import io.chymyst.dhall.SyntaxConstants._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.{CollectionHasAsScala, ListHasAsScala, MapHasAsScala}
import scala.util.{Failure, Success, Try}

sealed trait CBORmodel {

  def toCbor2: CBORObject

  def toCbor1: DataItem

  val toCbor3: Writer => Writer

  final def encodeCbor1: Array[Byte] = {
    val baos     = new ByteArrayOutputStream
    val dataItem = this.toCbor1
    // Need to use non-canonical encoding because Dhall's map key sorting is not canonical but by string order. For instance, shorter strings are not always before longer strings.
    new CborEncoder(baos).nonCanonical.encode(new CborBuilder().add(dataItem).build)
    baos.toByteArray
  }

  implicit final val encoder3: Encoder[CBORmodel] = Encoder { (writer, t) => t.toCbor3(writer) }

  final def encodeCbor3: Array[Byte] = Cbor.encode[CBORmodel](this).toByteArray

  final def encodeCbor2: Array[Byte] = this.toCbor2.EncodeToBytes()

  def dhallDiagnostics: String = this.toString

  private def removeVacuousTag(model: CBORmodel): CBORmodel = model match {
    case CArray(data)         => CArray(data.map(removeVacuousTag))
    case CMap(data)           => CMap(data.map { case (k, v) => (k, removeVacuousTag(v)) })
    case CTagged(55799, data) => removeVacuousTag(data)
    case _                    => model
  }

  final def toScheme: ExpressionScheme[Expression] = removeVacuousTag(this) match {
    case CNull         => ().die(s"Invalid top-level CBOR null value")
    case CTrue         => ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True)
    case CFalse        => ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False)
    case CInt(data)    => ExpressionScheme.Variable(underscore, data)
    case CDouble(data) => ExpressionScheme.DoubleLiteral(data)

    case CString(data) => {
      if (Grammar.builtinSymbolNamesSet contains data) ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.withName(data))
      else if (Grammar.constantSymbolNamesSet contains data) ExpressionScheme.ExprConstant(SyntaxConstants.Constant.withName(data))
      else
        ().die(
          s"String '$data' must be a Builtin or a constant name (one of ${(Grammar.builtinSymbolNamesSet ++ Grammar.constantSymbolNamesSet).toSeq.sorted.mkString(", ")})"
        )
    }

    case CArray(data) =>
      data.toList match {
        case CIntTag(0) :: head :: firstArg :: tail =>
          val firstTerm = ExpressionScheme.Application[Expression](head.toScheme, firstArg.toScheme)
          tail.map(_.toScheme).foldLeft(firstTerm)((prev, x) => ExpressionScheme.Application(prev, x))

        case CNull :: _ | CTrue :: _ | CFalse :: _ | CDouble(_) :: _                       => ().die(s"Invalid array $this - may not start with ${data.head}")
        case CIntTag(1) :: tipe :: body :: Nil                                             => ExpressionScheme.Lambda(underscore, tipe.toScheme, body.toScheme)
        case CIntTag(1) :: CString(name) :: tipe :: body :: Nil if name != underscore.name =>
          ExpressionScheme.Lambda(VarName(name), tipe.toScheme, body.toScheme)

        case CIntTag(2) :: tipe :: body :: Nil                                             => ExpressionScheme.Forall(underscore, tipe.toScheme, body.toScheme)
        case CIntTag(2) :: CString(name) :: tipe :: body :: Nil if name != underscore.name =>
          ExpressionScheme.Forall(VarName(name), tipe.toScheme, body.toScheme)

        case CIntTag(3) :: CInt(code) :: left :: right :: Nil if code.isValidByte && code >= 0 && code < 13 => // ExpressionScheme.Operator
          ExpressionScheme.ExprOperator(left.toScheme, SyntaxConstants.Operator.cborCodeDict(code.toInt), right.toScheme)

        case CIntTag(3) :: CInt(code) :: left :: right :: Nil
            if code.isValidInt && code.intValue == 13 => // ExpressionScheme.Completion - this is not an operator.
          ExpressionScheme.Completion(left.toScheme, right.toScheme)

        case CIntTag(28) :: tipe :: Nil => ExpressionScheme.EmptyList(tipe.toScheme)

        case CIntTag(4) :: tipe :: Nil if tipe != CNull =>
          ExpressionScheme.EmptyList[Expression](
            ExpressionScheme.Application[Expression](ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.List), tipe.toScheme)
          )

        case CIntTag(4) :: CNull :: exprs => ExpressionScheme.NonEmptyList(exprs.map(_.toScheme))

        case CIntTag(5) :: CNull :: body :: Nil => ExpressionScheme.KeywordSome(body.toScheme)

        case CIntTag(6) :: t :: u :: Nil      => ExpressionScheme.Merge(t.toScheme, u.toScheme, None)
        case CIntTag(6) :: t :: u :: v :: Nil => ExpressionScheme.Merge(t.toScheme, u.toScheme, Some(v.toScheme))

        case CIntTag(27) :: u :: Nil      => ExpressionScheme.ToMap(u.toScheme, None)
        case CIntTag(27) :: u :: v :: Nil => ExpressionScheme.ToMap(u.toScheme, Some(v.toScheme))

        case CIntTag(34) :: u :: Nil => ExpressionScheme.ShowConstructor(u.toScheme)

        case CIntTag(7) :: CMap(data) :: Nil => ExpressionScheme.RecordType(sortRecordFields(data)).sorted

        case CIntTag(8) :: CMap(data) :: Nil => ExpressionScheme.RecordLiteral(sortRecordFields(data)).sorted

        case CIntTag(9) :: t :: CString(name) :: Nil => ExpressionScheme.Field(t.toScheme, FieldName(name))

        case CIntTag(10) :: t :: tails if tails.nonEmpty && tails.forall(_.isInstanceOf[CString]) =>
          ExpressionScheme.ProjectByLabels(t.toScheme, tails.map(_.asInstanceOf[CString].data).map(FieldName))

        case CIntTag(10) :: t :: CArray(Array(tipe)) :: Nil => ExpressionScheme.ProjectByType(t.toScheme, tipe.toScheme)

        case CIntTag(11) :: CMap(data) :: Nil =>
          ExpressionScheme
            .UnionType[Expression](data.toSeq.map { case (name, expr) => (ConstructorName(name), if (expr == CNull) None else Some(expr.toScheme)) }).sorted

        case CIntTag(14) :: cond :: ifTrue :: ifFalse :: Nil => ExpressionScheme.If(cond.toScheme, ifTrue.toScheme, ifFalse.toScheme)

        case CIntTag(15) :: CInt(n) :: Nil if n >= 0         => ExpressionScheme.NaturalLiteral(n)
        case CIntTag(15) :: CTagged(2, CBytes(bytes)) :: Nil =>
          val bigInt = BigInt(bytes)
          if (bigInt >= 0) ExpressionScheme.NaturalLiteral(bigInt)
          else ().die(s"Invalid natural literal: value must be non-negative but is ${bigInt.toString(10)}")

        case CIntTag(16) :: CInt(n) :: Nil                     => ExpressionScheme.IntegerLiteral(n)
        case CIntTag(16) :: (t @ CTagged(_, CBytes(_))) :: Nil => ExpressionScheme.IntegerLiteral(t.toBigIntIfPossible.get)

        case CIntTag(18) :: CString(head) :: tail if tail.zipWithIndex.forall {
              case (t, i) if i          % 2 == 0 && t.toScheme != null => true
              case (CString(_), i) if i % 2 == 1                       => true
              case _ => false
            } =>
          if (tail.isEmpty)
            ExpressionScheme.TextLiteral(List(), head)
          else {
            val trailing = tail.last.asInstanceOf[CString].data
            ExpressionScheme.TextLiteral(
              data.drop(1).init.grouped(2).toList.map { array => (array(0).asInstanceOf[CString].data, array(1).toScheme) },
              trailing,
            )
          }

        case CString(name) :: CInt(index) :: Nil =>
          assert(name != "_").or(s"Invalid array $this: variables named '_' must be encoded as integers")
          ExpressionScheme.Variable(VarName(name), index)

        case CIntTag(33) :: CBytes(bytes) :: Nil => ExpressionScheme.BytesLiteral(CBytes.byteArrayToHexString(bytes))

        case CIntTag(19) :: x :: Nil => ExpressionScheme.Assert(x.toScheme)

        case CIntTag(26) :: body :: tipe :: Nil => ExpressionScheme.Annotation(body.toScheme, tipe.toScheme)

        case CIntTag(24) :: maybeHash :: CIntTag(importModeTag) :: CIntTag(schemeTag) :: tail => // ExpressionScheme.Import
          val digest                             = maybeHash match {
            case CNull                                                                                     => None
            case CBytes(bytes) if bytes.length == 34 && bytes(0) == 0x12.toByte && bytes(1) == 0x20.toByte =>
              Some(ExpressionScheme.BytesLiteral(CBytes.byteArrayToHexString(bytes.drop(2))))
          }
          val importMode                         = ImportMode.cborCodeDict(importModeTag)
          val importType: ImportType[Expression] = (schemeTag, tail) match {
            case (t, headersOrCNull :: CString(authority) :: relativeURL) if SyntaxConstants.Scheme.cborCodeDict.keySet contains t =>
              val headers  = if (headersOrCNull == CNull) None else Some(headersOrCNull.toScheme)
              val query    = if (relativeURL.last == CNull) None else Some(relativeURL.last.asString)
              val segments = relativeURL.init.map(_.asString)
              val url      = SyntaxConstants.ImportURL(
                scheme = SyntaxConstants.Scheme.cborCodeDict(t),
                authority = authority,
                path = SyntaxConstants.FilePath.of(segments),
                query = query,
              )
              ImportType.Remote[Expression](url, headers.map(Expression.apply))

            case (t, filePath) if SyntaxConstants.FilePrefix.cborCodeDict.keySet contains t =>
              val filePrefix: FilePrefix = FilePrefix.cborCodeDict(t)
              ImportType.ImportPath(filePrefix, SyntaxConstants.FilePath.of(filePath.map(_.asString)))

            case (6, List(CString(varName))) => ImportType.Env(varName)

            case (7, List()) => ImportType.Missing
          }
          ExpressionScheme.Import(importType, importMode, digest)

        case CIntTag(25) :: defs if defs.length > 3 => // ExpressionScheme.Let
          val target = defs.last
          defs.init.grouped(3).foldRight(target.toScheme) { case (List(name, tipe, expr), t) =>
            ExpressionScheme.Let(VarName(name.asString), if (tipe == CNull) None else Some(tipe.toScheme), expr.toScheme, t)
          }

        case CIntTag(29) :: base :: CArray(defs) :: target :: Nil if defs.forall {
              case CIntTag(0) | CString(_) => true
              case _                       => false
            } =>
          ExpressionScheme.With(
            base.toScheme,
            defs.map {
              case CIntTag(0)    => PathComponent.DescendOptional
              case name: CString => PathComponent.Label(FieldName(name.data))
            },
            target.toScheme,
          )

        case CIntTag(30) :: CIntTag(year) :: CIntTag(month) :: CIntTag(day) :: Nil if month >= 1 && month <= 12 && day >= 1 && day <= 31 =>
          ExpressionScheme.DateLiteral(year, month, day)

        case CIntTag(31) :: CIntTag(hours) :: CIntTag(minutes) :: CTagged(4, CArray(Array(CIntTag(precision), totalSecondsObj))) :: Nil
            if hours >= 0 && hours <= 23 && minutes >= 0 && minutes < 60 && precision <= 0 && decodeTotalSeconds(totalSecondsObj).nonEmpty =>
          val totalSeconds = decodeTotalSeconds(totalSecondsObj).get
          ExpressionScheme.TimeLiteral.of(hours, minutes, totalSeconds, precision).or(s"Invalid TimeLiteral($hours, $minutes, $totalSeconds, $precision)")

        case CIntTag(32) :: (CTrue | CFalse) :: CIntTag(hours) :: CIntTag(minutes) :: Nil if hours >= 0 && hours <= 23 && minutes >= 0 && minutes < 60 =>
          val sign = data(1) match {
            case CTrue  => 1
            case CFalse => -1
          }
          ExpressionScheme.TimeZoneLiteral(sign * (hours * 60 + minutes))

        case _ => ().die(s"Invalid top-level array $this while parsing CBOR")
      }

    case CTagged(tag, data)  => ().die(s"Unexpected tagged top-level CBOR object: tag $tag with data $data")
    case CBytes(_) | CMap(_) => ().die(s"Unexpected top-level CBOR object: $this")
  }

  private def asString: String = (this.asInstanceOf[CString].data).or(s"This CBORmodel is $this and not a CString")
}

// In the library "cbor1" there is no constructor for double values with automatic downgrading of precision. This code provides that function.
private object CBOR1fix extends AbstractBuilder[CborBuilder](new CborBuilder()) {
  // We need to inherit from AbstractBuilder only to use the "convert" functions, which are `protected`.
  def createDataItemForDoubleAtMinimumPrecision(data: Double): DataItem =
    if (data.isFinite)
      if (data.toFloat.toDouble == data) convert(data.toFloat) else convert(data)
    else new HalfPrecisionFloat(data.toFloat)
}

object CBORmodel {
  implicit class OrError[A](expr: => A) {
    def die(message: String, t: Throwable = null): Nothing = throw new Exception(message, t)

    def or(message: String): A = Try(expr) match {
      case Failure(t)     => die(message, t)
      case Success(value) => value
    }
  }

  def decodeTotalSeconds(totalSecondsObj: CBORmodel): Option[Natural] = {
    totalSecondsObj match {
      case CInt(data) if data >= 0  => Some(data)
      case CTagged(2, CBytes(data)) =>
        val totalSeconds = BigInt(Array[Byte](0) ++ data)
        if (totalSeconds >= 0) Some(totalSeconds) else None
      case _                        => None
    }
  }

  val decodeCBORModel: Decoder[CBORmodel] = Decoder { reader =>
    if (reader.hasSimpleValue(SimpleValueType.NULL.getValue)) {
      CNull
    } else if (reader.hasSimpleValue(SimpleValueType.FALSE.getValue)) {
      CFalse
    } else if (reader.hasSimpleValue(SimpleValueType.TRUE.getValue)) {
      CTrue
    } else ???

  }

  def decodeCbor3(bytes: Array[Byte]): CBORmodel = Cbor.decode(bytes).to[CBORmodel](decodeCBORModel).value

  def decodeCbor2(bytes: Array[Byte]): CBORmodel = fromCbor2(CBORObject.DecodeFromBytes(bytes))

  def decodeCbor1(bytes: Array[Byte]): CBORmodel = {
    val bais = new ByteArrayInputStream(bytes)
    new CborDecoder(bais).decode.asScala.toList match {
      case head :: Nil  => fromCbor1(head)
      case head :: tail => ().die(s"Invalid sequence of CBOR objects, $tail, after the first CBOR1 object, $head")
      case Nil          => ().die(s"Invalid null byte stream for decoding CBOR1")
    }
  }

  def fromCbor1(dataItem: DataItem): CBORmodel = {
    val model = dataItem.getMajorType match {
      case MajorType.UNSIGNED_INTEGER => CInt(dataItem.asInstanceOf[UnsignedInteger].getValue)
      case MajorType.INVALID          => ???
      case MajorType.NEGATIVE_INTEGER => CInt(dataItem.asInstanceOf[NegativeInteger].getValue)
      case MajorType.BYTE_STRING      => CBytes(dataItem.asInstanceOf[ByteString].getBytes)
      case MajorType.UNICODE_STRING   => CString(dataItem.asInstanceOf[UnicodeString].getString)
      case MajorType.ARRAY            => CArray(dataItem.asInstanceOf[Cbor1Array].getDataItems.asScala.toArray.map(fromCbor1))
      case MajorType.MAP              =>
        CMap(
          dataItem
            .asInstanceOf[Cbor1Map].getKeys.asScala
            .zip(dataItem.asInstanceOf[Cbor1Map].getValues.asScala)
            .map { case (k, v) => (fromCbor1(k).asInstanceOf[CString].data, fromCbor1(v)) }
            .toMap
        )
      case MajorType.TAG              => ???
      case MajorType.SPECIAL          =>
        dataItem match {
          case number: Number   =>
            number match {
              case integer: NegativeInteger => CInt(integer.getValue)
              case integer: UnsignedInteger => CInt(integer.getValue)
              case _                        => ???
            }
          case special: Special =>
            special match {
              case float: DoublePrecisionFloat => CDouble(float.getValue)
              case simpleValue: SimpleValue    =>
                simpleValue.getSimpleValueType match {
                  case SimpleValueType.FALSE => CFalse
                  case SimpleValueType.TRUE  => CTrue
                  case SimpleValueType.NULL  => CNull
                  case _                     => throw new Exception(s"Unsupported CBOR1 simple value: $simpleValue")
                }
              case float: SinglePrecisionFloat => CDouble(float.getValue)
              case float: HalfPrecisionFloat   => CDouble(float.getValue)
              case _                           => ???
            }
          case tag: Tag         => ???
          case _                => ???
        }
    }
    if (dataItem.hasTag) CTagged(dataItem.getTag.getValue.toInt, model) else model
  }

  def fromCbor2(obj: CBORObject): CBORmodel = if (obj == null) CNull
  else {
    val decoded: CBORmodel = obj.getType match {
//      case CBORType.Number => throw new Exception(s"Unexpected CBOR type Number in object $obj")

      case CBORType.Boolean =>
        obj.getSimpleValue match {
          case 20 => CFalse
          case 21 => CTrue
          case 22 => CNull
          case x  => throw new Exception(s"boolean has unexpected simple value $x")
        }

      case CBORType.SimpleValue =>
        obj.getSimpleValue match {
          case 20 => CFalse
          case 21 => CTrue
          case 22 => CNull
          case x  => throw new Exception(s"got CBOR simple value $x")
        }

      case CBORType.ByteString    => CBytes(obj.GetByteString)
      case CBORType.TextString    => CString(obj.AsString)
      case CBORType.Array         =>
        val objs: Array[CBORObject] = obj.ToObject(classOf[Array[CBORObject]])
        CArray(objs.map(fromCbor2))
      case CBORType.Map           =>
        val objs: java.util.Map[CBORObject, CBORObject] = obj.ToObject(classOf[java.util.Map[CBORObject, CBORObject]])
        CMap(objs.asScala.map { case (k, v) => (fromCbor2(k).asString, fromCbor2(v)) }.toMap)
      case CBORType.Integer       =>
        if (obj.CanValueFitInInt64()) CInt(BigInt(obj.AsInt64Value)) else CInt(eIntegerToBigInt(obj.AsEIntegerValue))
      case CBORType.FloatingPoint => CDouble(obj.AsDoubleValue)
    }

    if (obj.isTagged) {
      val tags: Array[Natural] = obj.GetAllTags.map(eIntegerToBigInt)
      if (obj.HasOneTag)
        CTagged(tags(0).toInt, decoded)
      else throw new Exception(s"CBOR object $decoded has more than one tag, this is unsupported by Dhall")
    } else decoded
  }

  def eIntegerToBigInt(eInt: EInteger): BigInt = BigInt(eInt.signum, eInt.ToBytes(false))

  private def sortRecordFields(data: Map[String, CBORmodel]): Seq[(FieldName, Expression)] =
    data.toSeq.map { case (name, expr) => (FieldName(name), expr.toScheme) }

  case object CNull extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.Null

    override def toString: String = "null"

    override def toCbor1: DataItem = SimpleValue.NULL

    override val toCbor3: Writer => Writer = _.writeSimpleValue(SimpleValueType.NULL.getValue)
  }

  case object CTrue extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.True

    override def toString: String = "true"

    override def toCbor1: DataItem = SimpleValue.TRUE

    override val toCbor3: Writer => Writer = _.writeSimpleValue(SimpleValueType.TRUE.getValue)
  }

  case object CFalse extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.False

    override def toString: String = "false"

    override def toCbor1: DataItem = SimpleValue.FALSE

    override val toCbor3: Writer => Writer = _.writeSimpleValue(SimpleValueType.FALSE.getValue)
  }

  // Pattern-match CInt with an integer value. (Note: BigInt does not have `unapply`.)
  object CIntTag {
    // Cheat sheet: `x match { case CIntTag(1) => ... }` will match successfully when CIntTag.unapply(x) == Some(1).
    def unapply(x: CInt): Option[Int] = Some(x.data.intValue).filter(_ => x.data.isValidInt)
  }

  // Either a 64-bit int or a bigint.
  final case class CInt(data: BigInt) extends CBORmodel {
    override def toCbor2: CBORObject = CBOR.naturalToCbor2(data)

    override def toString: String = data.toString

    override def toCbor1: DataItem = if (data < 0) new NegativeInteger(data.bigInteger) else new UnsignedInteger(data.bigInteger)

    override val toCbor3: Writer => Writer = { writer =>
      writer.write(data)
    }
  }

  final case class CDouble(data: Double) extends CBORmodel {
    override def toCbor2: CBORObject = {
      val result = data match {
        // Important: match -0.0 before 0.0 or else it cannot match.
        case -0.0                    => CBORObject.FromObject(java.lang.Double.valueOf(data)) // CBORObject.FromFloatingPointBits(0x8000L, 2)
        case 0.0                     => CBORObject.FromFloatingPointBits(0L, 2)
        case Double.NaN              => CBORObject.FromFloatingPointBits(0x7e00L, 2)
        case Double.NegativeInfinity => CBORObject.FromFloatingPointBits(0xfc00L, 2)
        case Double.PositiveInfinity => CBORObject.FromFloatingPointBits(0x7c00L, 2)
        case _                       => CBORObject.FromObject(java.lang.Double.valueOf(data))
      }
      result
    }

    override def toString: String = f"$data%.1f"

    override def toCbor1: DataItem = CBOR1fix.createDataItemForDoubleAtMinimumPrecision(data)

    override val toCbor3: Writer => Writer = _.writeDouble(data)
  }

  final case class CString(data: String) extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.FromObject(data)

    override def toString: String = "\"" + escaped + "\"" // This can be written as s"\"$escaped\"" in Scala 2.13+.

    def escaped: String = data.flatMap {
      case '\b'                               => "\\b"
      case '\n'                               => "\\n"
      case '\f'                               => "\\f"
      case '\r'                               => "\\r"
      case '\t'                               => "\\t"
      case '"'                                => "\\\""
      case '\\'                               => "\\\\"
      case c if c.toInt > 255 || c.toInt < 20 => s"\\u${c.toInt.toHexString.toUpperCase}"
      case c                                  => c.toString
    }

    override def toCbor1: DataItem = new UnicodeString(data)

    override val toCbor3: Writer => Writer = _.writeString(data)
  }

  final case class CArray(data: Array[CBORmodel]) extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.FromObject(data.map(_.toCbor2))

    override def toString: String = "[" + data.map(_.toString).mkString(", ") + "]"

    override def dhallDiagnostics: String = "[" + data.map(_.dhallDiagnostics).mkString(", ") + "]"

    override def equals(obj: Any): Boolean = obj.isInstanceOf[CArray] && (obj.asInstanceOf[CArray].data.zip(data).forall { case (x, y) => x equals y })

    override def toCbor1: DataItem = data.foldLeft(new Cbor1Array(data.length))((prev, m) => prev.add(m.toCbor1))

    override val toCbor3: Writer => Writer = { writer =>
      writer.writeArrayOpen(data.size)
      data.foreach { d => writer.write(d)(d.encoder3) } // TODO make sure this works
      writer.writeArrayClose()
    }
  }

  object CBytes {
    def byteArrayToHexString(data: Array[Byte]): String = data.map(b => String.format("%02X", Byte.box(b))).mkString("")
  }

  final case class CBytes(data: Array[Byte]) extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.FromObject(data)

    override def toString: String = "h'" + byteArrayToHexString(data) + "'"

    override def equals(obj: Any): Boolean = obj.isInstanceOf[CBytes] && (obj.asInstanceOf[CBytes].data sameElements data)

    override def toCbor1: DataItem = new ByteString(data)

    override val toCbor3: Writer => Writer = _.write(data)
  }

  final case class CMap(data: Map[String, CBORmodel]) extends CBORmodel {
    override def toCbor2: CBORObject = {
      val dict = CBORObject.NewOrderedMap
      data.toSeq.sortBy(_._1).foreach { case (k, v) => dict.Add(k, v.toCbor2) } // Dhall requires sorting by the dictionary's keys.
      CBORObject.FromObject(dict)
    }

    override def toString: String = "{" + data.toSeq.sortBy(_._1).map { case (k, v) => "\"" + k + "\": " + v }.mkString(", ") + "}"

    override lazy val dhallDiagnostics: String =
      "{" + data.toSeq.sortBy(_._1).map { case (k, v) => "\"" + k + "\": " + v.dhallDiagnostics }.mkString(", ") + "}"

    override def equals(obj: Any): Boolean = obj.isInstanceOf[CMap] && (obj.asInstanceOf[CMap].data.zip(data).forall { case (x, y) => x equals y })

    override def toCbor1: DataItem =
      data.toSeq.sortBy(_._1).foldLeft(new Cbor1Map(data.size)) { case (prev, (k, v)) => prev.put(new UnicodeString(k), v.toCbor1) }

    override val toCbor3: Writer => Writer = { writer =>
      writer.writeMapOpen(data.size)
      data.foreach { case (k, v) => writer.writeMapMember(k, v) }
      writer.writeMapClose()
    }
  }

  final case class CTagged(tag: Int, data: CBORmodel) extends CBORmodel {
    override def toCbor2: CBORObject = CBORObject.FromObjectAndTag(data.toCbor2, tag)

    override def toString: String = s"$tag($data)"

    override def dhallDiagnostics: String = toBigIntIfPossible match {
      case Success(value) => CInt(value).dhallDiagnostics
      case _              => s"$tag(${data.dhallDiagnostics})"
    }

    def toBigIntIfPossible: Try[
      BigInt
    ] = // Note: data such as 2(h'0x123123123'), i.e., Tagged(2, CBytes(...)), must be decoded into a big integer. But we want to keep the CBORmodel unmodified.
      (tag, data) match {
        case (2, CBytes(bytes)) => Success(BigInt(1, bytes))
        case (3, CBytes(bytes)) => Success(BigInt(-1, bytes) - 1)
        case _                  => Failure(new Exception(s"Invalid integer literal $this: tag $tag must be 2 or 3, data is $data must be CBytes"))
      }

    override def toCbor1: DataItem = {
      val cbor1 = data.toCbor1
      cbor1.setTag(tag.toLong)
      cbor1
    }

    override val toCbor3: Writer => Writer = { writer =>
      writer.write(TaggedValue(Other(tag), data))
    }
  }

  def toCBORmodel: Any => CBORmodel = {
    case null                            => CNull
    case true                            => CTrue
    case false                           => CFalse
    case x: Int                          => CInt(BigInt(x))
    case x: Long                         => CInt(BigInt(x))
    case x: BigInt                       => CInt(x)
    case x: String                       => CString(x)
    case x: Array[Any]                   => CArray(x.map(toCBORmodel))
    case x: Map[String, Any]             => CMap(x.map { case (k, v) => (k, toCBORmodel(v)) })
    case x: ExpressionScheme[Expression] => CBOR.toCborModel(x)
    case x: Expression                   => CBOR.toCborModel(x)
    case x: CBORmodel                    => x
    case x                               => throw new Exception(s"Invalid input toCBORmodel($x:${x.getClass})")
  }

  def array(objs: Any*): CArray = CArray(Array(objs: _*).map(toCBORmodel))

}

// See https://github.com/dhall-lang/dhall-lang/blob/master/standard/binary.md
object CBOR {

  def java8ReadInputStreamToByteArray(input: InputStream): Array[Byte] = {
    val bufLen       = 1024
    val buf          = new Array[Byte](bufLen)
    var readLen      = 0
    val outputStream = new ByteArrayOutputStream

    while ({ readLen = input.read(buf, 0, bufLen); readLen } != -1) outputStream.write(buf, 0, readLen)

    outputStream.toByteArray
  }

  val maxCborNumberAsCInt: BigInt = BigInt(1L) << 64

  def naturalToCbor2(index: Natural): CBORObject =
    if (index < maxCborNumberAsCInt)
      CBORObject.FromObject(index.bigInteger)
    else
      CBORObject.FromObject(EInteger.FromBytes(index.toByteArray, false)) // TODO: Does this work correctly? Do we need to set littleEndian = true?

  def toCborModel(e: Expression): CBORmodel = e.scheme match {
    case Variable(name, index) => if (name == underscore) CInt(index) else array(name.name, index)

    case ExpressionScheme.Lambda(name, tipe, body) => if (name == underscore) array(1, tipe, body) else array(1, name.name, tipe, body)

    case ExpressionScheme.Forall(name, tipe, body) => if (name == underscore) array(2, tipe, body) else array(2, name.name, tipe, body)

    case e @ ExpressionScheme.Let(_, _, _, _) =>
      @tailrec def loop(acc: Seq[Any], expr: ExpressionScheme[Expression]): Seq[Any] = expr match {
        case ExpressionScheme.Let(name, tipe, subst, body) => loop((acc :+ name.name) :+ (tipe.orNull: Any) :+ subst, body)
        case _                                             => acc :+ expr
      }

      array(25 +: loop(Seq(), e): _*)

    case ExpressionScheme.If(cond, ifTrue, ifFalse) => array(14, cond, ifTrue, ifFalse)

    case ExpressionScheme.Merge(record, update, tipe) =>
      val args = Seq(record, update) ++ tipe.toSeq
      array(6 +: args: _*)

    case ExpressionScheme.ToMap(data, tipe) =>
      val args = Seq(data) ++ tipe.toSeq
      array(27 +: args: _*)

    case ExpressionScheme.EmptyList(Expression(ExpressionScheme.Application(Expression(ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.List)), tipe))) =>
      array(4, tipe)

    case ExpressionScheme.EmptyList(tipe) => array(28, tipe)

    case ExpressionScheme.NonEmptyList(exprs) => array(4 +: null +: exprs: _*)

    case ExpressionScheme.Annotation(data, tipe) => array(26, data, tipe)

    case ExpressionScheme.ExprOperator(lop, op, rop) => array(3, op.cborCode, lop, rop)

    case f @ ExpressionScheme.Application(_, _) =>
      @tailrec def loop(args: Seq[Expression], expr: Expression): Seq[Expression] = expr.scheme match {
        case ExpressionScheme.Application(f, a) => loop(a +: args, f)
        case _                                  => expr +: args
      }

      array(0 +: loop(Seq(), f): _*)

    case ExpressionScheme.Field(base, name) => array(9, base, name.name)

    case ExpressionScheme.ProjectByLabels(base, labels) => array(10 +: base +: labels.map(_.name): _*)

    case ExpressionScheme.ProjectByType(base, by) => array(10, base, array(by))

    case ExpressionScheme.Completion(base, target) => array(3, 13, base, target)

    case ExpressionScheme.Assert(data) => array(19, data)

    case ExpressionScheme.With(data, pathComponents, body) =>
      val path: Seq[Any] = pathComponents.map {
        case PathComponent.Label(name)     => name.name
        case PathComponent.DescendOptional => 0
      }
      array(29, data, array(path: _*), body)

    case ExpressionScheme.DoubleLiteral(value) => CDouble(value)

    case ExpressionScheme.NaturalLiteral(value) => array(15, value)

    case ExpressionScheme.IntegerLiteral(value) => array(16, value)

    case ExpressionScheme.TextLiteral(interpolations, trailing) =>
      val objects: Seq[Any] = interpolations.flatMap { case (head, tail) => Seq(head, tail) } :+ trailing
      array(18 +: objects: _*)

    case b @ ExpressionScheme.BytesLiteral(_) => array(33, CBytes(b.bytes))

    case ExpressionScheme.DateLiteral(y, m, d) => array(30, y, m, d)

    case t @ ExpressionScheme.TimeLiteral(hours, minutes, _, _) =>
      // TODO report issue: need to add a test to Dhall standard tests in order to validate the CBOR encoding with long nanos as CTagged(2,CByte(...)) instead of CInt(...)
      val cborTotalSeconds: Any =
        if (t.cborTotalSeconds < maxCborNumberAsCInt) CInt(t.cborTotalSeconds)
        else {
          val bytes                   = t.cborTotalSeconds.toByteArray
          val bytesWithoutLeadingZero = if (bytes(0) == 0) bytes.drop(1) else bytes
          CTagged(2, CBytes(bytesWithoutLeadingZero))
        }
      array(31, hours, minutes, CTagged(4, array(t.cborPrecision, cborTotalSeconds)))

    case t @ ExpressionScheme.TimeZoneLiteral(_) =>
      val cborSign: CBORmodel = if (t.isPositive) CTrue else CFalse
      array(32, cborSign, t.hours, t.minutes)

    case ExpressionScheme.RecordType(defs) =>
      val dict = defs.map { case (fieldName, expr) => (fieldName.name, expr) }.toMap
      array(7, dict)

    case ExpressionScheme.RecordLiteral(defs) =>
      val dict = defs.map { case (fieldName, expr) => (fieldName.name, expr) }.toMap
      array(8, dict)

    case ExpressionScheme.UnionType(defs) =>
      val dict = defs.map { case (constructorName, maybeExpr) => (constructorName.name, maybeExpr.orNull: Any) }.toMap
      array(11, dict)

    case ExpressionScheme.ShowConstructor(data) => array(34, data)

    case ExpressionScheme.Import(importType, importMode, digest) =>
      val integrity = digest.map(d => CBytes("\u0012\u0020".getBytes ++ d.bytes)).orNull
      val part1     = Seq(integrity, importMode.cborCode)
      val part2     = importType match {
        case ImportType.Missing => Seq(7)

        case ImportType.Remote(SyntaxConstants.ImportURL(scheme, authority, SyntaxConstants.FilePath(segments), query), headers) =>
          scheme.cborCode +: (headers.orNull: Any) +: authority +: segments :+ (query.orNull: Any)

        case ImportType.ImportPath(filePrefix, SyntaxConstants.FilePath(segments)) => filePrefix.cborCode +: segments

        case ImportType.Env(envVarName) => Seq(6, envVarName)
      }
      array(24 +: (part1 ++ part2): _*)

    case ExpressionScheme.KeywordSome(data) => array(5, null, data)

    case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) => CTrue

    case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) => CFalse

    case ExpressionScheme.ExprBuiltin(builtin) => CString(builtin.entryName)

    case ExpressionScheme.ExprConstant(constant) => CString(constant.entryName)
  }

}
