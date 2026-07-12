package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Color, Rect, Widget}

import scala.util.control.NonFatal

/** A raster image rendered with half-block cells: every terminal cell shows two vertical pixels (`▀` with the upper
  * pixel as foreground and the lower as background), the universally supported protocol from the reference ecosystems.
  * Kitty/iTerm2/Sixel protocols remain out of scope (ROADMAP).
  *
  * `pixels` is row-major RGB; the image is scaled to the render area by nearest-neighbor sampling, preserving nothing
  * but coverage — pre-scale for quality.
  */
final case class Image(pixels: Vector[Vector[Color.Rgb]]) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val sourceHeight = pixels.size
    val sourceWidth = pixels.headOption.map(_.size).getOrElse(0)
    if !area.isEmpty && sourceHeight > 0 && sourceWidth > 0 then
      val targetRows = area.height * 2 // two pixels per cell vertically
      var y = 0
      while y < area.height do
        var x = 0
        while x < area.width do
          val upper = sample(x, y * 2, area.width, targetRows, sourceWidth, sourceHeight)
          val lower = sample(x, y * 2 + 1, area.width, targetRows, sourceWidth, sourceHeight)
          buffer.set(
            area.x + x,
            area.y + y,
            Cell("▀", io.worxbend.tui.core.Style(fg = Some(upper), bg = Some(lower))),
          )
          x += 1
        y += 1

  private def sample(
      column: Int,
      row: Int,
      targetWidth: Int,
      targetHeight: Int,
      sourceWidth: Int,
      sourceHeight: Int,
  ): Color.Rgb =
    val sx = math.min(sourceWidth - 1, column * sourceWidth / targetWidth)
    val sy = math.min(sourceHeight - 1, row * sourceHeight / targetHeight)
    pixels(sy)(sx)

object Image:

  /** Decodes an image file (PNG/JPEG/GIF via `javax.imageio`) into pixels.
    *
    * Kept out of the `Image` constructor path so apps that never load files (and native-image builds) do not link AWT;
    * native-image users of `fromFile` should verify their platform's `libawt` support.
    */
  def fromFile(path: java.nio.file.Path): Either[String, Image] =
    try
      val decoded = javax.imageio.ImageIO.read(path.toFile)
      if decoded == null then Left(s"unsupported image format: $path")
      else
        val pixels: Vector[Vector[Color.Rgb]] = Vector.tabulate(decoded.getHeight) { y =>
          Vector.tabulate(decoded.getWidth) { x =>
            val argb = decoded.getRGB(x, y)
            Color.Rgb((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff): Color.Rgb
          }
        }
        Right(Image(pixels))
    catch case NonFatal(error) => Left(s"failed to read $path: ${error.getMessage}")
