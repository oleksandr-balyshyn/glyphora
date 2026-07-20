# ratatui → glyphora: Feature Inventory & Gap Analysis

Comparison of Rust **ratatui** (`ratatui-core`, `ratatui-widgets`) against Scala 3 **glyphora**
(`io.worxbend.tui.core`, `io.worxbend.tui.widgets`). All ratatui claims cite Rust source
file names; all glyphora claims cite the Scala source read directly.

---

## 1. Core model

### 1.1 Cell / Buffer

| Capability | ratatui (`buffer/cell.rs`, `buffer/buffer.rs`, `buffer/diff.rs`, `buffer/cell_width.rs`) | glyphora (`Cell.scala`, `Buffer.scala`, `CharWidth.scala`) | Gap |
|---|---|---|---|
| Cell symbol | `Option<CompactString>` grapheme cluster, small-string-optimized | `Cell(symbol: String, style: Style)` case class | Parity (glyphora simpler; boxes the String) |
| Cell colors | separate `fg`, `bg`, **`underline_color`** (feature-gated) | fg/bg carried inside `Style`; **no underline color** | Minor gap: no distinct underline color |
| Cell diff control | `CellDiffOption { None, Skip, AlwaysUpdate, ForcedWidth(NonZeroU16) }` | none | Gap: no per-cell skip / forced-width / always-update. Relevant for image protocols, hyperlinks, escape-graphics overdraw |
| Border merging | `Cell::merge_symbol` + `MergeStrategy {Replace, Exact, Fuzzy}` (`symbols/merge.rs`) | none — later writes overwrite | Gap: adjacent Blocks can't merge box-drawing joints |
| Buffer writes | `set_string/set_stringn/set_line/set_span/set_style`, `resize`, `merge`, `filled`, `with_lines` | `setString`, `set`, `get`, `blit`, `blit(region)`, `snapshot`, `reset` | glyphora lacks `set_line/set_span/set_style(area)`; **but has `blit` (offscreen compositing)** which ratatui lacks as a first-class op |
| Diff | `diff` (Vec) + `diff_iter` (zero-alloc `BufferDiff`), sophisticated wide-char / VS16 / "visible-on-blank" trailing-cell logic | `diff` returns `Iterator[(Position, Cell)]`; emit-all on area mismatch; skips continuation cells | glyphora's diff is correct & wide-aware but simpler — no VS16 re-emit heuristics, no forced trailing refresh |
| Width arithmetic | `unicode-width` crate + dakuten `+1` compensation (`cell_width.rs`) | **`CharWidth.scala`: full hand-rolled UCD width** — East-Asian Wide/Fullwidth table, combining marks, ZWJ emoji sequences, regional-indicator flags, VS15/VS16, conjoining Hangul jamo, emoji modifiers | **Near-parity, arguably a glyphora strength** (self-contained, no crate dep) |

Notes: glyphora's `Buffer` is a class holding `Array[Cell]`; ratatui's is `Vec<Cell>` with public `content`. glyphora coordinates are absolute (matches ratatui). glyphora's `blit` deliberately blanks half-cut wide graphemes at window edges — a nicety ratatui handles differently.

### 1.2 Style / Color / Modifiers

| Capability | ratatui (`style.rs`, `style/color.rs`, `style/stylize.rs`) | glyphora (`Style.scala`, `Color.scala`, `Modifiers.scala`) | Gap |
|---|---|---|---|
| Modifiers | 9 flags: BOLD, DIM, ITALIC, UNDERLINED, SLOW_BLINK, RAPID_BLINK, REVERSED, HIDDEN, CROSSED_OUT | 8 flags: Bold, Dim, Italic, Underline, **Blink** (single), Reverse, Hidden, CrossedOut (`opaque type Modifiers = Int`) | Minor: glyphora collapses slow/rapid blink into one `Blink` |
| add vs sub modifier | Style has BOTH `add_modifier` and `sub_modifier` (a patch can *remove* bold) | only additive union in `patch` (`modifiers | other.modifiers`) | Gap: glyphora cannot express "un-bold" in a patch; `not_bold()` equivalents impossible |
| Color spaces | Reset + **16 named** (8 + 8 light/dark: Gray, DarkGray, LightRed…) + `Rgb(u8³)` + `Indexed(u8)` | Reset + **8 named** (Black…White) + `Rgb(Int³)` + `Indexed(Int)` | Gap: glyphora has only 8 named colors, not the 16 ANSI bright variants |
| Color parsing | `FromStr` (named / `#RRGGBB` hex / decimal index), `from_u32`, `from_hsl`, `from_hsluv`, `From<[u8;3]>` etc. | only `approximateRgb` (Color→RGB downsample) | Gap: no string/hex parsing, no HSL |
| Palettes | `style/palette/tailwind.rs` (22 palettes × 11 shades), `material.rs` (Material Design) | none | Gap: no bundled palettes |
| Stylize ergonomics | `Stylize`/`Styled` traits: `"x".red().on_blue().bold()` on str, String, numbers | builder methods on `Style` only (`.bold`, `.withFg`) | Gap: no fluent stylize on raw strings |
| underline color | yes (feature) | no | Minor gap |
| Hyperlink | via `Cell` escape / not in core Style | **`Style.link: Option[String]` (OSC 8)** | **glyphora better**: hyperlinks are a first-class Style field |

