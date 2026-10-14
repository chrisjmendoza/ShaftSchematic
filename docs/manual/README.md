# ShaftSchematic User Manual — source

The printed manual for office and shop users. `ShaftSchematic_User_Manual.pdf` is built from:

| Path | What it is |
|---|---|
| `src/NN-*.html` | One HTML fragment per chapter, in order. Each is a `<section class="chapter" id="ch-…" data-audience="office\|shop\|both">` with one `<h1>` and `<h2>` topics. The build numbers chapters and writes the contents. |
| `style.css` | The print stylesheet — the only building blocks a chapter may use are the classes it styles (`.ui`, `.path`, `ol.steps`, `aside.tip/.note/.warn/.shop`, `table.ref`, `dl.terms` / `dl.terms.glossary`, `figure.wide/.strip/.detail/.detail.half`, `.annotated` + `.mark` + `ol.legend`). |
| `figures/` | Sheet images rendered by the app's real PDF composers. Never hand-edited. |
| `build.py` | Assembles `manual.html`, prints it through headless Microsoft Edge and lays it out: see below. |

## Rebuild the PDF

```
python docs/manual/build.py
```

Needs Python 3 with PyMuPDF (`pip install pymupdf`) and Microsoft Edge. A build takes about
five minutes: it prints the book 20–30 times while it lays it out.

### What the build does to the layout

Chromium has no page floats, running headers or `target-counter`, so `build.py` does that work
itself, printing repeatedly and reading the PDF back until nothing changes:

- **Figure placement.** A figure whose written place would leave a hole at a page foot is moved
  within its section — down past enough text to fill the page, or up so it fits on the page
  before; a full sheet may take the close-ups it passes along with it; as a last resort the
  section starts on a new page. A figure never leaves its section (`h2`), a close-up never leaves
  its `h3`, and none comes to rest right under a heading. The wording is never touched.
- **Page numbers.** The contents and a `, p. N` after every internal cross-reference come from
  the PDF's named destinations; the loop stops when they are the numbers it printed.
- **Runt pages.** A chapter whose last page would hold only a few lines is set a little closer
  (`.chapter.snug`).
- **Keeps.** Short paragraphs, list items and definitions (`.keep`) and short tables (`.whole`)
  never split; an annotated figure's legend is folded into the figure; a pair of `figure.half`
  close-ups becomes one `.figrow`.
- **Footers and outline.** Each chapter gets a named page whose footer carries its title; the
  PDF outline is rewritten to chapters and sections only, and the document metadata is set.

`MANUAL_LAYOUT_DEBUG=1` prints each pass's decisions. Figure classes in the chapters:
`figure.wide` (full sheet), `figure.strip` (full-width crop), `figure.detail` (close-up),
`figure.detail.half` (two side by side).

## Regenerate the figures

The figures come from `app/src/test/java/com/android/shaftschematic/manual/ManualFiguresTest.kt`,
which is skipped in the normal suite and runs only when `MANUAL_FIGURES_DIR` is set. From
PowerShell at the repo root:

```
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:MANUAL_FIGURES_DIR = "$PWD\docs\manual\figures"
.\gradlew --no-daemon :app:testDebugUnitTest --tests "com.android.shaftschematic.manual.ManualFiguresTest" --rerun
```

`--rerun` matters: Gradle does not count the environment variable as a task input, so without it
the test task can report up to date and write nothing.

Regenerate after any change to how a sheet draws, then rebuild the PDF.

## Keeping it true

The manual describes shipping behavior in the app's own on-screen words. When a feature or a
label changes, update the chapter that covers it in the same change, the way contract docs and
`CHANGELOG.md` are updated. The in-app Help (`ui/screen/HelpRoute.kt`) covers the same ground
more briefly; the two must not contradict each other.
