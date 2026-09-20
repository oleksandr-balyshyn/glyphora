package io.worxbend.tui.examples.showcase

import io.worxbend.tui.dsl.*

import java.util.Locale
import scala.concurrent.duration.DurationInt

/** showcase: the app-chrome tour — scaffold with top bar + sidebar + status bar, tabbed pages, theme switching, toasts,
  * a modal screen, and the command palette. Doubles as the manual PTY test bed.
  *
  * Keys: `ctrl+t` theme · `ctrl+n` toast · `ctrl+o` modal · `ctrl+p` palette · `Tab` focus · `Esc` quit.
  */
class ShowcaseApp extends TuiApp:

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(200.millis))

  override def splash: Option[SplashScreen] = Some(
    SplashScreen(
      centered(36, 5)(bigText("GLYPHORA").fg(Color.Cyan)),
      effect = Effect.coalesce(800.millis),
      minimumDuration = 1200.millis,
    )
  )

  private val themes          = Vector(Theme.Dark, Theme.Light, Theme.HighContrast)
  val themeIndex: Signal[Int] = Signal(0)
  override def theme: Theme   = themes(themeIndex.peek)

  val selectedTab: Signal[Int]     = Signal(0)
  val sidebarList: ListState       = ListState()
  val noteField: TextInputState    = TextInputState()
  val logState: LogState           = LogState()
  val ticks: Signal[Int]           = Signal(0)
  private val loadingScroll        = ScrollViewState()
  private val DemoCycleTicks: Int  = 50
  private val LogTickInterval: Int = 10

  override def onTick(): Unit =
    ticks.update(_ + 1)
    if ticks.peek % LogTickInterval == 0 then logState.append(s"tick ${ticks.peek}")

  override def bindings: KeyBindings = KeyBindings(
    binding("ctrl+t", "switch theme")(themeIndex.update(index => (index + 1) % themes.size)),
    binding("ctrl+n", "show a toast")(notify("hello from glyphora", NoticeLevel.Success)),
    binding("ctrl+o", "open modal")(openModal()),
    binding("ctrl+y", "copy note to clipboard") {
      copyToClipboard(noteField.value)
      notify("copied to clipboard", NoticeLevel.Info)
    },
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

  def view(using ReactiveScope, Theme): Element =
    scaffold(
      topBar =
        Some(topBar("glyphora", tabs = Seq("Widgets", "Loading", "Log", "About"), selectedTab = selectedTab.get)),
      sidebar = Some(sidebar(sidebarPane, width = 22)),
      statusBar = Some(statusBar(bindings)),
    )(mainPane)

  private def sidebarPane(using Theme): Element =
    panel("Menu")(
      list(Seq("dashboard", "deployments", "services", "settings"), sidebarList)
    )

  private def mainPane(using ReactiveScope, Theme): Element =
    tabbedContent(
      "Widgets" -> widgetsPage,
      "Loading" -> loadingPage,
      "Log"     -> log(logState),
      "About"   -> markdown(ShowcaseApp.AboutMarkdown),
    )(selectedTab)

  /** A live gallery of every loading preset, animating against the app's own tick counter — the quickest way to pick
    * one, and a standing check that no preset renders as a hole.
    */
  private def loadingPage(using ReactiveScope, Theme): Element =
    // the gallery is taller than most terminals, so it scrolls rather than hiding its tail below the fold
    scrollView(loadingGallery, loadingScroll)

  /** The gallery's own content, sized in rows rather than in fills.
    *
    * Every section carries an explicit `.length` because this whole tree is measured before it is drawn: a `.fill` says
    * "the container decides", which `Element.intrinsicHeight` correctly answers as *unmeasurable*, and one unmeasurable
    * section makes the sum unmeasurable too — at which point `scrollView` falls back to the viewport's own height and
    * the page stops scrolling. Each section's children are one row apiece, so the count is the height.
    */
  private def loadingGallery(using ReactiveScope, Theme): Element =
    val progress = demoProgress
    column(
      rule("spinners"),
      spinnersSection,
      rule("determinate"),
      determinateSection(progress),
      rule("shapes"),
      shapesSection,
      rule("indeterminate"),
      indeterminateSection,
    )

  private def demoProgress(using ReactiveScope): Double = ((ticks.get % DemoCycleTicks) + 1) / DemoCycleTicks.toDouble

  private def spinnersSection(using ReactiveScope, Theme): Element =
    column(SpinnerPreset.All.map(preset => spinner(preset.name).preset(preset).length(1))*)
      .length(SpinnerPreset.All.size)

  private def determinateSection(progress: Double)(using Theme): Element =
    column(
      ProgressPreset.All.map { bar =>
        row(
          text(bar.name).length(14),
          progressBar(progress).preset(bar).bare.fill,
        ).length(1)
      }*
    ).length(ProgressPreset.All.size)

  private def shapesSection(using ReactiveScope, Theme): Element =
    column(
      row(
        orbitSpinner().radius(4).length(10),
        orbitSpinner().path(OrbitPath.Square).radius(4).length(10),
        orbitSpinner().radius(4).solid.reversed.length(10),
        spinnerGrid().preset(SpinnerPreset.DotsRing).fill,
      ).length(3),
      row(
        text("linear").length(10),
        linearSpinner().fill,
        spacer(1),
        text("bounce").length(7),
        linearSpinner().bouncing.fill,
      ).length(1),
    )

  private def indeterminateSection(using ReactiveScope, Theme): Element =
    column(
      IndeterminateMotion.values.toSeq.map { motion =>
        row(
          // Locale.ROOT, as `Language.of` and `DataTable.filteredRows` do: case-folding an identifier through the
          // default locale turns an 'I' into a dotless 'ı' in Turkish. None of today's three names contain one,
          // which is exactly why this is worth pinning — the next case added would break the label silently.
          text(motion.toString.toLowerCase(Locale.ROOT)).length(14),
          indeterminateBar().motion(motion).preset(ProgressPreset.Blocks).fill,
        ).length(1)
      }*
    ).length(IndeterminateMotion.values.length)

  private def widgetsPage(using ReactiveScope, Theme): Element =
    val t = ticks.get
    column(
      spacer(1),
      row(
        spacer(2),
        text("note:").length(6),
        input(noteField, placeholder = "type here…"),
      ).length(1),
      spacer(1),
      gauge(demoProgress),
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

object Main extends ShowcaseApp
