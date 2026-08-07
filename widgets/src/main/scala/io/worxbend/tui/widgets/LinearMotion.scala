package io.worxbend.tui.widgets

import scala.concurrent.duration.FiniteDuration

/** The walk a linear spinner's head takes along a one-dimensional track.
  *
  * A path rather than two widgets: the animation is one head with a trail behind it, and only the walk differs. Each
  * case owns its own cycle length and its own reading of [[LinearFlow]], because the two are not reversed by the same
  * operation — see [[positionAt]].
  */
enum LinearPath:

  /** The head runs off one end and re-enters at the other — a sawtooth. It never pauses, so it reads as steady rather
    * than as effort, which is what a long-running background task wants.
    */
  case Wrap

  /** The head runs to the far end and turns — a triangle wave. The eye-catching one, and the right choice when the task
    * deserves attention.
    */
  case Bounce

  /** How many discrete steps one full cycle takes on a track of `slots`.
    *
    * `Bounce` is `2 * (slots - 1)` rather than `2 * slots` because the two end slots are visited exactly once per cycle
    * and the interior slots twice — that is what stops the head dwelling for a frame at each turn and appearing to
    * stick. The `max` keeps a one-slot track from asking [[Animation.step]] for a zero-length cycle.
    */
  private[widgets] def cycleLength(slots: Int): Int =
    this match
      case Wrap   => math.max(1, slots)
      case Bounce => math.max(1, 2 * (slots - 1))

  /** Where the head sits `phase` steps into the cycle, in `[0, slots)`.
    *
    * `flow` is applied differently per case, and that asymmetry is the design rather than an oversight. A sawtooth is
    * not time-symmetric, so reversing it means reversing *time*: the head then leaves by the near edge and re-enters by
    * the far one, and phase zero shows the same frame either way. A triangle wave *is* time-symmetric — `t(-p)` equals
    * `t(p)` exactly — so reversing its time changes nothing visible, and "the other way" can only mean starting from
    * the far end, which is a mirror of the position.
    */
  private[widgets] def positionAt(phase: Int, slots: Int, flow: LinearFlow): Int =
    val span  = math.max(1, slots)
    val cycle = cycleLength(slots)
    this match
      case Wrap   =>
        flow match
          case LinearFlow.Forward  => math.floorMod(phase, cycle)
          case LinearFlow.Backward => math.floorMod(-phase, cycle)
      case Bounce =>
        val folded = math.floorMod(phase, cycle)
        val raw    = if folded < span then folded else cycle - folded
        flow match
          case LinearFlow.Forward  => raw
          case LinearFlow.Backward => span - 1 - raw

/** Which way a linear spinner travels. An enum rather than a signed rate or a `reversed: Boolean`, so a call site reads
  * as a direction and a reversed spinner is greppable rather than a minus sign away from a typo.
  */
enum LinearFlow:
  case Forward, Backward

/** How the slots behind a linear spinner's head are shaded.
  *
  * Every case is a function of one number — how many steps ago the head was in that slot — because that is what makes a
  * trail work on a bouncing path as well as a wrapping one. A trail defined by *position* behind the head has no answer
  * at the turn, where "behind" flips; a trail defined by *time* simply folds back over itself, and the brighter sample
  * wins.
  */
enum LinearTrail:

  /** Every trailing slot at full brightness — a solid window. The only case that survives on a terminal with neither
    * color nor sub-cell glyphs, because it moves presence rather than intensity. On [[LinearPath.Wrap]] this is the
    * classic wrapping ellipsis pulse: for a sawtooth the head's last `length` positions *are* a contiguous run.
    */
  case Solid

  /** A bright head decaying to the oldest slot. Directional, so the head — the thing the eye actually tracks — is
    * unambiguous. The oldest slot lands at `1 / length` rather than at zero, so the trail's extent stays legible
    * instead of ending in a slot indistinguishable from the track.
    */
  case Comet

  /** Brightest in the middle, tapering to both ends over `fade` slots. Symmetric, which is what suits
    * [[LinearPath.Bounce]]: there the direction reverses twice a cycle, and a directional tail visibly flips. `fade` of
    * zero or less is a square window, i.e. [[Solid]].
    */
  case Taper(fade: Int = 2)

  /** Brightness in `[0, 1]` for the slot the head occupied `stepsAgo` steps ago, out of a `length`-step trail. */
  private[widgets] def intensityAt(stepsAgo: Int, length: Int): Double =
    val span = math.max(1, length)
    val k    = math.max(0, math.min(span - 1, stepsAgo))
    this match
      case Solid       => 1.0
      case Comet       => 1.0 - k.toDouble / span.toDouble
      case Taper(fade) =>
        if fade <= 0 then 1.0
        else
          val edge = math.min(k, span - 1 - k)
          math.min(1.0, (edge + 1).toDouble / (fade + 1).toDouble)