### 1.3 Text / Line / Span

| Capability | ratatui (`text/span.rs`, `line.rs`, `text.rs`, `grapheme.rs`, `masked.rs`) | glyphora (`Span.scala`, `Line.scala`, `Text.scala`) | Gap |
|---|---|---|---|
| Span | `{style, content: Cow<str>}`, `styled_graphemes`, alignment converters, `Add`, `Widget`, `Display` | `Span(content, style)`, `width` | glyphora minimal — no styled_graphemes, no operators |
| Line | `{style, alignment: Option<Alignment>, spans}`, `centered/left/right`, `patch_style`, `push_span`, `Add`, iterators | `Line(spans)` only — **no per-line style, no alignment field** | Gap: alignment/style live only at render sites, not on the Line |
| Text | `{alignment, style, lines}`, `centered/…`, `push_line`, `From<many>`, `Add`, iterators | `Text(lines)` + `raw`/`styled` | Gap: no Text-level alignment/style; alignment is a `Paragraph` param instead |
| StyledGrapheme | dedicated render unit | folded into `CharWidth.graphemeClusters` | parity via different mechanism |
| Masked | `Masked{inner, mask_char}` password display | none in core (TextInput has its own logic) | Minor gap: no reusable `Masked` text |

### 1.4 Symbols

ratatui ships a rich `symbols/*` catalogue: `bar` (8 vertical eighths + 3/9-level sets),
`block` (8 horizontal eighths), `border` (**18 border sets** incl. dashed ×6, QUADRANT_INSIDE/OUTSIDE,
proportional, one-eighth), `line` (normal/rounded/double/thick + dashed sets, tees, cross), `braille`
(256-entry table), `half_block`, `shade` (░▒▓█), `scrollbar` (4 sets w/ begin/end arrows),
`pixel` (QUADRANTS 16 / SEXTANTS 64 / OCTANTS 256 pseudo-pixel tables), `marker`
(`Dot/Block/Bar/Braille/HalfBlock/Quadrant/Sextant/Octant/Custom`), and `merge` (box-drawing joiner).

glyphora has **no shared symbols module**; each widget hard-codes its glyphs (Block border glyphs inline,
BarChart/Sparkline eighth-blocks inline, Canvas braille/half-block inline). See gap list §5.

---

## 2. Layout / constraint solver

This is the most consequential difference.

**ratatui** (`layout/layout.rs`, `constraint.rs`, `flex.rs`, `margin.rs`) uses **kasuari** (a renamed
Cassowary linear solver). Key facts:

- Constraints: `Min, Max, Length, Percentage, Ratio(u32,u32), Fill(u16)`; documented priority order
  Min > Max > Length > Percentage > Ratio > Fill.
- Each constraint is encoded as kasuari constraints at explicit strengths (`layout.rs mod strengths`):
  `MIN_SIZE_GE = MAX_SIZE_LE = 1e8`, `LENGTH_SIZE_EQ = 1e7`, `PERCENTAGE_SIZE_EQ = 1e6`,
  `RATIO_SIZE_EQ = 1e5`, `FILL_GROW = 1e3`, down to `ALL_SEGMENT_GROW = 1` (equalize leftover).
  `SPACER_SIZE_EQ = 1.001e8` even outranks Min/Max so spacer symmetry (centering) dominates.
