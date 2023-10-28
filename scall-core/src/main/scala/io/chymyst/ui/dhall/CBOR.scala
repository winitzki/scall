package io.chymyst.ui.dhall


import com.upokecenter.cbor.{CBORObject, CBORType}
import com.upokecenter.numbers.EInteger
import io.chymyst.ui.dhall.CBORmodel.CBytes.byteArrayToHexString
import io.chymyst.ui.dhall.CBORmodel._
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme, Natural, PathComponent}
import io.chymyst.ui.dhall.SyntaxConstants._

import java.time.LocalTime
import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.{Failure, Success, Try}

object CBORfix {
  //  def write(obj: CBORObject, writer: CBORWriter, )???
  /*
  public CborWriter WriteUsingCborWriter(CBORObject obj, CborWriter writer, int depth) {

    foreach(var tag in obj.GetAllTags()){
      writer.WriteTag((CborTag)(ulong)tag);
    }
    obj=obj.Untag();
    switch(obj.Type) {
       case CBORType.Integer:
          if(obj.CanValueFitInInt64())
             writer.WriteInt64(obj.AsInt64Value());
          else {
             var ei=obj.AsEIntegerValue();
             if(ei<0) {
               ei=ei.Abs()-1;
               writer.WriteCborNegativeIntegerRepresentation((ulong)ei);
             } else {
               writer.WriteUInt64((ulong)ei);
             }
          }
          break;
       case CBORType.FloatingPoint:
          writer.WriteDouble(obj.AsDouble());
          break;
       case CBORType.Boolean:
          if(obj.IsTrue)writer.WriteSimpleValue(CborSimpleValue.True);
          else writer.WriteSimpleValue(CborSimpleValue.False);
          break;
       case CBORType.SimpleValue:
          writer.WriteSimpleValue((CborSimpleValue)obj.SimpleValue);
          break;
       case CBORType.ByteString:
          writer.WriteByteString(obj.GetByteString());
          break;
       case CBORType.TextString:
          writer.WriteTextString(obj.AsString());
          break;
       case CBORType.Array:
          writer.WriteStartArray(obj.Count);
          foreach(var o in obj.Values){
             WriteUsingCborWriter(o, writer, depth+1);
          }
          writer.WriteEndArray();
          break;
       case CBORType.Map:
          writer.WriteStartMap(obj.Count);
          foreach(var o in obj.Keys){
             WriteUsingCborWriter(o, writer, depth+1);
             WriteUsingCborWriter(obj[o], writer, depth+1);
          }
          writer.WriteEndMap();
          break;
    }
    return writer;
  }


   */
}

sealed trait CBORmodel {

  def toCBOR: CBORObject

  private implicit class OrError[A](expr: => A) {
    def die(message: String, t: Throwable = null): Nothing = throw new Exception(message, t)

    def or(message: String): A = Try(expr) match {
      case Failure(t) => die(message, t)
      case Success(value) => value
    }
  }

  def removeVacuousTag(model: CBORmodel): CBORmodel = model match {
    case CArray(data) => CArray(data.map(removeVacuousTag))
    case CMap(data) => CMap(data.map { case (k, v) => (k, removeVacuousTag(v)) })
    case CTagged(55799, data) => removeVacuousTag(data)
    case _ => model
  }