/** Which way a linear spinner's track runs, and therefore which sub-cell axis carries its position.
  *
  * The two are not symmetric, and the asymmetry is worth knowing before choosing one. A braille cell is 2 dots across
  * and 4 down, so a horizontal track buys 2 positions per cell and can grade each over 4 brightness levels, while a
  * vertical track buys 4 positions per cell and can grade each over 2. Horizontal moves in coarser steps and fades more
  * smoothly; vertical is the other way round.
  */
enum LinearAxis:
  case Horizontal, Vertical

/** Elapsed time to a brightness per track slot, for the linear spinner family.
  *
  * The whole family is one head walking a [[LinearPath]] with a [[LinearTrail]] behind it. A trail is modelled as the
  * head's own *past* — slot `s` is lit at the brightness of the most recent step at which `head == s` — which is what
  * makes one formula cover both a wrapping window and a bouncing dot. It also means the scrolling window is not a
  * separate mechanism: for a sawtooth, the head's last `n` positions are exactly a contiguous run of `n` slots.
  *
  * Everything is a pure function of `elapsed`. Nothing is retained between renders and no clock is read here, so a test
  * can render any moment directly and two renders at the same `elapsed` are byte-identical.
  */
private[widgets] object LinearMotion:

  /** Brightness per slot, index `0` at the start of the track, for the frame `elapsed` into the animation.
    *
    * `period` is one full cycle — one traverse for [[LinearPath.Wrap]], a round trip for [[LinearPath.Bounce]] — so the
    * animation takes the same wall-clock time whatever the app's tick rate. It does *not* hold the per-cell speed
    * constant across widths: a wider track scrolls faster per cell. That is the same trade [[Skeleton]] and
    * [[IndeterminateBar]] already make, and the alternative ([[Animation.stepAtRate]], as [[Marquee]] uses) would make
    * two spinners of different widths fall out of step.
    *
    * `trailSlots` is in slots, not cells: at braille resolution a horizontal track has two slots per cell.
    *
    * The array is fresh per call rather than pooled, because a render already allocates and a shared buffer would be
    * the one piece of state in an otherwise pure family.
    */
  def intensities(
      elapsed: FiniteDuration,
      period: FiniteDuration,
      slots: Int,
      path: LinearPath,
      flow: LinearFlow,
      trail: LinearTrail,
      trailSlots: Int,
  ): Array[Double] =
    if slots <= 0 then Array.emptyDoubleArray
    else
      val lit    = math.max(1, math.min(slots, trailSlots))
      val cycle  = path.cycleLength(slots)
      val phase  = Animation.step(elapsed, period, cycle)
      val levels = new Array[Double](slots)
      var k      = 0
      while k < lit do
        // `head` is periodic with `cycle` and `floorMod` distributes over subtraction, so the head's position k steps
        // ago is recoverable from the wrapped phase alone — no unwrapped step counter, and `Animation.step` suffices.
        val at = path.positionAt(math.floorMod(phase - k, cycle), slots, flow)
        // Brightest wins: on a bounce the trail folds back over itself near a turn, and the newer, brighter sample is
        // the one the eye is tracking. Erring bright rather than dim also keeps the trail from appearing to break.
        levels(at) = math.max(levels(at), trail.intensityAt(k, lit))
        k += 1
      levels

  /** Where the head alone sits, for a caller that draws no trail — a scroll thumb, or a one-slot marker. */
  def headAt(
      elapsed: FiniteDuration,
      period: FiniteDuration,
      slots: Int,
      path: LinearPath,
      flow: LinearFlow,
  ): Int =
    if slots <= 0 then 0
    else
      val cycle = path.cycleLength(slots)
      path.positionAt(Animation.step(elapsed, period, cycle), slots, flow)
