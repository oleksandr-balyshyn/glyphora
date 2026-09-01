package io.worxbend.tui.dsl

import io.worxbend.tui.core.{CharWidth, Constraint, MouseEventKind, Text}
import io.worxbend.tui.widgets as w

/** The app-chrome presets: top bar, status bar, sidebar, scaffold, help overlay, and layout helpers. All of them are
  * plain element builders over the ambient [[Theme]] — nothing here bypasses the widget layer.
  */

/** A one-row title bar over the theme surface: title left, optional tabs center, optional right-side text. */
def topBar(title: String, tabs: Seq[String] = Seq.empty, selectedTab: Int = 0, right: String = "")(using
    theme: Theme
): Element =
  // the reserved cells must be *display* columns, or a CJK/emoji title asks for fewer cells than it renders into.
  // the gaps are `spacer`s, not spaces baked into the strings: a spacer inside the `FilledElement` still paints the
  // surface, and unlike " $title " it cannot drift out of step with the `.length(...)` beside it
  val parts = Seq.newBuilder[Element]
  parts += Element.spacer(1)
  parts += Element.text(title).styled(_ => theme.surface.bold).length(CharWidth.of(title))
  parts += Element.spacer(1)
  if tabs.nonEmpty then
    val tabsWidth = tabs.map(CharWidth.of).sum + (tabs.size - 1) * 3 // " │ " between titles
    parts += Element.spacer(2)
    parts += Element.tabs(tabs, selectedTab).styled(_ => theme.surface).length(tabsWidth)
  parts += Element.spacer
  if right.nonEmpty then
    parts += Element.text(right).styled(_ => theme.surface).length(CharWidth.of(right))
    parts += Element.spacer(1)
  FilledElement(Element.row(parts.result()*), theme.surface).length(1)

/** A one-row status bar of `key description` hints over the theme surface. */
def statusBar(hints: Seq[(String, String)])(using theme: Theme): Element =
  val content = hints.map((key, description) => s"$key $description").mkString("  │  ")
  // a leading `spacer`, not a leading space in the string — see `topBar`
  val row     = Element.row(Element.spacer(1), Element.text(content).styled(_ => theme.surface).fill)
  FilledElement(row, theme.surface).length(1)

/** Status bar fed directly from the app's declared [[KeyBindings]]. */
def statusBar(bindings: KeyBindings)(using Theme): Element =
  statusBar(bindings.hints)

/** Which edge of the content a [[Sidebar]] sits against. */
enum Side:
  case Left, Right

/** Vertical placement of a block inside the area it is drawn in — the down-the-screen counterpart of [[Alignment]]. */
enum VerticalAlignment:
  case Top, Middle, Bottom

/** Sidebar configuration for [[scaffold]]. */
final case class Sidebar(content: Element, width: Int = 24, side: Side = Side.Left)

def sidebar(content: Element, width: Int = 24, side: Side = Side.Left): Sidebar =
  Sidebar(content, width, side)

/** The application shell: optional top bar, optional sidebar (left or right of the content), the content filling the
  * middle, and an optional status bar.
  */
def scaffold(
    topBar: Option[Element] = None,
    sidebar: Option[Sidebar] = None,
    statusBar: Option[Element] = None,
)(content: Element): Element =
  val middle = sidebar match
    case None       => content.fill
    case Some(pane) =>
      val sideElement = pane.content.length(pane.width)
      val mainElement = content.fill
      val ordered     = pane.side match
        case Side.Left  => Seq(sideElement, mainElement)
        case Side.Right => Seq(mainElement, sideElement)
      Element.row(ordered*).fill
  val rows   = topBar.toSeq ++ Seq(middle) ++ statusBar.toSeq
  Element.column(rows*)

/** Cells between a key label and its description in [[helpOverlay]], and the width past which a long key spec stops
  * widening the key column (a longer one runs into its own description rather than pushing the whole table wider).
  */
private val HelpColumnGap    = 3
private val MaxHelpKeyColumn = 24

/** A centered help dialog listing every hinted binding — render it last (over the view) while visible. */
def helpOverlay(bindings: KeyBindings, title: String = "Help")(using theme: Theme): Element =
  // display columns, not UTF-16 lengths: a binding labelled with a CJK or emoji key otherwise asks for fewer cells
  // than it renders into and the second column stops lining up
  val width  = bindings.hints
    .map((key, description) => CharWidth.of(key) + CharWidth.of(description) + HelpColumnGap)
    .maxOption
    .getOrElse(4)
  val column = math.min(width, MaxHelpKeyColumn)
  val lines  = bindings.hints.map { (key, description) =>
    key + " " * math.max(0, column - CharWidth.of(key)) + description
  }
  Element.widget(
    w.Dialog(title, Text.raw(lines.mkString("\n")), buttons = Seq.empty, style = theme.primary)
  )