  final def toScheme: ExpressionScheme[Expression] = removeVacuousTag(this) match {
    case CNull => ().die(s"Invalid top-level CBOR null value")
    case CTrue => ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.True)
    case CFalse => ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.False)
    case CInt(data) => ExpressionScheme.Variable(underscore, data)
    case CDouble(data) => ExpressionScheme.DoubleLiteral(data)

    case CString(data) => ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.withName(data)).or(s"String '$data' must be a Builtin name (one of ${SyntaxConstants.Builtin.values.mkString(", ")})")

    case CArray(data) =>
      data.toList match {
        case CIntTag(0) :: head :: firstArg :: tail =>
          val firstTerm = ExpressionScheme.Application[Expression](head.toScheme, firstArg.toScheme)
          tail.map(_.toScheme).foldLeft(firstTerm)((prev, x) => ExpressionScheme.Application(prev, x))

        case CNull :: _ | CTrue :: _ | CFalse :: _ | CDouble(_) :: _ => ().die(s"Invalid array $this - may not start with ${data.head}")
        case CIntTag(1) :: tipe :: body :: Nil => ExpressionScheme.Lambda(underscore, tipe.toScheme, body.toScheme)
        case CIntTag(1) :: CString(name) :: tipe :: body :: Nil if name != underscore.name => ExpressionScheme.Lambda(VarName(name), tipe.toScheme, body.toScheme)

        case CIntTag(2) :: tipe :: body :: Nil => ExpressionScheme.Forall(underscore, tipe.toScheme, body.toScheme)
        case CIntTag(2) :: CString(name) :: tipe :: body :: Nil if name != underscore.name => ExpressionScheme.Forall(VarName(name), tipe.toScheme, body.toScheme)

        case CIntTag(3) :: CInt(code) :: left :: right :: Nil if code.isValidByte && code >= 0 && code < 13 => // ExpressionScheme.Operator
          ExpressionScheme.ExprOperator(left.toScheme, SyntaxConstants.Operator.cborCodeDict(code.toInt), right.toScheme)

        case CIntTag(3) :: CInt(code) :: left :: right :: Nil if code.isValidInt && code.intValue == 13 => // ExpressionScheme.Operator
          ExpressionScheme.Completion(left.toScheme, right.toScheme)

        case CIntTag(28) :: tipe :: Nil => ExpressionScheme.EmptyList(tipe.toScheme)

        case CIntTag(4) :: tipe :: Nil if tipe != CNull => ExpressionScheme.EmptyList[Expression](ExpressionScheme.Application[Expression](ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.List), tipe.toScheme))

        case CIntTag(4) :: CNull :: exprs => ExpressionScheme.NonEmptyList(exprs.map(_.toScheme))

        case CIntTag(5) :: CNull :: body :: Nil => ExpressionScheme.KeywordSome(body.toScheme)

        case CIntTag(6) :: t :: u :: Nil => ExpressionScheme.Merge(t.toScheme, u.toScheme, None)
        case CIntTag(6) :: t :: u :: v :: Nil => ExpressionScheme.Merge(t.toScheme, u.toScheme, Some(v.toScheme))

        case CIntTag(27) :: u :: Nil => ExpressionScheme.ToMap(u.toScheme, None)
        case CIntTag(27) :: u :: v :: Nil => ExpressionScheme.ToMap(u.toScheme, Some(v.toScheme))

        case CIntTag(34) :: u :: Nil => ExpressionScheme.ShowConstructor(u.toScheme)

        case CIntTag(7) :: CMap(data) :: Nil => ExpressionScheme.RecordType(sortRecordFields(data)).sorted

        case CIntTag(8) :: CMap(data) :: Nil => ExpressionScheme.RecordLiteral(sortRecordFields(data)).sorted

        case CIntTag(9) :: t :: CString(name) :: Nil => ExpressionScheme.Field(t.toScheme, FieldName(name))

        case CIntTag(10) :: t :: tails if tails.nonEmpty && tails.forall(_.isInstanceOf[CString]) =>
          ExpressionScheme.ProjectByLabels(t.toScheme, tails.map(_.asInstanceOf[CString].data).map(FieldName))

        case CIntTag(10) :: t :: CArray(Array(tipe)) :: Nil => ExpressionScheme.ProjectByType(t.toScheme, tipe.toScheme)

        case CIntTag(11) :: CMap(data) :: Nil => ExpressionScheme.UnionType[Expression](data.toSeq.map { case (name, expr) => (ConstructorName(name), if (expr == CNull) None else Some(expr.toScheme)) }).sorted

        case CIntTag(14) :: cond :: ifTrue :: ifFalse :: Nil => ExpressionScheme.If(cond.toScheme, ifTrue.toScheme, ifFalse.toScheme)

        case CIntTag(15) :: CInt(n) :: Nil if n >= 0 => ExpressionScheme.NaturalLiteral(n)
        case CIntTag(16) :: CInt(n) :: Nil => ExpressionScheme.IntegerLiteral(n)

        case CIntTag(18) :: CString(head) :: tail
          if tail.zipWithIndex.forall {
            case (t, i) if i % 2 == 0 && t.toScheme != null => true
            case (CString(_), i) if i % 2 == 1 => true
            case _ => false
          } =>
          if (tail.isEmpty)
            ExpressionScheme.TextLiteral(List(), head)
          else {
            val trailing = tail.last.asInstanceOf[CString].data
            ExpressionScheme.TextLiteral(data.drop(1).init.grouped(2).toList.map { array => (array(0).asInstanceOf[CString].data, array(1).toScheme) }, trailing)
          }

        case CString(name) :: CInt(index) :: Nil => ExpressionScheme.Variable(VarName(name), index)

        case CIntTag(33) :: CBytes(bytes) :: Nil => ExpressionScheme.BytesLiteral(CBytes.byteArrayToHexString(bytes))

        case CIntTag(19) :: x :: Nil => ExpressionScheme.Assert(x.toScheme)

        case CIntTag(26) :: body :: tipe :: Nil => ExpressionScheme.Annotation(body.toScheme, tipe.toScheme)

        case CIntTag(24) :: maybeHash :: CIntTag(importModeTag) :: CIntTag(schemeTag) :: tail => // ExpressionScheme.Import
          val digest = maybeHash match {
            case CNull => None
            case CBytes(bytes) if bytes.length == 34 && bytes(0) == 0x12.toByte && bytes(1) == 0x20.toByte => Some(ExpressionScheme.BytesLiteral(CBytes.byteArrayToHexString(bytes.drop(2))))
          }
          val importMode = ImportMode.cborCodeDict(importModeTag)
          val importType: ImportType[Expression] = (schemeTag, tail) match {
            case (t, headersOrCNull :: CString(authority) :: relativeURL) if SyntaxConstants.Scheme.cborCodeDict.keySet contains t =>
              val headers = if (headersOrCNull == CNull) None else Some(headersOrCNull.toScheme)
              val query = if (relativeURL.last == CNull) None else Some(relativeURL.last.asString)
              val segments = relativeURL.init.map(_.asString)
              val url = SyntaxConstants.URL(scheme = SyntaxConstants.Scheme.cborCodeDict(t), authority = authority, path = SyntaxConstants.File.of(segments), query = query)
              ImportType.Remote[Expression](url, headers.map(Expression.apply))

            case (t, filePath) if SyntaxConstants.FilePrefix.cborCodeDict.keySet contains t =>
              val filePrefix: FilePrefix = FilePrefix.cborCodeDict(t)
              ImportType.Path(filePrefix, SyntaxConstants.File.of(filePath.map(_.asString)))

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
          case _ => false
        } => ExpressionScheme.With(base.toScheme, defs.map {
          case CIntTag(0) => PathComponent.DescendOptional
          case CString(name) => PathComponent.Label(FieldName(name))
        }, target.toScheme)

        case CIntTag(30) :: CIntTag(year) :: CIntTag(month) :: CIntTag(day) :: Nil if month >= 1 && month <= 12 && day >= 1 && day <= 31 => ExpressionScheme.DateLiteral(year, month, day)

        case CIntTag(31) :: CIntTag(hours) :: CIntTag(minutes) :: CTagged(4, CArray(Array(CIntTag(precision), CIntTag(totalSeconds)))) :: Nil if hours >= 0 && hours <= 23 && minutes >= 0 && minutes < 60 && precision <= 0 && precision >= -9 =>
          val power = math.pow(10, -precision).toInt
          assert(power > 0)
          val seconds: Int = totalSeconds / power
          val nanos: Int = (totalSeconds % power) * math.pow(10, precision + 9).toInt

          ExpressionScheme.TimeLiteral(LocalTime.of(hours, minutes, seconds, nanos))


        case CIntTag(32) :: (CTrue | CFalse) :: CIntTag(hours) :: CIntTag(minutes) :: Nil if hours >= 0 && hours <= 23 && minutes >= 0 && minutes < 60 =>
          val sign = data(1) match {
            case CTrue => 1
            case CFalse => -1
          }
          ExpressionScheme.TimeZoneLiteral(sign * (hours * 60 + minutes))


        case _ => ().die(s"Invalid top-level array $this while parsing CBOR")
      }

    case CTagged(tag, data) => ().die(s"Unexpected tagged top-level CBOR object: tag $tag with data $data")
    case CBytes(_) | CMap(_) => ().die(s"Unexpected top-level CBOR object: $this")
  }

  def asString: String = (this.asInstanceOf[CString].data).or(s"This CBORmodel is $this and not a CString")
}

