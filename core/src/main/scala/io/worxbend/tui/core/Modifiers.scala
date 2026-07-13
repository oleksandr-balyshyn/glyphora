package io.worxbend.tui.core

/** Text-attribute flags (bold, italic, …) packed into an `Int` bitset.
  *
  * An opaque bitset rather than a `Set[Modifier]` on purpose: `Style` values are created per-cell, potentially
  * thousands of times per frame, so `Style` must stay a small value with no boxed collection inside (SPEC.md §2.3).
  */
opaque type Modifiers = Int

object Modifiers:
  val None: Modifiers       = 0
  val Bold: Modifiers       = 1 << 0
  val Dim: Modifiers        = 1 << 1
  val Italic: Modifiers     = 1 << 2
  val Underline: Modifiers  = 1 << 3
  val Blink: Modifiers      = 1 << 4
  val Reverse: Modifiers    = 1 << 5
  val Hidden: Modifiers     = 1 << 6
  val CrossedOut: Modifiers = 1 << 7

  extension (m: Modifiers)
    def |(other: Modifiers): Modifiers = (m: Int) | (other: Int)
    def has(flag: Modifiers): Boolean  = ((m: Int) & (flag: Int)) != 0
    def isEmpty: Boolean               = (m: Int) == 0
