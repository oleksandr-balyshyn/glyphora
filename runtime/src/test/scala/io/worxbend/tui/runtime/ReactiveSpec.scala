package io.worxbend.tui.runtime

import org.scalatest.funsuite.AnyFunSuite

/** Coverage categories replicated from Terminus's signals suite: conditional dependencies, unsubscribe-on-recompute,
  * and `peek` vs `get` semantics — as originally written tests, not ports.
  */
final class ReactiveSpec extends AnyFunSuite:

  test("peek reads a signal without any scope"):
    assert(Signal(42).peek == 42)

  test("set changes the value; update transforms it"):
    val signal = Signal(1)
    signal.set(5)
    assert(signal.peek == 5)
    signal.update(_ + 1)
    assert(signal.peek == 6)

  test("a computed evaluates lazily and caches until invalidated"):
    var runs    = 0
    val signal  = Signal(1)
    val doubled = Computed {
      runs += 1
      signal.get * 2
    }
    assert(runs == 0)
    assert(doubled.peek == 2)
    assert(doubled.peek == 2)
    assert(runs == 1)
    signal.set(3)
    assert(runs == 1) // set marks stale, does not recompute eagerly
    assert(doubled.peek == 6)
    assert(runs == 2)

  test("setting an equal value notifies nobody"):
    var runs    = 0
    val signal  = Signal(1)
    val derived = Computed {
      runs += 1
      signal.get
    }
    assert(derived.peek == 1)
    signal.set(1)
    assert(derived.peek == 1)
    assert(runs == 1)

  test("staleness cascades through chained computeds"):
    val base    = Signal(1)
    val plusOne = Computed(base.get + 1)
    val doubled = Computed(plusOne.get * 2)
    assert(doubled.peek == 4)
    base.set(5)
    assert(doubled.peek == 12)

  test("conditional dependencies subscribe only the branch that ran"):
    var runs      = 0
    val condition = Signal(true)
    val whenTrue  = Signal("a")
    val whenFalse = Signal("b")
    val chosen    = Computed {
      runs += 1
      if condition.get then whenTrue.get else whenFalse.get
    }
    assert(chosen.peek == "a")
    assert(runs == 1)
    // the untaken branch must not invalidate
    whenFalse.set("b2")
    assert(chosen.peek == "a")
    assert(runs == 1)

  test("recomputation unsubscribes from dependencies the new run did not read"):
    var runs      = 0
    val condition = Signal(true)
    val whenTrue  = Signal("a")
    val whenFalse = Signal("b")
    val chosen    = Computed {
      runs += 1
      if condition.get then whenTrue.get else whenFalse.get
    }
    assert(chosen.peek == "a")
    condition.set(false)
    assert(chosen.peek == "b")
    assert(runs == 2)
    // whenTrue was read by run 1 but not run 2 — changing it must not invalidate anymore
    whenTrue.set("a2")
    assert(chosen.peek == "b")
    assert(runs == 2)

  test("peek inside a computed does not subscribe"):
    var runs      = 0
    val tracked   = Signal(1)
    val untracked = Signal(10)
    val sum       = Computed {
      runs += 1
      tracked.get + untracked.peek
    }
    assert(sum.peek == 11)
    untracked.set(20)
    assert(sum.peek == 11) // stale? no — peeked dependency doesn't invalidate
    assert(runs == 1)
    tracked.set(2)
    assert(sum.peek == 22) // recompute picks up the newer peeked value too
    assert(runs == 2)

  test("map derives a reactive that follows the source"):
    val signal  = Signal(2)
    val squared = signal.map(n => n * n)
    assert(squared.peek == 4)
    signal.set(3)
    assert(squared.peek == 9)

  test("a root invalidation scope fires its callback when any read value changes"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val signal        = Signal(1)
    val _             = signal.get(using scope)
    signal.set(2)
    assert(invalidations == 1)

  test("reads through the untracked scope subscribe nothing"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val tracked       = Signal(1)
    val untracked     = Signal(1)
    val _             = tracked.get(using scope)
    val _             = untracked.get(using ReactiveScope.untracked)
    untracked.set(2)
    assert(invalidations == 0)
    tracked.set(2)
    assert(invalidations == 1)