object CBORmodel {

  def fromCbor(obj: CBORObject): CBORmodel = if (obj == null) CNull else {
    val decoded: CBORmodel = obj.getType match {
      case CBORType.Number => throw new Exception(s"Unexpected CBOR type Number in object $obj")

      case CBORType.Boolean => obj.getSimpleValue match {
        case 20 => CFalse
        case 21 => CTrue
        case 22 => CNull
        case x => throw new Exception(s"boolean has unexpected simple value $x")
      }

      case CBORType.SimpleValue => obj.getSimpleValue match {
        case 20 => CFalse
        case 21 => CTrue
        case 22 => CNull
        case x => throw new Exception(s"got CBOR simple value $x")
      }

      case CBORType.ByteString => CBytes(obj.GetByteString)
      case CBORType.TextString => CString(obj.AsString)
      case CBORType.Array =>
        val objs: Array[CBORObject] = obj.ToObject(classOf[Array[CBORObject]])
        CArray(objs.map(fromCbor))
      case CBORType.Map =>
        val objs: java.util.Map[CBORObject, CBORObject] = obj.ToObject(classOf[java.util.Map[CBORObject, CBORObject]])
        CMap(objs.asScala.map { case (k, v) => (fromCbor(k).asString, fromCbor(v)) }.toMap)
      case CBORType.Integer =>
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

  final case object CNull extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.Null

    override def toString: String = "null"
  }

  final case object CTrue extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.True

    override def toString: String = "true"
  }

