package io.worxbend.tui.widgets

/** Which sides of a [[Block]] draw a border, packed into an `Int` bitset (same pattern as core `Modifiers`). */
opaque type Borders = Int

object Borders:
  val None: Borders   = 0
  val Top: Borders    = 1 << 0
  val Right: Borders  = 1 << 1
  val Bottom: Borders = 1 << 2
  val Left: Borders   = 1 << 3
  val All: Borders    = Top | Right | Bottom | Left

  extension (b: Borders)
    def |(other: Borders): Borders  = (b: Int) | (other: Int)
    def has(side: Borders): Boolean = ((b: Int) & (side: Int)) != 0
