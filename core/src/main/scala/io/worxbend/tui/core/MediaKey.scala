package io.worxbend.tui.core

/** A hardware transport or volume key — the play, pause, next-track and volume buttons on a keyboard's top row or on a
  * pair of headphones.
  *
  * These are reported by the kitty keyboard protocol as the code-point block 57428-57440, and carried by
  * [[KeyCode.Media]]. Two things have to go right for one to arrive: the terminal must speak that protocol, and the
  * desktop environment must not swallow the key first — many take the volume and transport keys for themselves before
  * any application sees them. So a binding on a media key is an enhancement for the people whose setup delivers it,
  * never the only way to reach a command.
  *
  * `Pause` here is the media transport pause, which is a different physical key from [[KeyCode.Pause]] — the
  * Pause/Break key above the arrow cluster.
  */
enum MediaKey:
  case Play, Pause, PlayPause, Reverse, Stop, FastForward, Rewind
  case TrackNext, TrackPrevious, Record
  case LowerVolume, RaiseVolume, MuteVolume
