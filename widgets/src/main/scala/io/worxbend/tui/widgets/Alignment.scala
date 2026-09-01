package io.worxbend.tui.widgets

/** `Alignment` used to be declared in this package. It moved down to `tui-core` so that a `Line` — a core value — can
  * carry its own alignment, because a module may only depend on modules below it and `tui-core` depends on nothing.
  *
  * These two aliases keep the old spelling `io.worxbend.tui.widgets.Alignment` compiling, for the widgets in this
  * package and for any application that named it. A `type` alias plus a `val` alias is used rather than an `export`
  * because exporting an enum loses the companion object, and with it `Alignment.Left` and friends.
  */
type Alignment = io.worxbend.tui.core.Alignment

/** The companion of the moved [[Alignment]], so `Alignment.Center` still resolves in this package. */
val Alignment: io.worxbend.tui.core.Alignment.type = io.worxbend.tui.core.Alignment