  final case object CFalse extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.False

    override def toString: String = "false"
  }

  // Pattern-match CInt with an integer value. (Note: BigInt does not have `unapply`.)
  object CIntTag {
    // Cheat sheet: `x match { case CIntTag(1) => ... }` will match successfully when CIntTag.unapply(x) == Some(1).
    def unapply(x: CInt): Option[Int] = Some(x.data.intValue).filter(_ => x.data.isValidInt)
  }

  // Either a 64-bit int or a bigint.
  final case class CInt(data: BigInt) extends CBORmodel {
    override def toCBOR: CBORObject = CBOR.naturalToCbor2(data)

    override def toString: String = data.toString
  }

  final case class CDouble(data: Double) extends CBORmodel {
    override def toCBOR: CBORObject = {
      val result = data match {
        // Important: match -0.0 before 0.0 or else it cannot match.
        case -0.0 => CBORObject.FromObject(java.lang.Double.valueOf(data)) //CBORObject.FromFloatingPointBits(0x8000L, 2)
        case 0.0 => CBORObject.FromFloatingPointBits(0L, 2)
        case Double.NaN => CBORObject.FromFloatingPointBits(0x7e00L, 2)
        case Double.NegativeInfinity => CBORObject.FromFloatingPointBits(0xfc00L, 2)
        case Double.PositiveInfinity => CBORObject.FromFloatingPointBits(0x7c00L, 2)
        case _ => CBORObject.FromObject(java.lang.Double.valueOf(data))
      }
      result
    }

    override def toString: String = f"$data%.1f"
  }

  final case class CString(data: String) extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.FromObject(data)

    override def toString: String = s"\"$data\"" //s"\"$escaped\""

    lazy val escaped: String = data.flatMap {
      case '\b' => "\\b"
      case '\n' => "\\n"
      case '\f' => "\\f"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case '"' => "\\\""
      case '\\' => "\\\\"
      case c if c.toInt > 255 || c.toInt < 20 => s"\\u${c.toHexString.toUpperCase}"
      case c => c.toString
    }
  }

  final case class CArray(data: Array[CBORmodel]) extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.FromObject(data.map(_.toCBOR))

    override def toString: String = "[" + data.map(_.toString).mkString(", ") + "]"

    override def equals(obj: Any): Boolean = obj.isInstanceOf[CArray] && (obj.asInstanceOf[CArray].data.zip(data).forall { case (x, y) => x equals y })
  }

  object CBytes {
    def byteArrayToHexString(data: Array[Byte]): String = data.map(b => String.format("%02X", Byte.box(b))).mkString("")
  }

  final case class CBytes(data: Array[Byte]) extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.FromObject(data)

    override def toString: String = "h'" + byteArrayToHexString(data) + "'"

    override def equals(obj: Any): Boolean = obj.isInstanceOf[CBytes] && (obj.asInstanceOf[CBytes].data sameElements data)
  }

  final case class CMap(data: Map[String, CBORmodel]) extends CBORmodel {
    override def toCBOR: CBORObject = {
      val dict = CBORObject.NewOrderedMap
      data.toSeq.sortBy(_._1).foreach { case (k, v) => dict.Add(k, v.toCBOR) } // Dhall requires sorting by the dictionary's keys.
      CBORObject.FromObject(dict)
    }

    override def toString: String = "{" + data.toSeq.sortBy(_._1).map { case (k, v) => s"\"$k\": $v" }.mkString(", ") + "}"

    override def equals(obj: Any): Boolean = obj.isInstanceOf[CMap] && (obj.asInstanceOf[CMap].data.zip(data).forall { case (x, y) => x equals y })
  }

  final case class CTagged(tag: Int, data: CBORmodel) extends CBORmodel {
    override def toCBOR: CBORObject = CBORObject.FromObjectAndTag(data.toCBOR, tag)

    override def toString: String = s"$tag($data)"
  }

  def toCBORmodel: Any => CBORmodel = {
    case null => CNull
    case true => CTrue
    case false => CFalse
    case x: Int => CInt(BigInt(x))
    case x: Long => CInt(BigInt(x))
    case x: BigInt => CInt(x)
    case x: String => CString(x)
    case x: Array[Any] => CArray(x.map(toCBORmodel))
    case x: Map[String, Any] => CMap(x.map { case (k, v) => (k, toCBORmodel(v)) })
    case x: ExpressionScheme[Expression] => CBOR.toCborModel(x)
    case x: Expression => CBOR.toCborModel(x)
    case x: CBORmodel => x
    case x => throw new Exception(s"Invalid input toCBORmodel($x:${x.getClass})")
  }

  def array(objs: Any*): CArray = CArray(Array(objs: _*).map(toCBORmodel))

}

