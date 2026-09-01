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

  /** A bracketed paste: the whole pasted text arrives as one event instead of a storm of key events. */
  case Paste(text: String)

  /** A key came back *up*.
    *
    * Only ever delivered when the application asked for it (`RunnerConfig.keyEventTypes`) *and* the terminal speaks the
    * kitty keyboard protocol's "report event types" flag. Every other terminal reports presses only, so an application
    * that acts solely on releases does nothing at all there — treat a release as an enrichment, never as the event a
    * feature depends on.
    *
    * There is deliberately no `KeyRepeat`. A held key on a legacy terminal already arrives as a stream of ordinary
    * presses, so reporting kitty's auto-repeat as anything else would make held-arrow scrolling behave differently
    * depending on which terminal the app happened to start in. A repeat is a press.
    */
  case KeyRelease(event: KeyEvent)

  /** The terminal window gained or lost focus (mode 1004 reporting). */
  case FocusGained, FocusLost

  /** An interrupt signal (SIGINT/SIGQUIT — typically `Ctrl+C`) reached the process.
    *
    * Delivered as an ordinary event rather than killing the JVM, so the runner unwinds through its normal teardown and
    * the terminal is restored. An application that does not consume it quits cleanly.
    */
  case Interrupt
