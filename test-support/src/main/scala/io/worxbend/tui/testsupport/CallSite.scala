package io.worxbend.tui.testsupport

/** Makes an assertion thrown from inside this library point at the test line that called it, instead of at the helper
  * that threw.
  *
  * A `java.lang.AssertionError` records the stack where it was *constructed*. Constructed inside `GoldenFrames` or
  * `BufferAssertions`, its top frame is that helper — so a test calling `assertMatches` three times reports the same
  * source line for all three failures, and an IDE's "jump to failure" lands in this library rather than in the test.
  * (Rust solves the same problem with `#[track_caller]`; the JVM has no equivalent, so the fix is to edit the stack.)
  *
  * This drops the *leading* frames belonging to the helpers that throw, so the first frame a test reporter prints is
  * the caller's own. Nothing else about the error changes: the message, the cause and the remaining frames are
  * untouched.
  *
  * Frames are only ever removed from the front, and never all of them. If every frame is a helper's — the library
  * calling itself, or a JVM that handed back a stack the trimming would empty — the original stack is kept, on the
  * grounds that a stackless error is strictly worse than a misattributed one.
  */
private[testsupport] object CallSite:

  private val LibraryPackage: String = "io.worxbend.tui.testsupport."

  /** The classes whose frames are dropped: the helpers that throw. Named one by one rather than matched by package,
    * because this library's own test suites live in that same package — trimming by package alone would throw away the
    * very frame the trimming exists to expose whenever a glyphora suite is the caller. A new helper that throws an
    * assertion adds its name here.
    */
  private val Helpers: Set[String] = Set("BufferAssertions", "GoldenFrames", "GoldenFixtures", "Pilot", "CallSite")

  /** Whether `frame` belongs to one of the [[Helpers]]. Companion objects and lambdas append `$…` to the class name, so
    * only the part before the first `$` is compared.
    */
  private def isHelperFrame(frame: StackTraceElement): Boolean =
    val name = frame.getClassName
    name.startsWith(LibraryPackage) && Helpers.contains(name.stripPrefix(LibraryPackage).takeWhile(_ != '$'))

  /** Returns `error` with this library's own leading frames removed, for throwing at the call site:
    * {{{
    * throw CallSite.attribute(AssertionError(message))
    * }}}
    *
    * The argument is mutated and handed back rather than copied, which is safe because every caller passes a freshly
    * constructed error that nothing else has seen yet.
    */
  def attribute[E <: Throwable](error: E): E =
    val frames  = error.getStackTrace
    val trimmed = frames.dropWhile(isHelperFrame)
    if trimmed.nonEmpty && trimmed.length < frames.length then error.setStackTrace(trimmed)
    error
