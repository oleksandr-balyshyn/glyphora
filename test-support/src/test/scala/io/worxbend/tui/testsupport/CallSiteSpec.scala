package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, Color, Rect, Style}

import org.scalatest.funsuite.AnyFunSuite

/** Pins that a failed assertion points at the test that called it.
  *
  * Without this, every failure from `BufferAssertions` or `GoldenFrames` reported the helper's own line as the place
  * the test went wrong, which is the same line for every call in the suite — so a test that compares three frames gave
  * no clue which of the three failed beyond the message text, and an editor jumping to the failure landed in this
  * library instead of in the test.
  */
final class CallSiteSpec extends AnyFunSuite:

  private def failingComparison(): AssertionError =
    intercept[AssertionError](
      BufferAssertions.assertEquals(
        BufferAssertions.buffered("a"),
        BufferAssertions.buffered("b"),
      )
    )

  test("an assertion failure's top frame is the caller, not the assertion helper"):
    val top = failingComparison().getStackTrace.head
    assert(top.getClassName == classOf[CallSiteSpec].getName)
    assert(top.getFileName == "CallSiteSpec.scala")

  test("no leading frame belongs to an assertion helper"):
    val top = failingComparison().getStackTrace.head
    assert(top.getClassName != "io.worxbend.tui.testsupport.BufferAssertions$")
    assert(top.getClassName != "io.worxbend.tui.testsupport.CallSite$")

  test("a golden-fixture failure is attributed the same way"):
    val buffer = Buffer(Rect(0, 0, 4, 1))
    buffer.setString(0, 0, "ab", Style.Default.withFg(Color.Red))
    val error  = intercept[AssertionError](GoldenFrames.assertMatchesText("nowhere", buffer, "zz"))
    assert(error.getStackTrace.head.getClassName == classOf[CallSiteSpec].getName)

  test("the message and cause are untouched"):
    val cause = IllegalStateException("underlying")
    val error = CallSite.attribute(AssertionError("the message", cause))
    assert(error.getMessage == "the message")
    assert(error.getCause eq cause)

  test("an error whose whole stack is inside the library keeps its stack"):
    // trimming every frame would leave a stackless error, which is worse than a misattributed one: a reporter would
    // have nothing at all to print
    val error    = AssertionError("all ours")
    val internal = Array(
      StackTraceElement("io.worxbend.tui.testsupport.GoldenFrames$", "assertMatches", "GoldenFrames.scala", 40),
      StackTraceElement("io.worxbend.tui.testsupport.BufferAssertions$", "assertEquals", "BufferAssertions.scala", 12),
    )
    error.setStackTrace(internal)
    assert(CallSite.attribute(error).getStackTrace.toSeq == internal.toSeq)

  test("an error with no library frames at all is left exactly as it is"):
    val error    = AssertionError("not ours")
    val external = Array(StackTraceElement("com.example.Thing", "go", "Thing.scala", 7))
    error.setStackTrace(external)
    assert(CallSite.attribute(error).getStackTrace.toSeq == external.toSeq)
