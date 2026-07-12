package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class CharWidthSpec extends AnyFunSuite:

  test("ASCII text is one column per character"):
    assert(CharWidth.of("hello") == 5)

  test("the empty string has zero width"):
    assert(CharWidth.of("") == 0)

  test("CJK ideographs are two columns each"):
    assert(CharWidth.of("你好") == 4)

  test("hiragana is two columns"):
    assert(CharWidth.of("あ") == 2)

  test("hangul syllables are two columns"):
    assert(CharWidth.of("한") == 2)

  test("a combining mark adds no width to its base character"):
    assert(CharWidth.of("é") == 1)

  test("a string of several combined characters counts only the bases"):
    assert(CharWidth.of("áëô") == 3)

  test("a basic emoji is two columns"):
    assert(CharWidth.of("😀") == 2) // 😀 U+1F600

  test("an emoji ZWJ family sequence is a single two-column cluster"):
    // 👨‍👩‍👧‍👦 = U+1F468 ZWJ U+1F469 ZWJ U+1F467 ZWJ U+1F466
    val family = "👨‍👩‍👧‍👦"
    assert(CharWidth.of(family) == 2)

  test("a regional-indicator flag pair is two columns"):
    val flag = "🇺🇦" // 🇺🇦
    assert(CharWidth.of(flag) == 2)

  test("two consecutive flags are four columns"):
    val flags = "🇺🇦🇵🇱" // 🇺🇦🇵🇱
    assert(CharWidth.of(flags) == 4)

  test("an emoji with a skin-tone modifier is a single two-column cluster"):
    assert(CharWidth.of("👍🏽") == 2) // 👍🏽

  test("VS16 forces emoji presentation at two columns"):
    assert(CharWidth.of("☹️") == 2) // ☹️ — U+2639 is narrow without the selector

  test("VS15 forces text presentation at one column"):
    assert(CharWidth.of("⌚︎") == 1) // ⌚ text-style — U+231A is wide without the selector

  test("mixed ASCII and CJK sums both widths"):
    assert(CharWidth.of("ab你c") == 5)

  test("decomposed hangul jamo render inside the leading consonant's two columns"):
    assert(CharWidth.of("한") == 2) // 한 as L+V+T jamo

  test("substringByWidth truncates ASCII at the exact column"):
    assert(CharWidth.substringByWidth("hello", 3) == "hel")

  test("substringByWidth never splits a wide character"):
    assert(CharWidth.substringByWidth("你好", 3) == "你")

  test("substringByWidth keeps combining marks with their base"):
    assert(CharWidth.substringByWidth("éx", 1) == "é")

  test("substringByWidth of zero columns is empty"):
    assert(CharWidth.substringByWidth("hello", 0) == "")

  test("substringByWidth returns the whole string when it fits"):
    assert(CharWidth.substringByWidth("hi", 10) == "hi")

  test("isWideCodePoint recognizes CJK, emoji, and hangul jamo starts"):
    assert(CharWidth.isWideCodePoint(0x4e00))
    assert(CharWidth.isWideCodePoint(0x1f600))
    assert(CharWidth.isWideCodePoint(0x1100))

  test("isWideCodePoint rejects ASCII and halfwidth forms"):
    assert(!CharWidth.isWideCodePoint('a'.toInt))
    assert(!CharWidth.isWideCodePoint(0xff61)) // halfwidth ideographic full stop
