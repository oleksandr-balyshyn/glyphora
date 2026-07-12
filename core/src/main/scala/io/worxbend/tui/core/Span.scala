package io.worxbend.tui.core

/** A run of text rendered with a single [[Style]]. */
final case class Span(content: String, style: Style):
  def width: Int = CharWidth.of(content)

object Span:
  def raw(content: String): Span = Span(content, Style.Default)
