package io.worxbend.tui.core.palette

import io.worxbend.tui.core.Color

import org.scalatest.funsuite.AnyFunSuite

/** The Material palette is transcribed data, so the tests are of two kinds: a handful of spot checks against the
  * published hex values — which is what catches a digit typed wrong — and invariants applied to every ramp at once, so
  * a ramp added later is covered without anyone remembering to add a test for it.
  */
final class MaterialSpec extends AnyFunSuite:

  /** Every ramp in the palette, accented ones flattened to their tonal half. Enumerated once here so the invariants
    * below cover a new ramp the moment it is added to this list.
    */
  private val Tonals: Seq[(String, Tonal)] =
    Seq(
      "Red"        -> Material.Red.tonal,
      "Pink"       -> Material.Pink.tonal,
      "Purple"     -> Material.Purple.tonal,
      "DeepPurple" -> Material.DeepPurple.tonal,
      "Indigo"     -> Material.Indigo.tonal,
      "Blue"       -> Material.Blue.tonal,
      "LightBlue"  -> Material.LightBlue.tonal,
      "Cyan"       -> Material.Cyan.tonal,
      "Teal"       -> Material.Teal.tonal,
      "Green"      -> Material.Green.tonal,
      "LightGreen" -> Material.LightGreen.tonal,
      "Lime"       -> Material.Lime.tonal,
      "Yellow"     -> Material.Yellow.tonal,
      "Amber"      -> Material.Amber.tonal,
      "Orange"     -> Material.Orange.tonal,
      "DeepOrange" -> Material.DeepOrange.tonal,
      "Brown"      -> Material.Brown,
      "Gray"       -> Material.Gray,
      "BlueGray"   -> Material.BlueGray,
    )

  private val Accents: Seq[(String, Accented)] =
    Seq(
      "Red"        -> Material.Red,
      "Blue"       -> Material.Blue,
      "Green"      -> Material.Green,
      "DeepOrange" -> Material.DeepOrange,
    )

  test("spot-checked shades match the published values"):
    assert(Material.Red.c500 == Color.Rgb(0xf4, 0x43, 0x36))
    assert(Material.Blue.c500 == Color.Rgb(0x21, 0x96, 0xf3))
    assert(Material.Amber.c700 == Color.Rgb(0xff, 0xa0, 0x00))
    assert(Material.BlueGray.c900 == Color.Rgb(0x26, 0x32, 0x38))
    assert(Material.Red.a400 == Color.Rgb(0xff, 0x17, 0x44))
    assert(Material.Black == Color.Rgb(0, 0, 0))
    assert(Material.White == Color.Rgb(255, 255, 255))

  test("all nineteen ramps have ten shades in field order"):
    Tonals.foreach { (name, ramp) =>
      assert(ramp.all.sizeIs == 10, s"$name does not have ten shades")
      assert(
        ramp.all == Seq(
          ramp.c50,
          ramp.c100,
          ramp.c200,
          ramp.c300,
          ramp.c400,
          ramp.c500,
          ramp.c600,
          ramp.c700,
          ramp.c800,
          ramp.c900,
        ),
        s"$name's shades are not in c50..c900 order",
      )
    }

  test("every accented ramp has four accents"):
    Accents.foreach { (name, ramp) =>
      assert(ramp.accents == Seq(ramp.a100, ramp.a200, ramp.a400, ramp.a700), s"$name's accents are out of order")
    }

  test("every channel of every colour is a byte"):
    val everyColor = Tonals.flatMap((_, ramp) => ramp.all) ++ Accents.flatMap((_, ramp) => ramp.accents)
    everyColor.foreach {
      case Color.Rgb(r, g, b) =>
        assert(r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255, s"channel out of range in $r/$g/$b")
      case other              => fail(s"a palette entry is not a 24-bit colour: $other")
    }

  test("shade looks a step up by its Material name"):
    assert(Material.Blue.shade(500).contains(Material.Blue.c500))
    assert(Material.Gray.shade(50).contains(Material.Gray.c50))
    // 950 is a Tailwind step, not a Material one, and answering with the nearest shade would hide the mistake
    assert(Material.Blue.shade(950).isEmpty)
    assert(Material.Blue.shade(0).isEmpty)

  test("no two ramps are the same ramp"):
    assert(Tonals.map((_, ramp) => ramp).distinct.sizeIs == Tonals.size, "a ramp was transcribed twice")
