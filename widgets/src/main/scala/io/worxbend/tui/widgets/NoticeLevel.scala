package io.worxbend.tui.widgets

/** The severity of a message to the user, shared by every surface that reports one — notices, toasts, and badges.
  *
  * One enum rather than one per widget, because severity is the same idea everywhere and a second copy would drift: an
  * app that maps its own error type onto a level should not have to map it again per widget.
  *
  * The glyphs are deliberately from the geometric-shapes and dingbat blocks rather than emoji: emoji are two columns
  * wide and inconsistently rendered, which makes a column of notices ragged.
  */
enum NoticeLevel:
  case Success, Info, Warning, Error

  /** The default marker drawn before the message. One column wide for every level, so messages align. */
  def icon: String = this match
    case Success => "✔"
    case Info    => "•"
    case Warning => "▲"
    case Error   => "✖"

  /** A short uppercase tag, for a badge or a log line that has no room for prose. */
  def tag: String = this match
    case Success => "OK"
    case Info    => "INFO"
    case Warning => "WARN"
    case Error   => "FAIL"
