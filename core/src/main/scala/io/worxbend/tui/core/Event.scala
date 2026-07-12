package io.worxbend.tui.core

/** Everything a running application can receive from its event source.
  *
  * `Key`/`Mouse`/`Resize` originate from the terminal backend; `Tick` is synthetic, injected by the runtime's runner at
  * its configured tick rate — it lives in this ADT because consumers pattern-match all four together.
  */
enum Event:
  case Key(event: KeyEvent)
  case Mouse(event: MouseEvent)
  case Resize(size: Size)
  case Tick