- **Fill(weight)** proportionality is enforced pairwise (`configure_fill_constraints`): `Fill(a):Fill(b)`
  holds ratio `a:b`; `Fill(0) → 1e-6`; a non-Legacy `Min` behaves like `Fill(1)` and grows.
- **Flex** modes (`Flex { Legacy, Start(default), End, Center, SpaceBetween, SpaceEvenly, SpaceAround }`)
  are implemented via **spacer variables** (there are `segments+1` spacers): the solver grows leading/
  trailing/interior spacers per mode. `split_with_spacers` returns both segments and spacers.
- **Spacing** = `Spacing { Space(u16), Overlap(u16) }`; `From<i16>` maps negatives to overlap
  (negative spacing → segments overlap). Flattened to a signed i16 in the solve.
- **Margin** = `{horizontal, vertical}` distinct axes; applied via `Rect::inner(margin)` before solving.
- Percentage/Ratio are relative to the **whole** area, not remaining space.
- Values computed ×100 then double-rounded to u16; results cached in a thread-local LRU
  (`DEFAULT_CACHE_SIZE = 500`, keyed on `(Rect, Layout)`).

**glyphora** (`Layout.scala`, `Constraint.scala`) uses a **custom two-pass integer solver** (explicitly
*not* Cassowary):

- Constraints: `Length, Percentage, Ratio, Min, Max, Fill(weight=1)` — same six names.
- Pass 1 "fixed demand": `Length→cells`, `Percentage→available*pct/100`, `Ratio→available*num/den`,
  `Min→its floor`, `Max→0`, `Fill→0`.
- Pass 2 "leftover": distributed by weight to `Fill(weight)`, and to `Min`/`Max` with **weight 1**,
  `Max` capped; capped residue re-distributed until stable. Largest-remainder integer rounding,
  ties to earliest index (deterministic).
- Overflow: trailing segments truncated to zero rather than erroring.
- `spacing: Int` — a single non-negative uniform gap between segments.
- Sugar: `Layout.horizontal/vertical(Int | Double | Constraint*)` where `Int→Length`, `Double→Percentage`.

**Fidelity assessment — where the semantics diverge:**

| Feature | ratatui | glyphora | Match? |
|---|---|---|---|
| Fill weight proportionality | exact ratio via solver | weight-proportional integer share | **Close** — same intent, minor rounding differences |
| Constraint priority ladder | strict strength ordering (Min/Max beat Length beat %…) | flat two-pass; Min floor + Length/%/Ratio all treated as "fixed demand" summed together | **Diverges**: no priority arbitration when demands conflict/overflow — glyphora just truncates trailing segments |
| Min competing for leftover | Min grows like Fill(1) (non-Legacy) | Min gets weight 1 in leftover pass | **Close** |
| Max as cap | `<=` hard + `==` preference | cap in leftover redistribution; **Max contributes 0 fixed demand** | **Partial**: `Max` alone in glyphora yields 0 unless leftover exists |
| **Flex modes** | 7 modes via spacers | **none** (only Start-like packing) | **Missing entirely** — no Center/SpaceBetween/SpaceAround/End/SpaceEvenly |
| **Spacers output** | `split_with_spacers` returns spacer rects | not returned | **Missing** |
| **Negative spacing / Overlap** | `Overlap(n)` supported | only non-negative `Int` | **Missing** |
| **Two-axis margin** | `{horizontal, vertical}` on Layout | Layout has **no margin**; only `Rect.inset(uniform)` | **Missing**: no per-axis margin, no margin on Layout at all |
| Percentage basis | whole area | whole area | Match |
| Caching | thread-local LRU | none (recompute each call) | glyphora recomputes (fine for small layouts) |

**Rect ops:** ratatui `Rect` (`rect.rs`, `rect/ops.rs`) has `inner/outer(margin)`, `union`,
`intersection`, `intersects`, `contains`, `clamp` (moves inside), `offset`, `resize`,
`rows/columns/positions` iterators, `centered*`, saturating u16 arithmetic. glyphora `Rect.scala` has
`area`, `right/bottom`, `intersection`, `contains`, `inset(uniform)`. **Missing**: `union`, `intersects`,
`clamp`, `outer`, per-axis inner, `rows/columns/positions` iterators, `offset`, `centered`.