// See https://github.com/dhall-lang/dhall-lang/blob/master/standard/binary.md
object CBOR {

  def exprToBytes(e: Expression): Array[Byte] = toCborModel(e).toCBOR.EncodeToBytes()

  def bytesToExpr(bytes: Array[Byte]): Expression = fromCbor(CBORObject.DecodeFromBytes(bytes)).toScheme

  def naturalToCbor2(index: Natural): CBORObject =
    if (index < BigInt(1).<<(64))
      CBORObject.FromObject(index.bigInteger)
    else
      CBORObject.FromObject(EInteger.FromBytes(index.toByteArray, false)) // TODO: Does this work correctly? Do we need to set littleEndian = true?

  def toCborModel(e: Expression): CBORmodel = e.scheme match {
    case Variable(name, index) => if (name == underscore) CInt(index) else array(name.name, index)

    case ExpressionScheme.Lambda(name, tipe, body) => if (name == underscore) array(1, tipe, body) else array(1, name.name, tipe, body)

    case ExpressionScheme.Forall(name, tipe, body) => if (name == underscore) array(2, tipe, body) else array(2, name.name, tipe, body)

    case e@ExpressionScheme.Let(_, _, _, _) =>
      @tailrec def loop(acc: Seq[Any], expr: ExpressionScheme[Expression]): Seq[Any] = expr match {
        case ExpressionScheme.Let(VarName(name), tipe, subst, body) => loop((acc :+ name) :+ (tipe.orNull: Any) :+ subst, body)
        case _ => acc :+ expr
      }

      array(25 +: loop(Seq(), e): _*)

    case ExpressionScheme.If(cond, ifTrue, ifFalse) => array(14, cond, ifTrue, ifFalse)

    case ExpressionScheme.Merge(record, update, tipe) =>
      val args: Seq[Expression] = Seq(record, update) ++ tipe.toSeq
      array(6 +: args: _*)

    case ExpressionScheme.ToMap(data, tipe) =>
      val args: Seq[Expression] = Seq(data) ++ tipe.toSeq
      array(27 +: args: _*)

    case ExpressionScheme.EmptyList(Expression(ExpressionScheme.Application(Expression(ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.List)), tipe))) => array(4, tipe)

    case ExpressionScheme.EmptyList(tipe) => array(28, tipe)

    case ExpressionScheme.NonEmptyList(exprs) => array(4 +: null +: exprs: _*)

    case ExpressionScheme.Annotation(data, tipe) => array(26, data, tipe)

    case ExpressionScheme.ExprOperator(lop, op, rop) => array(3, op.cborCode, lop, rop)

    case f@ExpressionScheme.Application(_, _) =>
      @tailrec def loop(args: Seq[Expression], expr: Expression): Seq[Expression] = expr.scheme match {
        case ExpressionScheme.Application(f, a) => loop(a +: args, f)
        case _ => expr +: args
      }

