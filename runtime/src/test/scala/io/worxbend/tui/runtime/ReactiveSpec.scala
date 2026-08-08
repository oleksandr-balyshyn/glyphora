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

  test("dispose detaches a computed from its dependencies"):
    var runs    = 0
    val signal  = Signal(1)
    val doubled = Computed {
      runs += 1
      signal.get * 2
    }
    assert(doubled.peek == 2)
    doubled.dispose()
    signal.set(5) // must not mark the disposed computed's (cleared) subscribers, nor leak into it
    assert(runs == 1)

  test("a computed that clamps its own index while recomputing does not cache the pre-clamp value"):
    var redraws = 0
    val scope   = ReactiveScope.generational(() => redraws += 1)
    val count   = Signal(3)
    val index   = Signal(2)
    val label   = Computed {
      val i = index.get
      if i >= count.get then index.set(0) // the ordinary "clamp the selection" repair-after-read pattern
      s"item $i of ${count.peek}"
    }

    assert(label.get(using scope) == "item 2 of 3")
    count.set(1) // the list shrank under the selection
    assert(redraws == 1)

    // this evaluation repairs `index` from inside the thunk, so the string it produces is already out of date
    assert(label.get(using scope) == "item 2 of 1")
    assert(index.peek == 0)
    assert(redraws == 2, "the write from inside the thunk must schedule another redraw")

    assert(label.get(using scope) == "item 0 of 1")
    assert(label.get(using scope) == "item 0 of 1")
    assert(redraws == 2, "the repaired value is stable — no invalidation loop")

  test("a dependency written from inside a computed's own thunk leaves it stale, not falsely fresh"):
    var runs    = 0
    val trigger = Signal(0)
    val patched = Signal("before")
    val derived = Computed {
      runs += 1
      val _       = trigger.get
      val current = patched.get
      if current == "before" then patched.set("after")
      current
    }
    assert(derived.peek == "before")
    assert(derived.peek == "after")
    assert(derived.peek == "after")
    assert(runs == 2)

  test("disposing a mid-chain computed does not freeze the computeds derived from it"):
    val source = Signal(1)
    val middle = Computed(source.get * 2)
    val top    = Computed(middle.get + 1)
    assert(top.peek == 3)

    middle.dispose()
    source.set(5)
    assert(top.peek == 11, "a dependent must re-derive through the disposed computed, not keep its stale cache")
    assert(middle.peek == 10)

    // reading through it re-established the edges, so ordinary propagation continues
    source.set(7)
    assert(top.peek == 15)

  test("a signal holding 0.0 notices a set to -0.0, because the sign carries direction"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val velocity      = Signal(0.0)
    val _             = velocity.get(using scope)
    velocity.set(-0.0)
    assert(invalidations == 1)
    assert(1.0 / velocity.peek == Double.NegativeInfinity)

  test("a signal holding NaN does not repaint on every rewrite of NaN"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val ratio         = Signal(Double.NaN)
    val _             = ratio.get(using scope)
    ratio.set(Double.NaN)
    ratio.set(Double.NaN)
    assert(invalidations == 0)
    ratio.set(0.5)
    assert(invalidations == 1)

  test("mutating a held value in place and setting the same instance notifies nobody"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val rows          = scala.collection.mutable.ArrayBuffer("a")
    val signal        = Signal(rows)
    val _             = signal.get(using scope)
    rows += "b"
    signal.set(rows) // the same instance is equal to itself: change detection cannot see the mutation
    assert(invalidations == 0)
    signal.set(scala.collection.mutable.ArrayBuffer("a", "b", "c"))
    assert(invalidations == 1)

  test("a generational scope drops subscriptions that stop being read"):
    var invalidations = 0
    val scope         = ReactiveScope.generational(() => invalidations += 1)
    val hot           = Signal(1)
    val cold          = Signal(1)

    def evaluate(readCold: Boolean): Unit =
      scope.beginGeneration()
      val _ = hot.get(using scope)
      if readCold then
        val _ = cold.get(using scope)

    evaluate(readCold = true)
    evaluate(readCold = false)
    evaluate(readCold = false) // cold read two generations ago is pruned here
    cold.set(2)
    assert(invalidations == 0)
    hot.set(2)
    assert(invalidations == 1)

  test("a mapped value subscribes nothing to its source"):
    val signal  = Signal(2)
    val squared = signal.map(n => n * n)
    assert(signal.subscriberCount == 0)
    assert(squared.peek == 4)
    assert(signal.subscriberCount == 0, "an untracked read of a derived value must leave no edge behind")
    signal.set(3)
    assert(squared.peek == 9)
    assert(signal.subscriberCount == 0)

  test("deriving a mapped value once per generation does not grow the source's subscriber set"):
    var invalidations = 0
    val scope         = ReactiveScope.generational(() => invalidations += 1)
    val count         = Signal(0)
    var lastLabel     = ""

    for generation <- 1 to 100 do
      scope.beginGeneration()
      lastLabel = count.map(n => s"$n items").get(using scope)
      count.set(generation)

    assert(lastLabel == "99 items")
    assert(
      count.subscriberCount == 1,
      "only the scope's own subscriber may remain — before the fix this grew by one per generation",
    )
    assert(invalidations == 100)

  test("repeatedly mapping and reading a signal leaves nothing to dispose"):
    val signal = Signal(1)
    for _ <- 1 to 1000 do assert(signal.map(_ + 1).peek == 2)
    assert(signal.subscriberCount == 0)

  test("disposing a computed removes it from its source's subscriber set"):
    val signal  = Signal(1)
    val doubled = Computed(signal.get * 2)
    assert(doubled.peek == 2)
    assert(signal.subscriberCount == 1)
    doubled.dispose()
    assert(signal.subscriberCount == 0)

  test("chained derivations stay transparent to the reading scope"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val signal        = Signal(2)
    val label         = signal.map(_ * 2).map(n => s"= $n")
    val other         = signal.map(_ + 1).map(n => s"+ $n")
    assert(label.get(using scope) == "= 4")
    assert(other.get(using scope) == "+ 3")
    // both chains passed the caller's scope through to the signal, so the scope's own subscriber is all there is
    assert(signal.subscriberCount == 1, "a chain of derivations must contribute no subscriber of its own")
    signal.set(3)
    assert(invalidations == 1)
    assert(label.peek == "= 6")
    assert(other.peek == "+ 4")

  test("an equal-value set notifies nobody through a derived value"):
    var invalidations = 0
    val scope         = ReactiveScope.onInvalidation(() => invalidations += 1)
    val signal        = Signal(1)
    val label         = signal.map(n => s"n=$n")
    assert(label.get(using scope) == "n=1")
    signal.set(1)
    assert(invalidations == 0)
    signal.set(2)
    assert(invalidations == 1)
