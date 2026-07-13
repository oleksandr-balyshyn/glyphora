package io.worxbend.tui.core

import java.util.Arrays

/** Terminal display-width arithmetic.
  *
  * `String.length` counts UTF-16 code units, not terminal columns: CJK characters occupy two columns, combining marks
  * occupy none, and one emoji ZWJ sequence can span many codepoints yet occupy two columns. All layout and rendering
  * math in this library must go through this object — no code outside it may use `String.length` or `String.substring`
  * for anything that affects layout (SPEC.md §2.4).
  *
  * Widths are derived from the Unicode Character Database: East Asian Width `W`/`F` codepoints (see [[WidthTable]]) are
  * two columns; combining marks, format controls, and conjoining Hangul jamo are zero; everything else is one.
  * Variation selectors override the base width (VS16 forces emoji presentation, two columns; VS15 forces text
  * presentation, one column), and a regional-indicator pair (flag emoji) is two.
  */
object CharWidth:

  /** Display width of `text` in terminal columns. Control characters count as zero. */
  def of(text: String): Int =
    var total    = 0
    val clusters = graphemeClusters(text)
    while clusters.hasNext do total += clusterWidth(clusters.next())
    total

  /** The longest prefix of `text` that fits in `maxWidth` columns; never splits a grapheme cluster. */
  def substringByWidth(text: String, maxWidth: Int): String =
    val clusters  = graphemeClusters(text)
    val prefix    = StringBuilder()
    var used      = 0
    var truncated = false
    while !truncated && clusters.hasNext do
      val cluster = clusters.next()
      val width   = clusterWidth(cluster)
      if used + width > maxWidth then truncated = true
      else
        prefix.append(cluster)
        used += width
    prefix.result()

  /** Whether `codePoint` has East Asian Width `W` (Wide) or `F` (Fullwidth), i.e. occupies two columns. */
  def isWideCodePoint(codePoint: Int): Boolean =
    val table    = WidthTable.WideRanges
    val position = Arrays.binarySearch(table, codePoint)
    if position >= 0 then true
    else
      // insertion point is the index of the first element > codePoint; the codepoint is inside a range
      // when that index is odd (start already passed, end not yet reached)
      val insertion = -position - 1
      insertion % 2 == 1

  /** Splits `text` into the units that occupy terminal cells: a base codepoint plus its combining marks, variation
    * selectors, ZWJ-joined continuations, or regional-indicator partner. This is the only sanctioned way to step
    * through text one cell-unit at a time (wrapping, cursor movement, truncation).
    */
  def graphemeClusters(text: String): Iterator[String] =
    new Iterator[String]:
      private var index = 0

      def hasNext: Boolean = index < text.length

      def next(): String =
        val start = index
        val first = text.codePointAt(index)
        index += Character.charCount(first)
        if isRegionalIndicator(first) && index < text.length && isRegionalIndicator(text.codePointAt(index)) then
          index += Character.charCount(text.codePointAt(index))
        else absorbContinuations()
        text.substring(start, index)

      private def absorbContinuations(): Unit =
        var done = false
        while !done && index < text.length do
          val cp = text.codePointAt(index)
          // the ZWJ check must precede the generic continuation check: ZWJ is category Cf, and absorbing it
          // without also absorbing the codepoint it joins would split an emoji ZWJ sequence in two
          if cp == ZeroWidthJoiner && index + Character.charCount(cp) < text.length then
            index += Character.charCount(cp)
            index += Character.charCount(text.codePointAt(index))
          else if isClusterContinuation(cp) then index += Character.charCount(cp)
          else done = true

  private def clusterWidth(cluster: String): Int =
    if cluster.isEmpty then 0
    else if containsCodePoint(cluster, TextPresentationSelector) then 1
    else if containsCodePoint(cluster, EmojiPresentationSelector) then 2
    else
      val base = cluster.codePointAt(0)
      if isRegionalIndicator(base) && Character.codePointCount(cluster, 0, cluster.length) >= 2 then 2
      else if isZeroWidth(base) || Character.isISOControl(base) then 0
      else if isWideCodePoint(base) then 2
      else 1

  private def isClusterContinuation(cp: Int): Boolean =
    isZeroWidth(cp) || isEmojiModifier(cp)

  private def isZeroWidth(cp: Int): Boolean =
    val category = Character.getType(cp)
    category == Character.NON_SPACING_MARK.toInt ||
    category == Character.ENCLOSING_MARK.toInt ||
    category == Character.FORMAT.toInt ||
    isConjoiningJamoContinuation(cp)

  /** Medial vowels and trailing consonants of decomposed Hangul syllables render inside the leading consonant's two
    * columns rather than adding their own.
    */
  private def isConjoiningJamoContinuation(cp: Int): Boolean =
    (cp >= 0x1160 && cp <= 0x11ff) || (cp >= 0xd7b0 && cp <= 0xd7ff)

  private def isEmojiModifier(cp: Int): Boolean =
    cp >= 0x1f3fb && cp <= 0x1f3ff

  private def isRegionalIndicator(cp: Int): Boolean =
    cp >= 0x1f1e6 && cp <= 0x1f1ff

  private def containsCodePoint(text: String, target: Int): Boolean =
    var index = 0
    var found = false
    while !found && index < text.length do
      val cp = text.codePointAt(index)
      found = cp == target
      index += Character.charCount(cp)
    found

  private val ZeroWidthJoiner           = 0x200d
  private val TextPresentationSelector  = 0xfe0e
  private val EmojiPresentationSelector = 0xfe0f
