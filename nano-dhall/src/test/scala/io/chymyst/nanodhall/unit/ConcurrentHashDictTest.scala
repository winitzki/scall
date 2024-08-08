package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.ConcurrentHashDict
import munit.FunSuite

class ConcurrentHashDictTest extends FunSuite {
  test("hash strings") {
    val dict = new ConcurrentHashDict[String](10)
    val abc  = dict.store("abc")
    val cde  = dict.store("cde")
    expect(abc != cde)
    expect(dict.store("abc") == abc)
    expect(dict.store("cde") == cde)
    expect(dict.lookup(abc) == Some("abc"))
    expect(dict.lookup(cde) == Some("cde"))
    expect(dict.lookup(0) == None)
  }
}
