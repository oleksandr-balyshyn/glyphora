package io.worxbend.tui.dsl

import org.scalatest.funsuite.AnyFunSuite

import java.util.Locale

/** Pins the matching rule the command palette and `autocomplete` share. */
final class FuzzySpec extends AnyFunSuite:

  test("a query matches when its characters appear in order"):
    assert(Fuzzy.matcher("dpl")("deploy-service"))
    assert(Fuzzy.matcher("deploy-service")("deploy-service"))

  test("characters out of order do not match"):
    assert(!Fuzzy.matcher("lpd")("deploy-service"))

  test("a character missing from the candidate does not match"):
    assert(!Fuzzy.matcher("de")("restart-service"))

  test("matching ignores case on both sides"):
    assert(Fuzzy.matcher("DPL")("deploy-service"))
    assert(Fuzzy.matcher("dpl")("DEPLOY-SERVICE"))

  test("an empty query matches everything"):
    assert(Fuzzy.matcher("")("deploy-service"))
    assert(Fuzzy.matcher("")(""))

  /** Case folding must not depend on the machine's locale: `"Install".toLowerCase` is `"ınstall"` under `tr`, so a
    * default-locale fold would make the command palette come up empty for any query containing `i` — the same trap
    * `KeyBindings.keyCodeFor` guards against.
    */
  test("matching folds case the same way in every locale"):
    val original = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("tr"))
      assert(Fuzzy.matcher("i")("Install"))
      assert(Fuzzy.matcher("I")("install"))
      assert(Fuzzy.matcher("ins")("Install-Service"))
    finally Locale.setDefault(original)
