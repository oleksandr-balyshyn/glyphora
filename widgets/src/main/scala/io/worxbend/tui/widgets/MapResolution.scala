package io.worxbend.tui.widgets

/** How much coastline detail [[Shape.WorldMap]] paints.
  *
  * The two outlines are the same coastlines sampled at different densities, so a map does not change shape between
  * them, only how closely it follows the real thing. Which one to pick is a question of how many dots the canvas has,
  * not of how the map should look: painting five thousand points into a pane that offers a few hundred dots costs four
  * times the work to draw the same picture.
  */
enum MapResolution:

  /** Roughly a thousand outline points — enough for a small pane, or for any canvas at [[CanvasResolution.Cell]]
    * resolution, where one cell is one dot.
    */
  case Low

  /** Roughly five thousand outline points — the one to pair with [[CanvasResolution.Braille]] on a large pane, where
    * each cell carries eight dots and the lower-detail outline starts to look visibly polygonal.
    */
  case High

  /** The outline's interleaved longitude/latitude pairs, in EPSG:4326 degrees.
    *
    * `private[widgets]` because the array is shared and mutable in the way every JVM array is: handing it out would let
    * a caller edit the world map for every other caller in the process. Callers who want the points draw them through
    * [[Shape.WorldMap]].
    */
  private[widgets] def points: Array[Double] =
    this match
      case Low  => WorldTable.low
      case High => WorldTable.high
