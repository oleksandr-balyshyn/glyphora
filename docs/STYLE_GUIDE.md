# glyphora visual and editorial style guide

This is the source of truth for every glyphora surface: README, Docusaurus, GitHub
Wiki exports, social previews, diagrams, and future release artwork.

## Brand idea

**Terminal UI, written like Scala.** The visual language joins Scala's layered red
form with a cyan terminal cursor. Interfaces should feel precise, expressive, and
engineered—not retro for nostalgia's sake and not like a default documentation
template.

## Logo system

| Asset | Use |
|---|---|
| `docs/assets/mark.svg` | Square avatars, compact navigation, favicons. |
| `docs/assets/logo.svg` | Horizontal lockup when the product name must be readable. |
| `docs/assets/banner.svg` | README lead image, docs introduction, and social preview. |

Keep clear space around the mark equal to the cursor width. Never recolor individual
Scala layers, remove the cursor dot, place the logo on a busy photograph, or add a
second drop shadow.

## Palette

| Token | Hex | Meaning |
|---|---|---|
| Canvas | `#070910` | terminal-black foundation |
| Panel | `#0D1220` | raised surfaces and code windows |
| Scala coral | `#EF3340` / `#FF5C64` | brand, primary action, active state |
| Terminal cyan | `#22D3EE` | input, links, focus, data flow |
| Effect violet | `#A78BFA` | animation and composition |
| Runtime mint | `#34D399` | success, ready, native output |
| Signal amber | `#FBBF24` | state and reactive values |

Use color as information. A page may use the full palette, but one component should
have one dominant accent.

## Typography

- **Space Grotesk Variable** — headlines, metric numerals, short product statements.
- **IBM Plex Sans Variable** — long-form documentation and interface copy.
- **JetBrains Mono Variable** — code, labels, navigation, metadata, commands.

Large display type is sentence case, tightly tracked, and short. Monospace labels are
uppercase, small, and generously tracked. Body copy stays readable and calm.

## Shape and layout

- Use a four-pixel corner radius for controls and cards; the logo is the exception.
- Prefer fine grid lines, offset shadows, and deliberate hard edges over generic
  frosted-glass cards.
- Alternate dense technical regions with generous editorial whitespace.
- Use asymmetrical bento grids only when card span communicates priority.
- Animations must be subtle and respect `prefers-reduced-motion`.

## Icons and diagrams

Icons are original 96×96 SVG pictograms on the same dark rounded tile. Their accent
color must follow the palette meaning above. Diagrams use the same token colors,
monospace labels, numbered stages, and left-to-right data flow. Do not mix emoji font
glyphs into SVG files; use deterministic vector pictograms instead.

Emoji are welcome in Markdown and HTML as friendly wayfinding, but never replace a
label or carry meaning alone.

## Editorial voice

Write like an experienced Scala developer pairing with the reader:

- Lead with what the reader will accomplish.
- Explain *why* before listing API names.
- Prefer runnable snippets over abstract claims.
- Call out terminal-specific traps—display width, focus, render-thread writes, TTYs.
- End sections with the next useful action, not marketing filler.
- Define unfamiliar glyphora terms on first use.

Every guide should include a short orientation, at least one working example, a
pitfall or decision note where relevant, and links to the next deeper topic.
