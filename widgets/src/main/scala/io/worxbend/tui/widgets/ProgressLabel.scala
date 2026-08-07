package io.worxbend.tui.widgets

/** What a progress meter writes beside its bar.
  *
  * A named ADT rather than an `Option[String]`, because that option had to carry three meanings — absent for the
  * percentage, present for custom text, and present-but-empty for no caption at all. The third is the kind of sentinel
  * a reader cannot guess and a type cannot check, and it forced the widget to treat `Some("")` specially anyway.
  */
enum ProgressLabel:

  /** The percentage, rounded — the default. */
  case Percentage

  /** Fixed text in place of the percentage. */
  case Text(value: String)

  /** The percentage after fixed text, as in `syncing 42%`. */
  case TextAndPercentage(value: String)

  /** No caption: the bar gets the whole row. */
  case Hidden

  /** Renders this label for a `[0, 1]` progress fraction. */
  def render(fraction: Double): String =
    val percent = s"${math.round(fraction * 100)}%"
    this match
      case Percentage           => percent
      case Text(value)          => value
      case TextAndPercentage(v) => if v.isEmpty then percent else s"$v $percent"
      case Hidden               => ""