      array(0 +: loop(Seq(), f): _*)

    case ExpressionScheme.Field(base, FieldName(name)) => array(9, base, name)

    case ExpressionScheme.ProjectByLabels(base, labels) => array(10 +: base +: labels.map(_.name): _*)

    case ExpressionScheme.ProjectByType(base, by) => array(10, base, array(by))

    case ExpressionScheme.Completion(base, target) => array(3, 13, base, target)

    case ExpressionScheme.Assert(data) => array(19, data)

    case ExpressionScheme.With(data, pathComponents, body) =>
      val path: Seq[Any] = pathComponents.map {
        case PathComponent.Label(FieldName(name)) => name
        case PathComponent.DescendOptional => 0
      }
      array(29, data, array(path: _*), body)

    case ExpressionScheme.DoubleLiteral(value) => CDouble(value) // TODO: this does not work correctly for value = -0.0 or value = +0.0 because of CBOR-java issue #24

    case ExpressionScheme.NaturalLiteral(value) => array(15, value)

    case ExpressionScheme.IntegerLiteral(value) => array(16, value)

    case ExpressionScheme.TextLiteral(interpolations, trailing) =>
      val objects: Seq[Any] = interpolations.flatMap { case (head, tail) => Seq(head, tail) } :+ trailing
      array(18 +: objects: _*)

    case b@ExpressionScheme.BytesLiteral(_) => array(33, CBytes(b.bytes))

    case ExpressionScheme.DateLiteral(y, m, d) => array(30, y, m, d)

    case ExpressionScheme.TimeLiteral(time) =>
      @tailrec def getPrecision(nanos: Long, initPrecision: Int): Int =
        if (nanos <= 0) 0
        else if (nanos % 10 > 0) initPrecision
        else getPrecision(nanos / 10, initPrecision - 1)

      val precision = getPrecision(time.getNano, 9)
      val totalSeconds: Long = (time.getSecond * math.pow(10, precision).toLong + time.getNano) / math.pow(10, precision).toLong
      array(31, time.getHour, time.getMinute, CTagged(4, array(precision, totalSeconds)))

    case ExpressionScheme.TimeZoneLiteral(totalMinutes) =>
      val hours: Int = math.abs(totalMinutes) / 60
      val minutes: Int = math.abs(totalMinutes) % 60
      val isPositive: Boolean = totalMinutes >= 0
      val cborSign: CBORmodel = if (isPositive) CTrue else CFalse
      array(32, cborSign, hours, minutes)

    case ExpressionScheme.RecordType(defs) =>
      val dict = defs
        .map { case (FieldName(name), expr) => (name, expr) }
        .toMap
      array(7, dict)

    case ExpressionScheme.RecordLiteral(defs) =>
      val dict = defs
        .map { case (FieldName(name), expr) => (name, expr) }
        .toMap
      array(8, dict)

    case ExpressionScheme.UnionType(defs) =>
      val dict = defs
        .map { case (ConstructorName(name), maybeExpr) => (name, maybeExpr.orNull: Any)
        }.toMap
      array(11, dict)

    case ExpressionScheme.ShowConstructor(data) => array(34, data)

    case ExpressionScheme.Import(importType, importMode, digest) =>
      val integrity = digest.map(d => CBytes("\u0012\u0020".getBytes ++ d.bytes)).orNull
      val part1 = Seq(integrity, importMode.cborCode)
      val part2 = importType match {
        case ImportType.Missing => Seq(7)

        case ImportType.Remote(SyntaxConstants.URL(scheme, authority, SyntaxConstants.File(segments), query), headers) =>
          scheme.cborCode +: (headers.orNull: Any) +: authority +: segments :+ (query.orNull: Any)

        case ImportType.Path(filePrefix, SyntaxConstants.File(segments)) => filePrefix.cborCode +: segments

        case ImportType.Env(envVarName) => Seq(6, envVarName)
      }
      array(24 +: (part1 ++ part2): _*)

    case ExpressionScheme.KeywordSome(data) => array(5, null, data)

    case ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.True) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) => CTrue

    case ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.False) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) => CFalse

    case ExpressionScheme.ExprBuiltin(builtin) => CString(builtin.entryName)

    case ExpressionScheme.ExprConstant(constant) => CString(constant.entryName)
  }

}
