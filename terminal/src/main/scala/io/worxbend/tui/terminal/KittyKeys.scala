package io.worxbend.tui.terminal

import io.worxbend.tui.core.{KeyCode, MediaKey, ModifierKey}

/** The kitty keyboard protocol's key vocabulary, mapped onto glyphora's [[KeyCode]] vocabulary.
  *
  * This is a lookup table and nothing else: it reads no input, holds no state, and is safe to call from any thread. It
  * lives apart from [[InputDecoder]] because it is the one part of the decoder that has to be checked line by line
  * against an external specification, and a reader doing that should not have to walk past the escape-sequence scanner
  * to reach it.
  *
  * Code points in the Private Use Area 57344-63743 are the protocol's *functional* keys, not text — reporting one as a
  * character would insert a garbage glyph into whatever text input has focus.
  */
private[terminal] object KittyKeys:

  /** The key a kitty `CSI codepoint ; modifiers u` sequence names, or `None` when glyphora has no name for it. */
  def keyCode(codePoint: Int): Option[KeyCode] =
    codePoint match
      case 27                                                      => Some(KeyCode.Escape)
      case 13                                                      => Some(KeyCode.Enter)
      case 9                                                       => Some(KeyCode.Tab)
      case 127                                                     => Some(KeyCode.Backspace)
      case cp if cp >= FunctionalKeyLow && cp <= FunctionalKeyHigh => functionalKey(cp)
      case cp if cp >= FirstPrintable && isTextCodePoint(cp)       => Some(KeyCode.Char(cp))
      case _                                                       => None

  /** The functional-key block.
    *
    * Keypad keys report as their non-keypad equivalents (`KP_7` is `Home`, `KP_ENTER` is `Enter`, `KP_3` is `3`) —
    * glyphora has no separate keypad concept and an application almost never wants one.
    *
    * The lock and system keys (`CAPS_LOCK` through `MENU`) *are* reported, as the [[KeyCode]] cases of the same names.
    * A lock key reports the press itself, not the state it leaves behind — glyphora never claims to know whether Caps
    * Lock is currently on. So are the media keys, as `KeyCode.Media`. The modifier-only keys — a bare Shift press, with
    * no other key involved — decode to `KeyCode.Modifier`, and reach an application only on a terminal that has been
    * asked to report every key; on any other terminal the sequence is never sent in the first place. What is still
    * dropped is the unassigned code points.
    */
  private def functionalKey(codePoint: Int): Option[KeyCode] =
    codePoint match
      case CapsLock                                                   => Some(KeyCode.CapsLock)
      case ScrollLock                                                 => Some(KeyCode.ScrollLock)
      case NumLock                                                    => Some(KeyCode.NumLock)
      case PrintScreen                                                => Some(KeyCode.PrintScreen)
      case PauseKey                                                   => Some(KeyCode.Pause)
      case MenuKey                                                    => Some(KeyCode.Menu)
      case cp if cp >= F13 && cp <= F13 + (LastNamedFunctionKey - 13) =>
        Some(KeyCode.F(cp - F13 + 13))
      case cp if cp >= Keypad0 && cp <= Keypad0 + 9                   =>
        Some(KeyCode.Char('0' + (cp - Keypad0)))
      case KeypadDecimal                                              => Some(KeyCode.Char('.'))
      case KeypadDivide                                               => Some(KeyCode.Char('/'))
      case KeypadMultiply                                             => Some(KeyCode.Char('*'))
      case KeypadSubtract                                             => Some(KeyCode.Char('-'))
      case KeypadAdd                                                  => Some(KeyCode.Char('+'))
      case KeypadEnter                                                => Some(KeyCode.Enter)
      case KeypadEqual                                                => Some(KeyCode.Char('='))
      case KeypadSeparator                                            => Some(KeyCode.Char(','))
      case KeypadLeft                                                 => Some(KeyCode.Left)
      case KeypadRight                                                => Some(KeyCode.Right)
      case KeypadUp                                                   => Some(KeyCode.Up)
      case KeypadDown                                                 => Some(KeyCode.Down)
      case KeypadPageUp                                               => Some(KeyCode.PageUp)
      case KeypadPageDown                                             => Some(KeyCode.PageDown)
      case KeypadHome                                                 => Some(KeyCode.Home)
      case KeypadEnd                                                  => Some(KeyCode.End)
      case KeypadInsert                                               => Some(KeyCode.Insert)
      case KeypadDelete                                               => Some(KeyCode.Delete)
      case cp if MediaKeys.contains(cp)                               => MediaKeys.get(cp).map(KeyCode.Media.apply)
      case cp if cp >= LeftShiftKey && cp <= IsoLevel5ShiftKey        =>
        modifierKey(cp).map(KeyCode.Modifier.apply)
      case _                                                          => None // unassigned

  /** The modifier-only block, in the code-point order the kitty keyboard-protocol specification lists it.
    *
    * Written out one code point at a time rather than indexed into a sequence so that a reader checking this against
    * the specification can compare it line by line.
    */
  private def modifierKey(codePoint: Int): Option[ModifierKey] =
    codePoint match
      case 57441 => Some(ModifierKey.LeftShift)
      case 57442 => Some(ModifierKey.LeftControl)
      case 57443 => Some(ModifierKey.LeftAlt)
      case 57444 => Some(ModifierKey.LeftSuper)
      case 57445 => Some(ModifierKey.LeftHyper)
      case 57446 => Some(ModifierKey.LeftMeta)
      case 57447 => Some(ModifierKey.RightShift)
      case 57448 => Some(ModifierKey.RightControl)
      case 57449 => Some(ModifierKey.RightAlt)
      case 57450 => Some(ModifierKey.RightSuper)
      case 57451 => Some(ModifierKey.RightHyper)
      case 57452 => Some(ModifierKey.RightMeta)
      case 57453 => Some(ModifierKey.IsoLevel3Shift)
      case 57454 => Some(ModifierKey.IsoLevel5Shift)
      case _     => None

  /** Whether `codePoint` is a character a string can actually hold.
    *
    * `Character.isValidCodePoint` only bounds by 0x10FFFF, so on its own it admits the surrogate range — and a lone
    * surrogate corrupts every string it is appended to, which is exactly what the decoder's surrogate pairing exists to
    * prevent.
    */
  private def isTextCodePoint(codePoint: Int): Boolean =
    Character.isValidCodePoint(codePoint) && !(codePoint >= SurrogateLow && codePoint <= SurrogateHigh)

  // the functional-key block, by the names the kitty keyboard-protocol spec gives these code points
  private val FunctionalKeyLow     = 57344
  private val FunctionalKeyHigh    = 63743
  private val CapsLock             = 57358
  private val ScrollLock           = 57359
  private val NumLock              = 57360
  private val PrintScreen          = 57361
  // `PauseKey`/`MenuKey` rather than `Pause`/`Menu`: the bare names would read as the KeyCode cases beside them
  private val PauseKey             = 57362
  private val MenuKey              = 57363
  private val F13                  = 57376 // F13; the block runs to F35 at 57398
  private val LastNamedFunctionKey = 35
  private val Keypad0              = 57399 // KP_0; the block runs to KP_9 at 57408
  private val KeypadDecimal        = 57409
  private val KeypadDivide         = 57410
  private val KeypadMultiply       = 57411
  private val KeypadSubtract       = 57412
  private val KeypadAdd            = 57413
  private val KeypadEnter          = 57414
  private val KeypadEqual          = 57415
  private val KeypadSeparator      = 57416
  private val KeypadLeft           = 57417
  private val KeypadRight          = 57418
  private val KeypadUp             = 57419
  private val KeypadDown           = 57420
  private val KeypadPageUp         = 57421
  private val KeypadPageDown       = 57422
  private val KeypadHome           = 57423
  private val KeypadEnd            = 57424
  private val KeypadInsert         = 57425
  private val KeypadDelete         = 57426
  private val LeftShiftKey         = 57441 // LEFT_SHIFT; the modifier-only block runs to ISO_LEVEL5_SHIFT
  private val IsoLevel5ShiftKey    = 57454

  /** The media block, 57428-57440, by the names the kitty keyboard-protocol spec gives these code points. */
  private val MediaKeys: Map[Int, MediaKey] = Map(
    57428 -> MediaKey.Play,
    57429 -> MediaKey.Pause,
    57430 -> MediaKey.PlayPause,
    57431 -> MediaKey.Reverse,
    57432 -> MediaKey.Stop,
    57433 -> MediaKey.FastForward,
    57434 -> MediaKey.Rewind,
    57435 -> MediaKey.TrackNext,
    57436 -> MediaKey.TrackPrevious,
    57437 -> MediaKey.Record,
    57438 -> MediaKey.LowerVolume,
    57439 -> MediaKey.RaiseVolume,
    57440 -> MediaKey.MuteVolume,
  )

  /** The first code point that is text rather than a C0 control; below it the byte is a control the decoder names. */
  private val FirstPrintable = 32

  private val SurrogateLow  = 0xd800
  private val SurrogateHigh = 0xdfff
