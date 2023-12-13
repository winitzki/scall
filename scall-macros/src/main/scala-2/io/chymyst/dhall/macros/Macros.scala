package io.chymyst.dhall.macros

import izumi.reflect.AnyTag

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object Macros {
  def knownSubclasses[A]: List[AnyTag] = macro knownSubclassesImpl[A]

  def knownSubclassesImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._

    val subclasses: Set[c.universe.Symbol] = c.weakTypeOf[A].typeSymbol.asClass.knownDirectSubclasses

    val tags = subclasses.map { subclass =>
      subclass.asType.typeParams.length match {
        case 0 => q"izumi.reflect.Tag.apply[${subclass.asType}]"
        case 1 => q"izumi.reflect.TagK.apply[${subclass.asType.toTypeConstructor.typeSymbol}]"
        case 2 => q"izumi.reflect.TagKK.apply[${subclass.asType.toTypeConstructor.typeSymbol}]"
      }
    }
    q"List(..$tags)"
  }

  def knownSubclassesByTag(t: AnyTag): List[AnyTag] = {
    val params = t.closestClass.getPermittedSubclasses
    ???
  }
}