---

## 3. Block

| Feature | ratatui (`block.rs`, `borders.rs`, `block/padding.rs`, `block/shadow.rs`) | glyphora (`Block.scala`, `Borders.scala`) | Gap |
|---|---|---|---|
| Borders bitset | `TOP/RIGHT/BOTTOM/LEFT/ALL/NONE` (`bitflags u8`) | identical `opaque type Borders = Int` | **Parity** |
| BorderType | **12**: Plain, Rounded, Double, Thick, 6× dashed, QuadrantInside, QuadrantOutside | **4**: Plain, Rounded, Double, Thick | Gap: no dashed / quadrant border sets |
| Custom border_set | `border_set(Set)` — arbitrary glyphs | none (fixed 4) | Gap |
| Titles | **multiple** titles; each a `Line`; `title_top`/`title_bottom`; per-title alignment (via `Line.alignment`) + block default; `title_style` | **single** `Option[Line]`; `titleAlignment`; top only | Gap: no multiple titles, no bottom titles, no per-title alignment, no separate title style |
| Title position | Top / Bottom | Top only | Gap |
| Padding | `Padding{left,right,top,bottom}` + `uniform/horizontal/vertical/proportional/symmetric/side` helpers | single uniform `padding: Int` | Gap: no per-side / proportional padding |
| border_style | yes | yes (`borderStyle`) | Parity |
| base `style` fill | `set_style(area, style)` before borders | no block-level background fill | Minor gap |
| Border merging | `merge_borders: MergeStrategy` | none | Gap (see §1.1) |
| **Shadow** | `Shadow{effect, style, offset}` — Overlay/Symbol/Custom, `Dimmed` effect | none | Gap |
| inner() | borders + titles(each side) + padding; title consumes a row even without border | borders + uniform padding | glyphora title does not reserve its own row separately (drawn on top border row) |

Architectural note: in glyphora, Block is a standalone wrapper and **no other widget embeds a Block**
(ratatui widgets nearly all have a `.block(Block)` builder). This is a deliberate glyphora design
(compose Block + content separately) but means titled/bordered widgets require manual `inner()` plumbing.

---

## 4. Widget-by-widget

Legend: ✅ present / faithful · ◑ partial · ❌ absent · ➕ glyphora-only extra

