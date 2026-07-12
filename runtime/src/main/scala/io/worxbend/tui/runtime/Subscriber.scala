package io.worxbend.tui.runtime

/** Something that depends on reactive values and must be told when one of them changes.
  *
  * `markStale` is a dirty flag, not a recomputation trigger — recomputation happens lazily on the next read
  * (SPEC.md §4.1).
  */
private[runtime] trait Subscriber:
  def markStale(): Unit

/** A reactive value that subscribers can attach to. The counterpart of [[Subscriber]]: `Computed` implements
  * both (it subscribes to its dependencies and is subscribed to by its dependents).
  */
private[runtime] trait Subscribable:
  private[runtime] def subscribe(subscriber: Subscriber): Unit
  private[runtime] def unsubscribe(subscriber: Subscriber): Unit
