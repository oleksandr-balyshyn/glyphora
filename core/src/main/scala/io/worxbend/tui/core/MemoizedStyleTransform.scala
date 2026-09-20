package io.worxbend.tui.core

/** A `Style => Style` transform that remembers its most recent input and result, so a whole-region restyle pass pays
  * for the transform only where the style actually changes.
  *
  * Neighbouring cells nearly always share one style, and a run of cells written by one call usually shares the very
  * same `Style` object, so the comparison is reference equality first and a structural compare only after that fails.
  * Without the memo a full-frame pass would build a fresh `Style` for each of a frame's ten thousand cells to arrive at
  * the same answer ten thousand times.
  *
  * The wrapped transform must be a pure function of the style it is handed, because it is *not* called once per cell.
  *
  * Shared by [[Buffer.mapStyle]] and the effects in [[Effect]]. Not thread-safe; one instance belongs to one pass over
  * one region.
  */
private[core] final class MemoizedStyleTransform(transform: Style => Style):

  // `primed` distinguishes "no cell seen yet" from "the last cell happened to carry Style.Default", so the transform
  // is never run on a style no cell in the region actually has
  private var primed: Boolean = false
  private var lastIn: Style   = Style.Default
  private var lastOut: Style  = Style.Default

  def apply(style: Style): Style =
    if !primed || !((style eq lastIn) || style == lastIn) then
      lastIn = style
      lastOut = transform(style)
      primed = true
    lastOut
