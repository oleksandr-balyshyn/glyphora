package io.worxbend.tui.dsl

/** The one fuzzy-matching rule the DSL filters with: a candidate matches when every character of the query appears in
  * it, in order, not necessarily adjacent, ignoring case. Shared by the command palette and the `autocomplete` element
  * so both stay consistent as the rule evolves.
  */
private[dsl] object Fuzzy:

  /** A predicate accepting the candidates that match `query` (an empty query accepts everything). The query is folded
    * once, so the returned predicate is cheap to apply across a list of candidates.
    */
  def matcher(query: String): String => Boolean =
    val needle = query.toLowerCase
    candidate =>
      var matched = 0
      candidate.toLowerCase.foreach(c => if matched < needle.length && needle.charAt(matched) == c then matched += 1)
      matched == needle.length
