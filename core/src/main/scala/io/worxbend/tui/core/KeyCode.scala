package io.worxbend.tui.core

/** The key a key event reports: a printable character, a named editing/navigation key, or a function key. */
enum KeyCode:
  case Char(c: scala.Char)
  case Enter, Escape, Backspace, Tab, Delete, Insert, Home, End, PageUp, PageDown
  case Up, Down, Left, Right
  case F(n: Int)