| ratatui widget & notable knobs | glyphora status | Gap |
|---|---|---|
| **Paragraph** (`paragraph.rs`, `reflow.rs`): block, style, `Wrap{trim}`, **word-wrap** (`WordWrapper`) vs truncate (`LineTruncator`), **scroll (x,y)**, alignment inherited from Text, `line_count`/`line_width` | `Paragraph.scala`: alignment, `wrap: Boolean`, style, `heightOf` | ◑ **grapheme-wrap only, no word wrap**; **no scroll offset**; no `trim`; no block; no per-line alignment |
| **List** (`list.rs`): block, items(Text), highlight_style, highlight_symbol(Line), `repeat_highlight_symbol`, `HighlightSpacing{Always,WhenSelected,Never}`, `ListDirection{TopToBottom,BottomToTop}`, `scroll_padding`, rich `ListState` (select_first/last, scroll_by) | `ListView.scala` (StatefulWidget): items(Line), highlightStyle, highlightSymbol(String), offset auto-follow, selectNext/Previous | ◑ no direction, no HighlightSpacing, no repeat symbol, no scroll_padding, no select_first/last, items are Line not Text (single-row) |
| **Table** (`table.rs`): block, header, **footer**, Constraint widths, column_spacing, **flex**, row/column/**cell** highlight styles, highlight_symbol, HighlightSpacing, `Row{height,top/bottom_margin,style}`, `Cell{column_span}`, full `TableState` (col + cell selection, scroll_by) | `Table.scala` (stateless): Constraint widths ✅, header ✅, columnSpacing ✅, headerStyle | ◑ no selection/highlight, no footer, no per-row height/margin, no cell/column highlight, no flex, no per-cell styling |
| ↳ (no ratatui equivalent) | `DataTable.scala` ➕: **sorting, filtering, pagination**, row selection | ➕ glyphora extra — but cells are plain `String` (no styled cells) |
| **Tabs** (`tabs.rs`): block, titles(Line), select(Option), style, highlight_style, divider(Span), **padding_left/right** | `Tabs.scala`: titles(Line), selected, style, highlightStyle, divider(String) | ◑ no per-tab padding, no block |
| **Chart** (`chart.rs`): block, `Axis{title,bounds,labels,labels_alignment,style}` ×2, datasets, `GraphType{Scatter,Line,Bar,Area}`, `Dataset{name,marker,fill_to_y}`, **legend** (`LegendPosition` 8-way + `hidden_legend_constraints`) | `Chart.scala`: datasets, x/y bounds, `GraphType{Line,Scatter}`, marker, resolution, `showLabels` (y min/max only) | ◑ no Bar/Area graph types, **no legend** (name stored, unused), no axis titles/labels (only y-min/max), no per-axis style, no block |
| **BarChart** (`barchart.rs`): block, bar_width, bar_gap, bar_set, bar/value/label_style, **group_gap + BarGroup (grouped bars)**, max, **Direction (H/V)**, `Bar{value,label,text_value,value_style}` | `BarChart.scala`: data `(String,Long)`, barWidth, barGap, max, barStyle, labelStyle, eighth-block sub-cell | ◑ vertical-only, **no grouping**, no value labels on bars, no per-bar style, no bar_set, no block |
| ↳ | `DualSparkline.scala`, `VizExtras.scala` (`PieChart`, `StackedBarChart`, `Heatmap`) ➕ | ➕ glyphora extras (StackedBarChart ≈ ratatui grouped/stacked; PieChart & Heatmap novel) |
| **Sparkline** (`sparkline.rs`): block, max, `RenderDirection{LtR,RtL}`, bar_set, **absent values** (`Option<u64>`, absent_value_style/symbol), per-bar style (`SparklineBar`) | `Sparkline.scala`: data(Long), max, style | ◑ no direction, no absent-value gaps, no per-bar style, no bar_set, no block |
| **Gauge** (`gauge.rs`): block, ratio/percent, label(Span), gauge_style, **use_unicode** (1/8-block smooth fill) | `Gauge.scala`: ratio, label, style, filledStyle, `Gauge.of(cur,total)` | ◑ **whole-cell fill only** (no unicode sub-cell), no block |
| **LineGauge** (`gauge.rs`): block, ratio, label(Line), `line_set`, filled/unfilled symbol **and** style | `LineGauge.scala`: ratio, label, filled/unfilled symbol, filledStyle | ◑ no separate unfilled_style, no block (symbols ≈ line_set) |
| **Scrollbar** (`scrollbar.rs`): `ScrollbarOrientation{VerticalRight/Left,HorizontalBottom/Top}`, thumb/track/**begin/end** symbols + styles, `ScrollbarState{content_length,position,viewport_content_length}` | `Scrollbar.scala` (StatefulWidget): orientation(Vertical/Horizontal), track/thumb symbol+style, `ScrollbarState{contentLength,position}` | ◑ **no begin/end arrows**, vertical always right / horizontal always bottom (no side choice), no viewport_content_length |
| **Canvas** (`canvas.rs` + shapes): x/y_bounds, marker (9 types), background_color, **paint closure + layers + labels**, Grids (Braille/Char/HalfBlock/Pattern/Quadrant/Sextant/Octant), shapes **Line(Bresenham+clip), FilledLine, Circle, Rectangle, Points, Map(Low/High)** | `Canvas.scala`: x/y bounds, shapes(Points, Segment, Polyline, Rectangle, Circle), marker(String), resolution(Cell/HalfBlock/Braille) | ◑ **no Map/world**, **no text Label shape**, no FilledLine (area fill), no paint-closure/layer API, no Marker enum (Quadrant/Sextant/Octant), line is param-stepped not Bresenham+clip, no block |
| **Calendar/Monthly** (`calendar.rs`): `CalendarEventStore` (arbitrary date→Style map), show_surrounding, show_weekdays_header, show_month_header (each toggleable), default_style, block; uses `time` crate; Sunday-first | `Calendar.scala`: year/month, single `selected` day-int, headerStyle, selectedStyle; Monday-first, English hardcoded | ◑ no per-date event map (only one selected day), headers always on, no surrounding days, hardcoded Monday/English, no block |
| **Clear** (`clear.rs`): reset cells in area | folded into `Dialog` (clears its box); **no standalone `Clear` widget** | ◑ no reusable Clear |
| **Fill** (`fill.rs`): fill area with symbol+style, Styled | ❌ (Rule/backgrounds ad hoc) | ❌ no Fill widget |
| **RatatuiLogo** (`logo.rs`), **RatatuiMascot** (`mascot.rs`) | ❌ (branding) | ❌ — glyphora has `BigText` ➕ (3×5 block font) instead |

**glyphora widgets with NO ratatui equivalent (➕ extras):**
`TextInput`, `TextArea` (undo/redo, grapheme cursor), `Button`, `Checkbox`, `Toggle`, `RadioGroup`,
`Slider`, `Paginator`, `Select`, `Dialog`, `Tree`, `DirectoryTree` (filesystem browser), `ScrollView`
(offscreen-blit scroll container), `Log` (bounded ring + follow), `Markdown` (headings/bullets/code/
links/OSC8), `BigText`, `WaveText`, `Marquee`, `Skeleton`, `IndeterminateBar`, `Spinner`, `Image`
(half-block, `fromFile` via ImageIO), `Link` (OSC 8), `PieChart`, `StackedBarChart`, `Heatmap`, `Rule`,
`Spacer`, `Row`/`Column` containers. This is a **substantially larger application-widget surface** than
ratatui ships (ratatui pushes forms/inputs to third-party crates like `tui-textarea`, `tui-tree-widget`).

---

## 5. Ranked gap list

### HIGH value
1. **Layout Flex modes** (Center / SpaceBetween / SpaceAround / SpaceEvenly / End) + **spacer output**
   (`split_with_spacers`). This is the single most-used ratatui layout ergonomic (centering dialogs,
   distributing toolbars). glyphora has none. (`flex.rs`, `layout.rs`)
2. **Layout margins (per-axis) on `Layout`** + **negative spacing / Overlap**. glyphora's `Layout` has
   no margin field and only non-negative uniform spacing. (`margin.rs`, `layout.rs Spacing`)
3. **Paragraph word-wrap + scroll offset.** Grapheme wrapping breaks mid-word; no vertical/horizontal
   scroll. Real text panes need `WordWrapper` + `Wrap{trim}` + `scroll(x,y)`. (`reflow.rs`)
4. **Table selection & highlighting** (row/column/cell highlight, highlight_symbol, footer, per-row
   height). glyphora's plain `Table` is stateless; `DataTable` adds selection but only over `String`
   cells. (`table.rs`)
5. **Chart legend + axis labels/titles + Bar/Area graph types.** Charts are near-unusable for
   presentation without axis tick labels and a legend. (`chart.rs`)
6. **16 ANSI colors + color string/hex parsing.** Only 8 named colors and no `Color.parse("#rrggbb")`.
   (`style/color.rs`)
7. **`sub_modifier` (removable modifiers) in Style patching.** Cannot express "remove bold" — bites
   theme/override systems. (`style.rs`)

### MEDIUM value
8. **Shared `symbols` module** (bar/block/line/border/braille/shade/scrollbar/marker sets). Centralizing
   glyphs enables custom border sets, dashed borders, scrollbar arrows, marker choice. (`symbols/*`)
9. **Block: multiple titles + bottom titles + per-side padding.** (`block.rs`, `padding.rs`)
10. **Gauge `use_unicode` sub-cell fill** and **Sparkline direction + absent values.** (`gauge.rs`, `sparkline.rs`)
11. **Scrollbar begin/end arrows + orientation side choice + viewport_content_length.** (`scrollbar.rs`)
12. **BarChart grouping (BarGroup) + horizontal direction + on-bar value labels.** (`barchart.rs`)
13. **Standalone `Clear` and `Fill` widgets** (popup blanking, backgrounds, tracks). (`clear.rs`, `fill.rs`)
14. **`Line`/`Text` alignment & style fields** (so alignment travels with the data, not just render calls). (`text/line.rs`)
15. **Rect ops**: `union`, `intersects`, `clamp`, per-axis `inner`, `rows/columns/positions` iterators,
    `centered`. Handy building blocks used all over ratatui widgets. (`rect.rs`)
16. **Canvas: world Map + text Label shape + FilledLine (area charts) + Marker enum.** (`canvas/*`)
17. **Calendar event-store (per-date styling), surrounding days, header toggles, week-start/locale.** (`calendar.rs`)

### LOW value
18. **Bundled palettes** (Tailwind / Material). (`style/palette/*`)
19. **Cell diff-options** (Skip / ForcedWidth / AlwaysUpdate) — matters only for image/hyperlink escape
    overdraw pipelines. (`buffer/cell.rs`)
20. **Border merging** (`MergeStrategy`) — adjacent-block joint glyphs. (`symbols/merge.rs`)
21. **Block shadow**, **underline color**, **`Masked` text**, **RatatuiLogo/Mascot branding**.
22. Slow vs rapid blink distinction (glyphora has one `Blink`).

---

## 6. What glyphora does BETTER (or differently well)

1. **Far larger application-widget library.** ratatui core ships primitives; glyphora bundles interactive
   controls ratatui delegates to third-party crates: `TextInput`, `TextArea` (with **undo/redo** and
   grapheme-cluster cursor), `Button`, `Checkbox`, `Toggle`, `RadioGroup`, `Slider`, `Paginator`,
   `Select`, `Dialog`, `Tree`, `DirectoryTree` (lazy filesystem browser), `Log` (ring buffer + follow),
   `Markdown` renderer, `ScrollView` (offscreen-blit container), `Spinner`/`Marquee`/`Skeleton`/
   `IndeterminateBar` loaders, `Image` (with `fromFile`), `PieChart`, `StackedBarChart`, `Heatmap`,
   `BigText`, `WaveText`, `Link`. Batteries included.
2. **DataTable** offers built-in **sorting, filtering, and pagination** — ratatui's `Table` has none of this.
3. **First-class OSC 8 hyperlinks** as a `Style.link` field (plus a `Link` widget) — ratatui keeps links
   at the cell/escape level, not in `Style`.
4. **Self-contained, dependency-free `CharWidth`** reimplementing Unicode display width (wide table,
   combining marks, ZWJ emoji, regional-indicator flags, VS15/16, Hangul jamo, emoji modifiers) — parity
   with ratatui's `unicode-width` + `unicode-segmentation` crates but with no external deps and a single
   well-documented file.
5. **`Buffer.blit` (region compositing) + `snapshot`** as first-class buffer operations, enabling
   `ScrollView`/overlay rendering that ratatui implements more manually.
6. **Ergonomic Scala 3 layout sugar**: `Layout.horizontal(3, 0.5, Constraint.fill)` via a
   `Int | Double | Constraint` union type — no imports, no builder ceremony.
7. **Cleaner immutable Style with `patch`** and an `opaque type` modifier bitset that stays a flat value
   (no boxed Set), matching ratatui's performance intent idiomatically.
8. **Simpler, deterministic integer layout solver** — no floating-point Cassowary, no LRU cache needed;
   easier to reason about and test (though it trades away Flex/priority fidelity — see §2).

---

## Appendix — files read

**ratatui (Rust):** `ratatui-core/src/buffer/{cell,buffer,diff,cell_width}.rs`,
`style/{color,stylize,palette/*}.rs` + `style.rs`, `text/{span,line,text,grapheme,masked}.rs`,
`symbols/*.rs`, `layout/{layout,constraint,flex,direction,margin,rect,rect/ops}.rs`;
`ratatui-widgets/src/{block,borders,block/padding,block/shadow,paragraph,reflow,list*,table*,tabs,
chart,barchart*,sparkline,gauge,scrollbar,canvas*,calendar,clear,fill,logo,mascot}.rs`.

**glyphora (Scala):** `core/.../{Cell,Buffer,Style,Color,Modifiers,Layout,Constraint,Rect,Text,Line,
Span,Widget,CharWidth}.scala`; `widgets/.../{Block,Borders,Paragraph,ListView,Table,DataTable,Tabs,
Chart,BarChart,Sparkline,DualSparkline,Gauge,LineGauge,Scrollbar,Canvas,Calendar,Alignment,Spacer,
Containers,ScrollView,Tree,DirectoryTree,Select,TextInput,TextArea,Button,Checkbox,Toggle,FormControls,
Dialog,Loading,Spinner,Log,Markdown,BigText,Rule,Image,VizExtras,WaveText,LinkWidget,LineRenderer}.scala`.
