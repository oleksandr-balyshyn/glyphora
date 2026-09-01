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

  test("dropByWidth of zero or fewer columns returns the input unchanged"):
    assert(CharWidth.dropByWidth("hello", 0) == "hello")
    assert(CharWidth.dropByWidth("hello", -3) == "hello")

  test("dropByWidth removes ASCII up to the exact column"):
    assert(CharWidth.dropByWidth("hello", 3) == "lo")

  test("dropByWidth returns the empty string once the whole text has scrolled off"):
    assert(CharWidth.dropByWidth("hello", 5) == "")
    assert(CharWidth.dropByWidth("hello", 99) == "")
    assert(CharWidth.dropByWidth("\u4f60\u597d", 4) == "")
    assert(CharWidth.dropByWidth("\u4f60\u597d", 99) == "")

  test("dropByWidth never leaves half of a wide character"):
    // one column of budget lands inside the first ideograph; the whole cluster goes rather than half of it
    assert(CharWidth.dropByWidth("\u4f60\u597d", 1) == "\u597d")
    assert(CharWidth.dropByWidth("\u4f60\u597d", 2) == "\u597d")

  test("dropByWidth keeps a combining mark with its base"):
    assert(CharWidth.dropByWidth("\u00e9x", 1) == "x")
    assert(CharWidth.dropByWidth("e\u0301x", 1) == "x")

  test("dropByWidth treats an emoji ZWJ sequence as one unit"):
    val family = "\ud83d\udc68\u200d\ud83d\udc69\u200d\ud83d\udc67" // man-woman-girl, two columns in total
    assert(CharWidth.dropByWidth(family + "ab", 2) == "ab")
    assert(CharWidth.dropByWidth(family + "ab", 1) == "ab")

  test("dropByWidth of the empty string is the empty string"):
    assert(CharWidth.dropByWidth("", 4) == "")
  test("dropByWidth discards ASCII up to the exact column"):
    assert(CharWidth.dropByWidth("hello", 3) == "lo")
    assert(CharWidth.dropByWidth("hello", 0) == "hello")
    assert(CharWidth.dropByWidth("hello", -1) == "hello")
    assert(CharWidth.dropByWidth("hello", 5) == "")
    assert(CharWidth.dropByWidth("hello", 99) == "")

  test("dropByWidth drops a straddling wide cluster whole rather than half of it"):
    // One column into "你好" lands in the middle of the first character; half a character is not a character, so the
    // whole of it goes and the result is one column narrower than the arithmetic alone would suggest.
    assert(CharWidth.dropByWidth("你好", 1) == "好")
    assert(CharWidth.of(CharWidth.dropByWidth("你好", 1)) == 2)
    assert(CharWidth.dropByWidth("你好", 2) == "好")
    assert(CharWidth.dropByWidth("a你好", 1) == "你好")

  test("dropByWidth keeps combining marks and emoji sequences with their base"):
    assert(CharWidth.dropByWidth("xé", 1) == "é")
    assert(CharWidth.dropByWidth("ab👨‍👩‍👧‍👦", 2) == "👨‍👩‍👧‍👦")

  test("dropByWidth and substringByWidth cut the same string in two"):
    Seq("hello world", "你好 world", "ab👍🏽cd", "éxé").foreach { text =>
      (0 to CharWidth.of(text)).foreach { at =>
        val head = CharWidth.substringByWidth(text, at)
        val tail = CharWidth.dropByWidth(text, at)
        // At a cut inside a wide cluster neither side keeps it, so the halves can be shorter than the whole; neither
        // may ever invent text, and together they must stay in order.
        assert(text.startsWith(head), s"'$head' is not a prefix of '$text'")
        assert(text.endsWith(tail), s"'$tail' is not a suffix of '$text'")
        assert(head.length + tail.length <= text.length, s"'$head' + '$tail' overlap in '$text' at $at")
      }
    }

  test("isWideCodePoint recognizes CJK, emoji, and hangul jamo starts"):
    assert(CharWidth.isWideCodePoint(0x4e00))
    assert(CharWidth.isWideCodePoint(0x1f600))
    assert(CharWidth.isWideCodePoint(0x1100))

  test("isWideCodePoint rejects ASCII and halfwidth forms"):
    assert(!CharWidth.isWideCodePoint('a'.toInt))
    assert(!CharWidth.isWideCodePoint(0xff61)) // halfwidth ideographic full stop

  test("a regional indicator with a combining mark is not a flag (regression from property testing)"):
    val loneRiWithMark = "🇺" + "́"
    assert(CharWidth.of(loneRiWithMark) == 1)
    assert(CharWidth.of("🇺" + "́" + "🇦") == CharWidth.of("🇺" + "́") + CharWidth.of("🇦"))

  test("a variation selector with no base character before it claims no column"):
    // a stray VS16 (pasted text, a decoded escape) used to report two columns for something a terminal draws in
    // none, so a backend advanced its cursor past cells it never painted
    assert(CharWidth.of(EmojiPresentationSelector) == 0)
    assert(CharWidth.of(TextPresentationSelector) == 0)

  test("VS15 does not narrow a CJK ideograph"):
    // 你 is two columns with or without a text-presentation selector: it has no emoji presentation to switch away
    // from. Reporting one column allocates a single cell for a two-column glyph and shifts the rest of the row.
    assert(CharWidth.of("你" + TextPresentationSelector) == 2)
    assert(CharWidth.of("你" + TextPresentationSelector + "xy") == 4)

  test("VS16 does not widen a Latin letter"):
    // 'a' has no emoji presentation, so the selector is decoration the terminal ignores — claiming two columns
    // would leave a blank continuation cell in the middle of a word
    assert(CharWidth.of("a" + EmojiPresentationSelector) == 1)

  test("a variation selector still switches the presentation of an emoji-capable base"):
    assert(CharWidth.of("☹" + EmojiPresentationSelector) == 2) // narrow by default, wide as emoji
    assert(CharWidth.of("⌚" + TextPresentationSelector) == 1) // wide by default, narrow as text

  test("a ZWJ between two non-emoji characters does not glue them into one cell"):
    // pasted web text and Indic/Persian input carry stray U+200D; joining unconditionally under-counted the width
    // and let the following characters overflow the rect they were clipped to
    assert(CharWidth.of("a" + ZeroWidthJoiner + "b") == 2)
    assert(CharWidth.graphemeClusters("a" + ZeroWidthJoiner + "b").size == 2)

  test("a ZWJ next to a CJK ideograph keeps both characters' widths"):
    assert(CharWidth.of("a" + ZeroWidthJoiner + "你") == 3)
    assert(CharWidth.of("你" + ZeroWidthJoiner + "a") == 3)

  test("a ZWJ between two emoji still forms one cluster"):
    assert(CharWidth.of("👨" + ZeroWidthJoiner + "👩") == 2)
    assert(CharWidth.graphemeClusters("👨" + ZeroWidthJoiner + "👩").size == 1)

  test("a ZWJ sequence built on a legacy symbol base still forms one cluster"):
    // ❤️‍🔥 is U+2764 VS16 ZWJ U+1F525: the base predates the emoji planes, so gating the join on "is emoji" must
    // recognize the dingbats and pictographs too or the sequence splits into two cells
    val heartOnFire = "❤" + EmojiPresentationSelector + ZeroWidthJoiner + "🔥"
    assert(CharWidth.graphemeClusters(heartOnFire).size == 1)
    assert(CharWidth.of(heartOnFire) == 2)

  test("withoutControls drops C0, DEL and C1 but keeps everything else"):
    assert(CharWidth.withoutControls("a\tb") == "ab")
    assert(CharWidth.withoutControls("a" + Escape + "[31mb") == "a[31mb")
    assert(CharWidth.withoutControls("a" + Delete + "b" + NextLine + "c") == "abc")
    assert(CharWidth.withoutControls("plain text") == "plain text")

  test("withoutControls keeps combining marks and emoji clusters"):
    // the filter is a *control* filter, not a zero-width filter: a combining mark belongs to the cluster it follows
    val combining = "e" + CombiningAcute
    assert(CharWidth.withoutControls(combining) == combining)
    val family    = "\ud83d\udc68" + ZeroWidthJoiner + "\ud83d\udc69"
    assert(CharWidth.withoutControls(family) == family)
    assert(CharWidth.withoutControls(CombiningAcute) == CombiningAcute)

  test("ofCluster agrees with of on single grapheme clusters"):
    // Buffer measures every cell it writes with ofCluster; its printable-ASCII early-out is the only new width logic
    // in the fix, and a single disagreement with `of` would silently mis-reserve continuation columns
    val clusters = Seq(
      "",
      " ",
      "a",
      "~",
      0x7f.toChar.toString,                // DEL: the printable-ASCII fast path deliberately stops one short of it
      0x01.toChar.toString,                // a C0 control, zero columns
      0x00ad.toChar.toString,              // SOFT HYPHEN: category Cf below U+0300, so also zero columns
      0x0301.toChar.toString,              // a lone combining mark
      "\u4f60",
      "\u3042",
      "\ud55c",
      "\u4f60" + TextPresentationSelector, // VS15 after an ideograph is inert: still two columns
      "\u2500",
      "\u283f",
      "\ud83c\uddfa\ud83c\uddf8",
      "\ud83d\udc68" + ZeroWidthJoiner + "\ud83d\udc69",
    )
    clusters.foreach(cluster => assert(CharWidth.ofCluster(cluster) == CharWidth.of(cluster), s"cluster [$cluster]"))

  /** Spelled by codepoint rather than as literals: all three are invisible in an editor. */
  private val ZeroWidthJoiner: String           = 0x200d.toChar.toString
  private val TextPresentationSelector: String  = 0xfe0e.toChar.toString
  private val EmojiPresentationSelector: String = 0xfe0f.toChar.toString
  private val CombiningAcute: String            = 0x0301.toChar.toString

  /** Controls spelled by codepoint: a literal one in a source file is invisible and survives no reformat. */
  private val Escape: String   = 0x1b.toChar.toString
  private val Delete: String   = 0x7f.toChar.toString
  private val NextLine: String = 0x85.toChar.toString // C1
