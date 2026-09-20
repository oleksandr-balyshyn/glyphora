package io.worxbend.tui.core

/** The bitset arithmetic shared by [[Modifiers]] and [[KeyModifiers]].
  *
  * Both are opaque types over `Int` carrying the same small algebra — union, intersection, any/all tests, clearing
  * flags, and rendering a set against a table of flag names — so that algebra is written once here, over plain `Int`,
  * and each companion's extension methods forward to it. The flag-name table stays with each companion: which flags
  * exist is what distinguishes the two types, only the mechanics are shared.
  *
  * A shared *public* abstraction over the opaque types themselves was considered and rejected: an upper-bound trait
  * would return the trait type — not the concrete opaque type — from `|` and `without`, breaking every call that hands
  * the result on as a `Modifiers`; and the `dsl` module re-exports both types as alias pairs precisely because an
  * exported opaque type loses its companion's extension methods, so any design that moves those extensions off the
  * companions would break that re-export. A private kernel keeps both public surfaces exactly as published.
  */
private[core] object FlagBits:

  /** The flags set in either bitset — set union. */
  def union(flags: Int, other: Int): Int = flags | other

  /** The flags set in both bitsets — set intersection. */
  def intersect(flags: Int, other: Int): Int = flags & other

  /** Whether any flag of `flag` is set in `flags`. */
  def hasAny(flags: Int, flag: Int): Boolean = (flags & flag) != 0

  /** Whether every flag of `required` is set in `flags`. An empty `required` is always satisfied. */
  def hasAll(flags: Int, required: Int): Boolean = (flags & required) == required

  def isEmpty(flags: Int): Boolean = flags == 0

  /** `flags` with every flag in `cleared` removed. */
  def without(flags: Int, cleared: Int): Int = flags & ~cleared

  /** The names of the flags set in `flags`, in the order `named` declares them; empty when nothing is set. */
  def names(flags: Int, named: Seq[(Int, String)]): Seq[String] =
    named.collect { case (flag, name) if hasAny(flags, flag) => name }

  /** The set flags as `"Bold|Italic"`, or `"None"` when nothing is set. */
  def show(flags: Int, named: Seq[(Int, String)]): String =
    if isEmpty(flags) then "None" else names(flags, named).mkString("|")
