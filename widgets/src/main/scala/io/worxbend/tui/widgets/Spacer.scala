package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Widget}

/** Renders nothing — claims layout space to push siblings apart. */
case object Spacer extends Widget:
  def render(area: Rect, buffer: Buffer): Unit = ()
