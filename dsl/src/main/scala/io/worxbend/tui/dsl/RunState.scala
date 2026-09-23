package io.worxbend.tui.dsl

import io.worxbend.tui.runtime.{RenderThread, RunnerConfig}

/** The clock behind one run's timed services — the toast stack and the effect stack — which outlive any single
  * [[TuiApp.runWith]] invocation and so cannot take the run's clock at construction. It holds the system clock until a
  * `runWith` hands in another, so a test driving the app with a hand-moved clock steps every timed feature together:
  * the intro, the effects, the toasts and the runner's own tick schedule all read the same reading.
  */
private[dsl] final class RunClock:
  private var reading: () => Long    = () => System.nanoTime()
  def now(): Long                    = reading()
  def use(reading: () => Long): Unit = this.reading = reading

/** How many layers covered the app's own view when the last frame was composed — pushed screens plus the command
  * palette — and which screen was on top. Compared against the current state on every frame to decide whether focus
  * should move into an incoming layer or back out of one that has gone; see `TuiApp.syncFocusLayers`.
  *
  * One value rather than two fields because the count and the top screen are only ever read and written together: a
  * count without the screen it was taken with cannot tell a swap at the same depth from no change at all.
  */
private[dsl] final case class LayerSnapshot(count: Int, top: Option[Screen]):

  /** Reference identity, not `==`: two screens can be equal values and still be different pushes. */
  def sameTopAs(other: LayerSnapshot): Boolean = (top, other.top) match
    case (Some(mine), Some(theirs)) => mine eq theirs
    case (None, None)               => true
    case _                          => false

private[dsl] object LayerSnapshot:
  val Empty: LayerSnapshot = LayerSnapshot(0, None)

/** The mutable state of a single [[TuiApp.runWith]] invocation: whether a redraw is pending, the focus-decorated tree
  * the last frame produced (events are routed against that tree, not against a freshly evaluated one), the focus
  * tracker, the intro player, and the one [[RunnerConfig]] the run was started with.
  *
  * Owned by one `runWith` call and touched only on the render thread — the event callbacks, the render lambda, and the
  * app's own hooks all run there, so none of these fields is synchronised. The splash player lives here rather than on
  * the app because an intro belongs to a run: running the same app twice plays it twice.
  */
private[dsl] final class RunState(val splash: SplashPlayer, val config: RunnerConfig):
  var invalidated: Boolean = false

  /** The render-and-dispatch engine this run drives. `TuiApp` is that engine plus this file's policy — Tab traversal,
    * Ctrl+P, Ctrl+C, screens, toasts, the splash — so the focus bookkeeping and the tree events are routed against live
    * in [[ElementHost]] and are reached through here.
    */
  val host: ElementHost = ElementHost()

  /** This run's focus tracker, which is the host's. Named here because the layer bookkeeping below and the imperative
    * `focusTo`/`clearFocus` helpers both work directly against it.
    */
  def tracker: FocusTracker = host.tracker

  /** The state `useSignal`/`useState` hand out, keyed by where in the view the call was made. Owned by this run and
    * touched only while its render thread is evaluating the view, like everything else here — running the same app a
    * second time therefore starts every component-local value fresh.
    */
  val viewState: ViewState = ViewState()

  /** This run's render loop, captured in `onStart` while it is still registered, so the exit path can hand the
    * [[AnimationClock]] entry back. `None` until then, and for a run whose `onStart` never happened.
    */
  var renderLoop: Option[RenderThread.RenderLoop] = None

  /** What covered the app's own view when the last frame was composed; see [[LayerSnapshot]]. */
  var layers: LayerSnapshot = LayerSnapshot.Empty

  /** Whether the runner itself is producing ticks for this run — that is, whether the app configured a `tickRate`. When
    * it is, the ambient ticker below stays out of the way entirely and nothing about ticking changes.
    */
  var runnerTicks: Boolean = false

  /** The repeating tick this run started for itself because the frame it composed contained an animation — see
    * [[AmbientTick]] for why the ticker and its interval are one value. Retargeted after every frame and cancelled on
    * the way out; `None` means the app currently owes no ticks at all, which is the state an app showing nothing
    * animated sits in.
    */
  var ambient: Option[AmbientTick] = None
