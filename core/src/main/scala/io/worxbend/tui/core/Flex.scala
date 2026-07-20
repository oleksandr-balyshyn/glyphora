package io.worxbend.tui.core

/** How a [[Layout]] distributes any space its segments do not consume (ratatui's `Flex`).
  *
  * Flex only has an effect when the constraints leave leftover space — i.e. there is no `Fill`/`Min` greedily absorbing
  * it. With such a growing constraint present the leftover is zero and every mode behaves like [[Flex.Start]].
  *
  *   - [[Start]] — pack segments at the start; leftover trails at the end (the default).
  *   - [[End]] — pack at the end; leftover leads at the start.
  *   - [[Center]] — center the block; leftover splits evenly before and after.
  *   - [[SpaceBetween]] — first and last segments touch the edges; leftover splits between segments.
  *   - [[SpaceAround]] — equal space around each segment (edges get a half-gap).
  *   - [[SpaceEvenly]] — equal space in every gap including both edges.
  */
enum Flex:
  case Start, End, Center, SpaceBetween, SpaceAround, SpaceEvenly
