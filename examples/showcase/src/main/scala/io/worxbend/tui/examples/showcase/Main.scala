package io.worxbend.tui.examples.showcase

import io.worxbend.tui.dsl.*
import io.worxbend.tui.runtime.RunnerConfig
import io.worxbend.tui.widgets.{ListState, LogState, TextInputState}

import scala.concurrent.duration.DurationInt

// (splash effect durations use the same DurationInt syntax)

/** showcase: the app-chrome tour — scaffold with top bar + sidebar + status bar, tabbed pages, theme switching, toasts,
  * a modal screen, and the command palette. Doubles as the manual PTY test bed.
  *
  * Keys: `ctrl+t` theme · `ctrl+n` toast · `ctrl+o` modal · `ctrl+p` palette · `Tab` focus · `Esc` quit.
  */
final class ShowcaseApp extends TuiApp:

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(200.millis))

  override def splash: Option[SplashScreen] = Some(
    SplashScreen(
      centered(36, 5)(bigText("GLYPHORA").color(Color.Cyan)),
      effect = Effect.coalesce(800.millis),
      minimumDuration = 1200.millis,
    )
  )

  private val themes          = Vector(Theme.Dark, Theme.Light, Theme.HighContrast)
  val themeIndex: Signal[Int] = Signal(0)
  override def theme: Theme   = themes(themeIndex.peek)

  val selectedTab: Signal[Int]  = Signal(0)
  val sidebarList: ListState    = ListState()
  val noteField: TextInputState = TextInputState()
  val logState: LogState        = LogState()
  val ticks: Signal[Int]        = Signal(0)

  override def onTick(): Unit =
    ticks.update(_ + 1)
    if ticks.peek % 10 == 0 then logState.append(s"tick ${ticks.peek}")

  override def bindings: KeyBindings = KeyBindings(
    binding("ctrl+t", "switch theme")(themeIndex.update(index => (index + 1) % themes.size)),
    binding("ctrl+n", "show a toast")(notify("hello from glyphora", ToastLevel.Success)),
    binding("ctrl+o", "open modal")(openModal()),
    binding("esc", "quit")(quit()),
  )

  private def openModal(): Unit = pushScreen(Screen {
    centered(34, 5) {
      panel("About")(
        text("glyphora showcase").bold,
        text("press Esc to close").dim,
      ).rounded.onKeyEvent {
        case KeyEvent(KeyCode.Escape, _) =>
          popScreen()
          true
        case _                           => false
      }
    }
  })

  def view(using ReactiveScope): Element =
    given Theme = theme
    val _       = themeIndex.get // theme switching re-renders
    scaffold(
      topBar = Some(topBar("glyphora", tabs = Seq("Widgets", "Log", "About"), selectedTab = selectedTab.get)),
      sidebar = Some(sidebar(sidebarPane, width = 22)),
      statusBar = Some(statusBar(bindings)),
    )(mainPane)

  private def sidebarPane: Element =
    panel("Menu")(
      list(Seq("dashboard", "deployments", "services", "settings"), sidebarList)
    )

  private def mainPane(using ReactiveScope): Element =
    tabbedContent(
      "Widgets" -> widgetsPage,
      "Log"     -> log(logState),
      "About"   -> markdown(ShowcaseApp.AboutMarkdown),
    )(selectedTab)

  private def widgetsPage(using ReactiveScope): Element =
    val t = ticks.get
    column(
      spacer(1),
      row(
        text("  note: ").length(8),
        input(noteField, placeholder = "type here…"),
      ).length(1),
      spacer(1),
      gauge(((t % 50) + 1) / 50.0),
      sparkline(Vector.tabulate(40)(i => ((math.sin((t + i) * 0.4) + 1) * 50).toLong)),
      spacer,
      rule("chrome"),
      text("Tab cycles focus · ctrl+p opens the palette").dim,
    )

object ShowcaseApp:
  private val AboutMarkdown =
    """# glyphora
      |
      |A **Scala 3** TUI library.
      |
      |- scaffold, theme, key bindings
      |- screens, toasts, palette
      |- `ctrl+t` switches the theme
      |""".stripMargin

object Main:
  def main(args: Array[String]): Unit =
    ShowcaseApp().run().left.foreach(error => println(s"failed to run: $error"))
