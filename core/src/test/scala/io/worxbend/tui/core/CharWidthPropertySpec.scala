package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

final class CharWidthPropertySpec extends AnyFunSuite with ScalaCheckPropertyChecks:

  private val interestingChars: Gen[String] = Gen.oneOf(
    Gen.alphaNumChar.map(_.toString),
    Gen.const(" "),
    Gen.const("你"),
    Gen.const("é"),
    Gen.const("👍"),
    Gen.const("🇺🇦"),
    Gen.const("́"), // combining acute
  )

  private val genText: Gen[String] = Gen.listOf(interestingChars).map(_.mkString).map(_.take(40))

  test("width is never negative and empty text is zero"):
    forAll(genText) { text =>
      assert(CharWidth.of(text) >= 0)
    }
    assert(CharWidth.of("") == 0)

  test("substringByWidth never exceeds its budget and is a prefix"):
    forAll(genText, Gen.chooseNum(0, 30)) { (text, budget) =>
      val prefix = CharWidth.substringByWidth(text, budget)
      assert(CharWidth.of(prefix) <= math.max(0, budget))
      assert(text.startsWith(prefix))
    }

  test("concatenation never increases total width beyond the sum of the parts"):
    forAll(genText, genText) { (a, b) =>
      assert(CharWidth.of(a + b) <= CharWidth.of(a) + CharWidth.of(b))
    }

  test("cluster widths partition the total width"):
    forAll(genText) { text =>
      val clusters = CharWidth.graphemeClusters(text).toSeq
      assert(clusters.map(CharWidth.of).sum == CharWidth.of(text))
      assert(clusters.mkString == text)
    }