// ---- layout presets ----

/** Side pane + main pane.
  *
  * The pane is `pane`, not `side`, because [[Side]] and `Sidebar.side` fifty lines above already own that word for
  * *which edge*; one name for two ideas in one file is a reading trap.
  */
def sidebarLayout(pane: Element, main: Element, paneWidth: Int = 24): Element =
  Element.row(pane.length(paneWidth), main.fill)

/** The classic list-left, detail-right split. */
def masterDetail(master: Element, detail: Element, masterWidth: Int = 30): Element =
  sidebarLayout(master, detail, masterWidth)

/** `content` laid over a backdrop that reports a mouse press no part of `content` took, so a dialog can be closed by
  * clicking away from it.
  *
  * How it works, and what it therefore means. [[EventRouter]] resolves a press against the *topmost* sibling subtree
  * that covers the pointer and never offers it to the ones underneath, and it only considers elements that recorded an
  * area — that is, elements that are focusable or carry a mouse handler of their own. `content` is the later of the two
  * layers here, so a press that lands on any of its controls is resolved there and the backdrop never hears about it. A
  * press that lands where the dialog has no control resolves to nothing inside `content`, falls through to the
  * backdrop, and calls `onOutsidePress`.
  *
  * The consequence worth knowing before using this: "outside" means "on no control of the dialog", which is not quite
  * the same as "outside the dialog's frame". A press on a dialog's border or on a caption inside it lands on no control
  * and so counts as outside. A dialog that wants its whole frame to be inert says so in one line, by consuming presses
  * at its root:
  *
  * {{{
  * panel("Really?")(body).onMouseEvent(_ => true)
  * }}}
  *
  * A geometric test is deliberately not attempted. Nothing in the tree records where a dialog was *placed* — placement
  * is spacers around a sized node, and the placed node has no area of its own unless it is a control — so a rectangle
  * to compare against would have to be guessed, and a guess that is wrong closes a dialog the user was using.
  *
  * `TuiApp` wires this for any modal `Screen` whose [[Dismissal]] includes a click outside; call it directly only for a
  * dialog an application layers itself.
  */
def dismissibleOverlay(content: Element)(onOutsidePress: () => Unit): Element =
  val backdrop = Element.spacer.onMouseEvent { event =>
    if event.kind == MouseEventKind.Down then
      onOutsidePress()
      true
    else false
  }
  Element.layers(backdrop, content)

/** `content` at a fixed size, centered both ways in whatever space is available. */
def centered(width: Int, height: Int)(content: Element): Element =
  place(width, height)(content)

/** Positions `content` (sized `width` x `height`) inside whatever area it is given, aligned `horizontal` x `vertical`
  * (centered on both axes by default — the [[centered]] case). Pass `backdrop` to paint the surrounding whitespace with
  * a style (Lip Gloss `Place`-style), e.g. a dimmed area behind a dialog.
  *
  * The parameter is `backdrop`, not `fill`, because `Element.fill` already means something else in every snippet this
  * appears in: there it is the layout extension that claims a row's or column's leftover space.
  *
  * Each axis is named in its own vocabulary: `horizontal` takes [[Alignment]] (`Left`/`Center`/`Right`, the same enum
  * `Block` titles and `Paragraph` text use) and `vertical` takes [[VerticalAlignment]] (`Top`/`Middle`/`Bottom`).
  */
def place(
    width: Int,
    height: Int,
    horizontal: Alignment = Alignment.Center,
    vertical: VerticalAlignment = VerticalAlignment.Middle,
    backdrop: Option[Style] = None,
)(content: Element): Element =
  // a `spacer` before the block pushes it off the near edge, one after it off the far edge, and both together centre it
  def bracketAcross(align: Alignment, block: Element): Seq[Element]       =
    align match
      case Alignment.Left   => Seq(block, Element.spacer)
      case Alignment.Center => Seq(Element.spacer, block, Element.spacer)
      case Alignment.Right  => Seq(Element.spacer, block)
  def bracketDown(align: VerticalAlignment, block: Element): Seq[Element] =
    align match
      case VerticalAlignment.Top    => Seq(block, Element.spacer)
      case VerticalAlignment.Middle => Seq(Element.spacer, block, Element.spacer)
      case VerticalAlignment.Bottom => Seq(Element.spacer, block)
  val sized = content.withProps(content.props.copy(constraint = Some(Constraint.Length(width))))
  val row    = Element.row(bracketAcross(horizontal, sized)*).length(height)
  val placed = Element.column(bracketDown(vertical, row)*)
  backdrop match
    case Some(style) => FilledElement(placed, style)
    case None        => placed
