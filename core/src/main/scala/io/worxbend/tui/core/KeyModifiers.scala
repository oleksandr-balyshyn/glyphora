package io.worxbend.tui.core

/** Modifier keys held during a key or mouse event, packed into an `Int` bitset (same pattern as [[Modifiers]]). */
opaque type KeyModifiers = Int

object KeyModifiers:
  val None: KeyModifiers  = 0
  val Shift: KeyModifiers = 1 << 0
  val Ctrl: KeyModifiers  = 1 << 1
  val Alt: KeyModifiers   = 1 << 2

  extension (m: KeyModifiers)
    def |(other: KeyModifiers): KeyModifiers = (m: Int) | (other: Int)
    def has(flag: KeyModifiers): Boolean     = ((m: Int) & (flag: Int)) != 0
    def isEmpty: Boolean                     = (m: Int) == 0
