package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, Text}
import io.worxbend.tui.widgets as w

/** The app-chrome presets (ROADMAP 0.3.0): top bar, status bar, sidebar, scaffold, help overlay, and layout
  * helpers. All of them are plain element builders over the ambient [[Theme]] — nothing here bypasses the
  * widget layer.
  */

/** A one-row title bar over the theme surface: title left, optional tabs center, optional right-side text. */
def topBar(title: String, tabs: Seq[String] = Seq.empty, selectedTab: Int = 0, right: String = "")(using
    theme: Theme,
): Element =
  val parts = Seq.newBuilder[Element]
  parts += Element.text(s" $title ").styled(_ => theme.surface.bold).length(title.length + 2)
  if tabs.nonEmpty then
    val tabsWidth = tabs.map(_.length).sum + (tabs.size - 1) * 3 // " │ " between titles
    parts += Element.spacer(2)
    parts += Element.tabs(tabs, selectedTab).styled(_ => theme.surface).length(tabsWidth)
  parts += Element.spacer
  if right.nonEmpty then parts += Element.text(s"$right ").styled(_ => theme.surface).length(right.length + 1)
  FilledElement(Element.row(parts.result()*), theme.surface).length(1)

/** A one-row status bar of `key description` hints over the theme surface. */
def statusBar(hints: Seq[(String, String)])(using theme: Theme): Element =
  val content = hints.map((key, description) => s"$key $description").mkString("  │  ")
  FilledElement(Element.text(s" $content").styled(_ => theme.surface), theme.surface).length(1)

/** Status bar fed directly from the app's declared [[KeyBindings]]. */
def statusBar(bindings: KeyBindings)(using Theme): Element =
  statusBar(bindings.hints)

/** Sidebar configuration for [[scaffold]]. */
final case class Sidebar(content: Element, width: Int = 24, onRight: Boolean = false)

def sidebar(content: Element, width: Int = 24, onRight: Boolean = false): Sidebar =
  Sidebar(content, width, onRight)

/** The application shell: optional top bar, optional sidebar (left or right of the content), the content
  * filling the middle, and an optional status bar.
  */
def scaffold(
    topBar: Option[Element] = None,
    sidebar: Option[Sidebar] = None,
    statusBar: Option[Element] = None,
)(content: Element): Element =
  val middle = sidebar match
    case None => content.fill
    case Some(side) =>
      val sideElement = side.content.length(side.width)
      val mainElement = content.fill
      val ordered = if side.onRight then Seq(mainElement, sideElement) else Seq(sideElement, mainElement)
      Element.row(ordered*).fill
  val rows = topBar.toSeq ++ Seq(middle) ++ statusBar.toSeq
  Element.column(rows*)

/** A centered help dialog listing every hinted binding — render it last (over the view) while visible. */
def helpOverlay(bindings: KeyBindings, title: String = "Help")(using theme: Theme): Element =
  val width = bindings.hints.map((key, description) => key.length + description.length + 3).maxOption.getOrElse(4)
  val lines = bindings.hints.map((key, description) => s"%-${math.min(width, 24)}s".format(key) + description)
  Element.widget(
    w.Dialog(title, Text.raw(lines.mkString("\n")), buttons = Seq.empty, style = theme.primary),
  )

// ---- layout presets ----

/** Side pane + main pane. */
def sidebarLayout(side: Element, main: Element, sideWidth: Int = 24): Element =
  Element.row(side.length(sideWidth), main.fill)

/** The classic list-left, detail-right split. */
def masterDetail(master: Element, detail: Element, masterWidth: Int = 30): Element =
  sidebarLayout(master, detail, masterWidth)

/** `content` at a fixed size, centered both ways in whatever space is available. */
def centered(width: Int, height: Int)(content: Element): Element =
  Element.column(
    Element.spacer,
    Element
      .row(Element.spacer, content.withProps(content.props.copy(constraint = Some(Constraint.Length(width)))), Element.spacer)
      .length(height),
    Element.spacer,
  )
