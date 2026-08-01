package io.worxbend.tui.core

/** The axis a [[Layout]] splits along: `Horizontal` divides an area into columns, `Vertical` into rows.
  *
  * It names the axis the constraints apply to, not the direction content flows — a `Horizontal` layout gives every
  * segment the full height of the area and shares its width.
  */
enum Direction:
  case Horizontal, Vertical
