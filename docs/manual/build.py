"""Builds the ShaftSchematic User Manual PDF from the chapter fragments in src/.

    python docs/manual/build.py            # -> docs/manual/ShaftSchematic_User_Manual.pdf

Prints through headless Microsoft Edge until the page numbers settle (usually two or three
passes): the first prints with placeholder page numbers; the PDF's named destinations give the
page of every heading and cross-reference target, which the next pass writes into the contents
and after each cross-reference. The finished PDF then gets a clean outline (chapters and their
sections only) and its document metadata.
Figures are regenerated separately by the gated ManualFiguresTest (see README.md).
"""
from __future__ import annotations

import html
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import date
from pathlib import Path

import fitz  # PyMuPDF

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
OUT_HTML = HERE / "manual.html"
OUT_PDF = HERE / "ShaftSchematic_User_Manual.pdf"

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

# Append ", p. N" after every internal cross-reference in the chapters.
PAGE_REFS = True
# About five lines of body text: shorter paragraphs, list items and definitions never split.
KEEP_WHOLE_CHARS = 520
WHOLE_TABLE_MAX_IN = 1.9   # a table up to about this tall never splits
TALL_ROW_CHARS = 260       # a first row this long may stand alone over a page break

AUDIENCE_LABEL = {"office": "Office", "shop": "Shop floor", "both": "Office &amp; shop"}


def app_version() -> str:
    """Version name the way app/build.gradle.kts derives it (1.3.<base + commit count>)."""
    try:
        gradle = (HERE.parent.parent / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        base = int(re.search(r"versionBase\s*=\s*(\d+)", gradle).group(1))
        count = int(subprocess.check_output(
            ["git", "rev-list", "--count", "HEAD"], cwd=HERE, text=True).strip())
        return f"1.3.{base + count}"
    except Exception:
        return ""


def slug(text: str) -> str:
    s = re.sub(r"<[^>]+>", "", text)
    s = html.unescape(s).lower()
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s or "section"


def strip_tags(text: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", "", text)).strip()


class Chapter:
    def __init__(self, n: int, path: Path):
        self.n = n
        raw = path.read_text(encoding="utf-8")
        m = re.search(r'<section class="chapter" id="([^"]+)"[^>]*data-audience="([^"]+)"', raw)
        if not m:
            raise SystemExit(f"{path.name}: missing <section class=\"chapter\" id=… data-audience=…>")
        self.id, self.audience = m.group(1), m.group(2)
        h1 = re.search(r"<h1>(.*?)</h1>", raw, re.S)
        self.title = strip_tags(h1.group(1))
        # Number the chapter and tag its audience.
        tag = AUDIENCE_LABEL.get(self.audience, "")
        raw = raw.replace(
            h1.group(0),
            f'<div class="chapter-num">Chapter {n}</div>\n  <h1>{h1.group(1)}</h1>'
            + (f'\n  <span class="audience-tag">{tag}</span>' if tag else ""),
            1,
        )
        # Every h2 gets an id (kept if the writer gave one) so the contents can link to it.
        self.sections: list[tuple[str, str]] = []
        seen: set[str] = set()

        def h2(mm: re.Match) -> str:
            attrs, inner = mm.group(1) or "", mm.group(2)
            idm = re.search(r'id="([^"]+)"', attrs)
            hid = idm.group(1) if idm else f"{self.id}-{slug(inner)}"
            while hid in seen:
                hid += "-x"
            seen.add(hid)
            self.sections.append((hid, strip_tags(inner)))
            return f'<h2 id="{hid}">{inner}</h2>' if not idm else mm.group(0)

        raw = re.sub(r"<h2([^>]*)>(.*?)</h2>", h2, raw, flags=re.S)
        # An annotated figure's legend moves inside the figure, so figure, caption and legend
        # move and break as one unit and the legend can never be left on another page.
        raw = re.sub(r'(<div class="annotated">.*?)</figure>(\s*)(<ol class="legend[^"]*">.*?</ol>)',
                     lambda m: f"{m.group(1)}{m.group(3)}</figure>", raw, flags=re.S)
        # Two side-by-side close-ups travel as one row (one figure unit, one break unit).
        raw = re.sub(r'(<figure class="[^"]*half">.*?</figure>\s*<figure class="[^"]*half">.*?</figure>)',
                     r'<div class="figrow">\1</div>', raw, flags=re.S)
        # Chromium drops widows/orphans for a paragraph too short to honour both (fewer than
        # orphans + widows lines), which is how one-line widows got through. A block of up to
        # five lines is therefore kept whole; longer ones split under widows/orphans 3.
        def keep(mm: re.Match) -> str:
            tag, attrs, inner = mm.group(1), mm.group(2), mm.group(3)
            if len(strip_tags(inner)) >= KEEP_WHOLE_CHARS or "class=" in attrs:
                return mm.group(0)
            return f'<{tag}{attrs} class="keep">{inner}</{tag}>'
        raw = re.sub(r"<(p|dd|li)(\s[^>]*)>(.*?)</\1>", keep,
                     raw.replace("<p>", "<p >").replace("<dd>", "<dd >").replace("<li>", "<li >"), flags=re.S)
        raw = raw.replace("<p >", "<p>").replace("<dd >", "<dd>").replace("<li >", "<li>")
        # Reference tables: a short table moves whole; a long one may split, never leaving a
        # header and one short row at a page foot (a tall first row is not a stub).
        def table(mm: re.Match) -> str:
            t = mm.group(0)
            rows = re.findall(r"<tr>(.*?)</tr>", t.split("<tbody>", 1)[-1], flags=re.S)
            cls = ["ref"]
            if est_height_in(t, "table") <= WHOLE_TABLE_MAX_IN:
                cls.append("whole")
            if rows and len(strip_tags(rows[0])) < TALL_ROW_CHARS:
                t = re.sub(r"<tbody>(\s*)<tr>", r'<tbody>\1<tr class="short">', t, count=1)
            return t.replace('<table class="ref">', f'<table class="{" ".join(cls)}">', 1)
        raw = re.sub(r'<table class="ref">.*?</table>', table, raw, flags=re.S)
        # One named page per chapter carries the chapter title in the running footer.
        self.page_name = f"chapter{n}"
        self.snug = False  # set by the layout passes when the chapter would end on a runt page
        self.html = raw.replace('<section class="chapter"', f'<section style="page: {self.page_name}" class="chapter"', 1)


def toc_html(chapters: list[Chapter], pages: dict[str, int]) -> str:
    def p(key: str) -> str:
        return str(pages.get(key, "00"))

    items = []
    for c in chapters:
        subs = "".join(
            f'<li><a href="#{hid}"><span class="row"><span class="t">{html.escape(t)}</span>'
            f'<span class="dots"></span><span class="p">{p(hid)}</span></span></a></li>'
            for hid, t in c.sections
        )
        items.append(
            f'<li><a href="#{c.id}"><span class="row"><span class="t"><span class="num">{c.n}</span>'
            f'{html.escape(c.title)}<span class="aud">{AUDIENCE_LABEL.get(c.audience, "")}</span></span>'
            f'<span class="dots"></span><span class="p">{p(c.id)}</span></span></a>'
            f"<ul>{subs}</ul></li>"
        )
    return '<ol class="toc">' + "".join(items) + "</ol>"


PREF_RE = re.compile(r'(<a href="#([^"]+)">.*?</a>)', re.S)


def with_page_refs(fragment: str, pages: dict[str, int]) -> str:
    """Appends the target's page number to every internal cross-reference (", p. 21").

    Pass one has no pages yet and prints a three-digit placeholder of the widest width, so the
    real numbers seldom move a line; main() reprints until the numbers stop changing.
    """
    if not PAGE_REFS:
        return fragment

    def rep(m: re.Match) -> str:
        n = pages.get(m.group(2))
        num = str(n) if n else "000"
        return f'{m.group(1)}<span class="pref">, p.&#8239;{num}</span>'

    return PREF_RE.sub(rep, fragment)


def footer_css(chapters: list[Chapter]) -> str:
    rules = []
    for c in chapters:
        t = c.title.replace("\\", "").replace('"', '\\"')
        rules.append(
            f'@page {c.page_name} {{ @bottom-left {{ content: "ShaftSchematic User Manual  \\00B7  {t}"; }} }}'
        )
    return "\n".join(rules)


# ── Figure placement ──────────────────────────────────────────────────────────────────────────
# Chromium has no page carry: a figure that does not fit at the foot of a page moves to the next
# one and leaves a hole behind it. The build fixes that the way a layout artist would — it moves
# the figure down its section, one block at a time, until the text before it fills the page.
# A figure never moves past the end of its section (the next h2) or past another figure.
FIG_UNIT = re.compile(r'<figure\b|<div class="figrow"')
TAG = re.compile(r'<(/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*?(/?)>')
VOID = {"img", "br", "hr", "meta", "link", "input", "source", "wbr"}
GAP_MIN_IN = 1.25          # a hole this tall at a page foot is worth closing
MAX_LAYOUT_PASSES = 36


def top_level_children(fragment: str) -> list[tuple[int, int, str]]:
    """(start, end, tag) of every element directly inside the chapter's <section>."""
    out, depth, start, tag0 = [], 0, None, None
    for m in TAG.finditer(fragment):
        closing, tag, selfclose = m.group(1), m.group(2).lower(), m.group(3)
        if tag in VOID or selfclose:
            if depth == 1 and not closing:
                out.append((m.start(), m.end(), tag))
            continue
        if not closing:
            depth += 1
            if depth == 2:
                start, tag0 = m.start(), tag
        else:
            if depth == 2 and tag == tag0:
                out.append((start, m.end(), tag0))
            depth -= 1
    return out


def is_unit(fragment: str, child: tuple[int, int, str]) -> bool:
    return FIG_UNIT.match(fragment, child[0]) is not None


def unit_images(fragment: str, child: tuple[int, int, str]) -> int:
    return fragment.count("<img", child[0], child[1])


HEADINGS = {"h2", "h3", "h4"}
SHORT_LEAD_CHARS = 160     # a block this short after a heading is not enough to stand on its own


def est_height_in(block_html: str, tag: str) -> float:
    """Rough printed height of a block, enough to choose how far down to move a figure."""
    chars = len(strip_tags(block_html))
    if tag in HEADINGS:
        return 0.45
    if tag == "table":
        return 0.35 + block_html.count("<tr") * 0.22 + chars / 85 * 0.2
    return 0.12 + chars / 95 * 0.22 + block_html.count("<li") * 0.04


def place_figures(fragment: str, first_unit: int, defer: dict[int, float],
                  carry: set[int] | None = None, breaks: set[int] | None = None) -> str:
    """Moves figure unit k (book order) about defer[k] inches of text down its section, or up
    when defer[k] is negative.

    Limits: a figure never passes an h2 or another full sheet; a close-up (anything but a full
    sheet) never leaves its h3 subsection or passes another figure, so it stays beside the text
    it illustrates. A full sheet in `carry` takes the close-ups it passes along with it, right
    after it and in their order: the layout a page float would give, without CSS floats (which
    make Chromium drop break-avoid rules on the page). A figure never comes to rest directly
    under a heading, nor under a heading plus one short line, because that strands the heading
    at the foot of the page; when it cannot avoid that, it stays where it was written.
    """
    kids = top_level_children(fragment)
    order = list(range(len(kids)))
    carry = carry or set()
    breaks = breaks or set()
    unit_no: dict[int, int] = {}
    k = first_unit
    for i, c in enumerate(kids):
        if is_unit(fragment, c):
            unit_no[i] = k
            k += 1

    def text_len(j: int) -> int:
        return len(strip_tags(fragment[kids[j][0]:kids[j][1]]))

    def wide_at(j: int) -> bool:
        return fragment.startswith('<figure class="wide', kids[j][0])

    def bad_rest(seq: list[int], pos: int) -> bool:
        """True when a figure inserted at seq[pos] would sit under a heading (+ one short line)
        or between a paragraph and the list, table or steps it introduces."""
        prev = seq[pos - 1] if pos >= 1 else None
        prev2 = seq[pos - 2] if pos >= 2 else None
        nxt = seq[pos] if pos < len(seq) else None
        if prev is not None and kids[prev][2] in HEADINGS:
            return True
        if prev is not None and nxt is not None and kids[prev][2] == "p"                 and kids[nxt][2] in ("ul", "ol", "table", "dl"):
            return True
        return (prev is not None and prev2 is not None and kids[prev2][2] in HEADINGS
                and text_len(prev) < SHORT_LEAD_CHARS)

    for i in [j for j in range(len(kids)) if j in unit_no]:
        steps = defer.get(unit_no[i], 0.0)
        if not steps:
            continue
        wide = wide_at(i)
        start = order.index(i)
        rest = order[:start] + order[start + 1:]          # the section without this figure
        pos = start                                       # insertion point in `rest`
        carried: list[int] = []
        if steps < 0:
            # Up, past about -steps inches, so the figure fits at the foot of the page before.
            need = -steps
            last_good = start
            while need > 0 and pos > 0:
                prv = rest[pos - 1]
                tag = kids[prv][2]
                opener = 'class="chapter-num"' in fragment[kids[prv][0]:kids[prv][1]]
                if tag in ("h1", "h2") or prv in unit_no or (not wide and tag in HEADINGS) or opener:
                    break
                pos -= 1
                need -= est_height_in(fragment[kids[prv][0]:kids[prv][1]], tag)
                if not bad_rest(rest, pos):
                    last_good = pos
            while wide and pos > 0 and kids[rest[pos - 1]][2] in ("h3", "h4"):
                pos -= 1
            if bad_rest(rest, pos):
                pos = last_good
        else:
            need = steps
            good = (start, list(rest), [])               # last good resting place passed
            while pos < len(rest) and (need > 0 or bad_rest(rest, pos)):
                nxt = rest[pos]
                tag = kids[nxt][2]
                if tag == "h2" or (not wide and tag in HEADINGS):
                    break
                if nxt in unit_no:
                    if not (wide and unit_no[i] in carry and not wide_at(nxt)):
                        break
                    carried.append(rest.pop(pos))         # travels with the sheet
                    continue
                pos += 1
                need = max(0.0, need - est_height_in(fragment[kids[nxt][0]:kids[nxt][1]], tag))
                if not bad_rest(rest, pos):
                    good = (pos, list(rest), list(carried))
            if bad_rest(rest, pos):
                # Blocked short of a good place: fall back to the last good one passed.
                pos, rest, carried = good
        if pos == start and not carried:
            continue
        if bad_rest(rest, pos):
            continue                                      # no good resting place: leave it
        order = rest[:pos] + [i] + carried + rest[pos:]
    # A sheet in `breaks` starts its section on a new page (the section's h2 breaks before),
    # rather than leaving the h2 and a line of text alone over a hole at a page foot.
    broken = set()
    for i, u in unit_no.items():
        if u in breaks:
            pos = order.index(i)
            for j in reversed(order[:pos]):
                if kids[j][2] == "h2":
                    broken.add(j)
                    break
    if order == list(range(len(kids))) and not broken:
        return fragment

    def chunk(j: int) -> str:
        h = fragment[kids[j][0]:kids[j][1]]
        return h.replace("<h2", '<h2 class="newpage"', 1) if j in broken else h

    head = fragment[:kids[0][0]] if kids else fragment
    tail = fragment[kids[-1][1]:] if kids else ""
    return head + "\n\n  ".join(chunk(j) for j in order) + tail


def unit_sections(chapters: list["Chapter"]) -> list[tuple[int, int]]:
    """(chapter, h2 ordinal) of every figure unit, in book order."""
    out = []
    for ci, c in enumerate(chapters):
        h2 = 0
        for ch in top_level_children(c.html):
            if ch[2] == "h2":
                h2 += 1
            elif is_unit(c.html, ch):
                out.append((ci, h2))
    return out


def count_units(fragment: str) -> int:
    return sum(1 for c in top_level_children(fragment) if is_unit(fragment, c))


def figure_holes(pdf_path: Path, chapters: list["Chapter"]) -> dict[int, tuple[float, float]]:
    """Figure units that open a page while the page before ends in a hole of GAP_MIN_IN or more,
    with the height of that hole and of the figure itself, in inches."""
    units_imgs: list[int] = []
    for c in chapters:
        kids = top_level_children(c.html)
        units_imgs += [unit_images(c.html, ch) for ch in kids if is_unit(c.html, ch)]
    with fitz.open(pdf_path) as d:
        chapter_first = {e[2] for e in d.get_toc() if e[0] == 1}
        content_top = 0.8 * 72
        content_bottom = d[0].rect.height - 0.85 * 72
        imgs = []  # (page index, top, only figures above it) in reading order, cover excluded
        img_h: list[float] = []
        bottoms = {}
        for i in range(1, d.page_count):
            page = d[i]
            infos = sorted(page.get_image_info(), key=lambda r: (r["bbox"][1], r["bbox"][0]))
            blocks = [b for b in page.get_text("blocks") if b[3] < content_bottom - 1]
            # Body text: not a caption, not a heading (headings are what a hole strands).
            prose = []
            for b in page.get_text("dict")["blocks"]:
                for line in b.get("lines", []):
                    t = "".join(sp["text"] for sp in line["spans"]).strip()
                    size = max(sp["size"] for sp in line["spans"])
                    if t and size < 11.3 and not t.startswith("Figure ") and line["bbox"][3] < content_bottom - 1:
                        prose.append(line["bbox"])
            for r in infos:
                top = r["bbox"][1]
                imgs.append((i, top, not any(b[3] <= top + 1 for b in prose)))
                img_h.append((r["bbox"][3] - top) / 72)
            ys = [b[3] for b in blocks] + [r["bbox"][3] for r in infos]
            bottoms[i] = max(ys) if ys else content_top
    holes: dict[int, tuple[float, float]] = {}
    pos = 0
    unit_h: list[float] = []
    p2 = 0
    for count in units_imgs:
        hs = [img_h[j] for j in range(p2, min(p2 + count, len(img_h)))]
        unit_h.append((max(hs) if hs else 5.4) + 0.5)
        p2 += count
    for unit, count in enumerate(units_imgs):
        if pos >= len(imgs):
            break
        page, top, figures_only_above = imgs[pos]
        pos += count
        if not figures_only_above or (page + 1) in chapter_first:
            continue
        prev = page - 1
        hole = (content_bottom - bottoms[prev]) / 72 if prev >= 1 else 0
        if hole >= GAP_MIN_IN:
            holes[unit] = (hole, unit_h[unit] if unit < len(unit_h) else 5.8)
    return holes


RUNT_MAX_IN = 2.0  # a chapter's last page holding less than this is a runt


def runt_chapters(pdf_path: Path, chapters: list["Chapter"]) -> set[int]:
    """Chapters (0-based) whose last page holds less than RUNT_MAX_IN of content."""
    where = dest_pages(pdf_path)
    firsts = [where[c.id] for c in chapters if c.id in where]
    with fitz.open(pdf_path) as d:
        content_bottom = d[0].rect.height - 0.85 * 72
        out = set()
        for ci, first in enumerate(firsts):
            last = (firsts[ci + 1] - 1) if ci + 1 < len(firsts) else d.page_count
            if last <= first:
                continue
            page = d[last - 1]
            ys = [b[3] for b in page.get_text("blocks") if b[3] < content_bottom - 1]
            ys += [r["bbox"][3] for r in page.get_image_info()]
            if ys and (max(ys) - 0.8 * 72) / 72 < RUNT_MAX_IN:
                out.add(ci)
    return out


def chapter_bodies(chapters: list["Chapter"], pages: dict[str, int], defer: dict[int, float],
                   carry: set[int], breaks: set[int] | None = None) -> str:
    out, first = [], 0
    for c in chapters:
        body = place_figures(c.html, first, defer, carry, breaks)
        if c.snug:
            body = body.replace('class="chapter"', 'class="chapter snug"', 1)
        out.append(with_page_refs(body, pages))
        first += count_units(c.html)
    return "".join(out)


def document(chapters: list[Chapter], pages: dict[str, int], version: str,
             defer: dict[int, float] | None = None, carry: set[int] | None = None,
             breaks: set[int] | None = None) -> str:
    cover_img = next((f"figures/{n}" for n in ("cover-shaft.png", "schematic.png")
                      if (HERE / "figures" / n).exists()), "")
    by_aud = {
        "office": [c for c in chapters if c.audience in ("office", "both")],
        "shop": [c for c in chapters if c.audience in ("shop", "both")],
    }

    def links(cs: list[Chapter]) -> str:
        return ", ".join(f'<a href="#{c.id}">{c.n}.&nbsp;{html.escape(c.title)}</a>' for c in cs)

    ver = f"Version {version} &nbsp;·&nbsp; " if version else ""
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>ShaftSchematic User Manual</title>
<link rel="stylesheet" href="style.css">
<style>
{footer_css(chapters)}
</style>
</head><body>
<section class="cover">
  <div class="titles">
    <div class="kicker">Marine propulsion shafts</div>
    <h1>ShaftSchematic</h1>
    <p class="subtitle">User Manual — drawing, documenting and measuring<br>propeller shafts, from the office to the lathe</p>
  </div>
  <div class="rule"></div>
  <div class="band">{f'<img src="{cover_img}" alt="">' if cover_img else ""}</div>
  <div class="foot">
    <div><strong>For office and shop users</strong><br>Android phones and tablets</div>
    <div style="text-align:right">{ver}{date.today():%B %Y}</div>
  </div>
</section>
<section class="front">
  <h1>Contents</h1>
  {toc_html(chapters, pages)}
  <div class="howto">
    <h2>Where to start</h2>
    <p><strong>Building shafts and producing sheets in the office?</strong> Read {links(by_aud["office"][:4])}.</p>
    <p><strong>Reading sheets and taking measurements on the floor?</strong> Read {links(by_aud["shop"][:3])}.</p>
    <p>Stuck on something specific? The <a href="#ch-help">Troubleshooting</a> chapter is organized by the question you'd ask.
    The app's own <span class="ui">Help &amp; FAQ</span> screen is searchable, and the <span class="ui">?</span> buttons on the document tabs open it at that tab's guide.</p>
  </div>
</section>
{chapter_bodies(chapters, pages, defer or {}, carry or set(), breaks)}
</body></html>
"""


def edge() -> str:
    for c in EDGE_CANDIDATES:
        if os.path.exists(c):
            return c
    raise SystemExit("Microsoft Edge not found")


def print_pdf(html_path: Path, pdf_path: Path) -> None:
    # The msedge.exe launcher hands off to a browser process and returns before the PDF is
    # written, so wait for the file to appear and stop growing.
    profile = tempfile.mkdtemp(prefix="manual-edge-")
    if pdf_path.exists():
        pdf_path.unlink()
    try:
        subprocess.run(
            [
                edge(), "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                "--generate-pdf-document-outline", f"--user-data-dir={profile}",
                f"--print-to-pdf={pdf_path}", html_path.as_uri(),
            ],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=300,
        )
        deadline = time.time() + 300
        last = -1
        while time.time() < deadline:
            if pdf_path.exists():
                size = pdf_path.stat().st_size
                if size > 0 and size == last:
                    try:
                        with fitz.open(pdf_path) as d:
                            d.page_count
                        break
                    except Exception:
                        pass
                last = size
            time.sleep(1.5)
        else:
            raise SystemExit("Edge produced no PDF")
    finally:
        time.sleep(1)
        shutil.rmtree(profile, ignore_errors=True)


def dest_pages(pdf_path: Path) -> dict[str, int]:
    """Page (1-based) of every named destination Edge wrote, i.e. of every linked id."""
    with fitz.open(pdf_path) as d:
        names = d.resolve_names()
    return {k: v["page"] + 1 for k, v in names.items() if v.get("page", -1) >= 0}


def finish_pdf(pdf_path: Path, chapters: list[Chapter], version: str) -> None:
    """Replaces Edge's outline (cover title, contents, h3s) with chapters and their sections."""
    tmp = pdf_path.with_suffix(".tmp.pdf")
    with fitz.open(pdf_path) as d:
        names = d.resolve_names()
        toc = []
        for c in chapters:
            for level, hid, title in [(1, c.id, f"{c.n}. {c.title}")] + [(2, h, t) for h, t in c.sections]:
                dest = names.get(hid)
                if not dest:
                    print(f"warning: no destination for {hid}", file=sys.stderr)
                    continue
                # Named destinations are in PDF space (origin bottom-left); the outline wants
                # the page's top-left space.
                x, y = dest.get("to") or (0, d[dest["page"]].rect.height)
                to = fitz.Point(x, d[dest["page"]].rect.height - y)
                toc.append([level, title, dest["page"] + 1,
                            {"kind": fitz.LINK_GOTO, "page": dest["page"], "to": to}])
        d.set_toc(toc)
        meta = d.metadata
        meta.update({
            "title": "ShaftSchematic User Manual",
            "subject": "Drawing, documenting and measuring marine propulsion shafts"
                       + (f" — version {version}" if version else ""),
            "author": "ShaftSchematic",
            "keywords": "ShaftSchematic, propeller shaft, tailshaft, runout, schematic, user manual",
        })
        d.set_metadata(meta)
        d.set_pagemode("UseOutlines")
        d.save(tmp, garbage=3, deflate=True)
    os.replace(tmp, pdf_path)


def main() -> None:
    files = sorted(SRC.glob("[0-9][0-9]-*.html"))
    if not files:
        raise SystemExit("no chapters in src/")
    chapters = [Chapter(i + 1, f) for i, f in enumerate(files)]
    version = app_version()

    pages: dict[str, int] = {}
    defer: dict[int, float] = {}
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as td:
        # Layout passes. Each figure that would leave a hole at a page foot is fixed in turn,
        # the first one in each chapter per pass (chapters start on a new page, so they paginate
        # independently). The remedies, tried in order until the hole closes without opening an
        # earlier one: move the figure down its section past enough text to fill the page; move
        # it further (twice); move it up so it fits at the foot of the page before; for a full sheet,
        # move it down and let it carry the close-ups it passes (what a page float would do);
        # last, start its section on a new page, so a section heading and its first line are
        # never left alone over the hole.
        # A figure no remedy closes keeps the remedy that left the smallest hole.
        carry: set[int] = set()
        breaks: set[int] = set()
        chap_of = [ci for ci, _ in unit_sections(chapters)]
        is_wide = [c.html.startswith('<figure class="wide', ch[0])
                   for c in chapters for ch in top_level_children(c.html) if is_unit(c.html, ch)]
        best: dict[int, tuple[float, int]] = {}   # unit -> (smallest hole seen, remedy index)
        working: dict[int, int] = {}      # chapter -> unit being fixed
        attempt: dict[int, int] = {}      # unit -> remedy index
        origin: dict[int, tuple[float | None, bool, float, float]] = {}
        resolved: set[int] = set()
        gave_up: set[int] = set()
        remedies = ("down", "further", "farther", "up", "carry", "carry further", "new page")

        def restore(u: int) -> None:
            d0, f0, _, _ = origin[u]
            breaks.discard(u)
            if d0 is None:
                defer.pop(u, None)
            else:
                defer[u] = d0
            if f0:
                carry.add(u)
            else:
                carry.discard(u)

        def apply(u: int) -> bool:
            """Applies the unit's next remedy that changes the document; False when none is left."""
            while attempt[u] < len(remedies):
                restore(u)
                before = document(chapters, pages, version, defer, carry, breaks)
                d0, _, hole, fig_h = origin[u]
                kind = remedies[attempt[u]]
                if (kind.startswith("carry") or kind == "new page") and not is_wide[u]:
                    attempt[u] += 1               # only full sheets carry close-ups along
                    continue
                if kind == "down":
                    defer[u] = (d0 or 0.0) + hole
                elif kind == "further":
                    defer[u] = (d0 or 0.0) + hole + 1.5
                elif kind == "farther":
                    defer[u] = (d0 or 0.0) + hole + 3.0
                elif kind == "up":
                    defer[u] = -(fig_h - hole + 0.3)
                elif kind == "carry":
                    defer[u] = (d0 or 0.0) + hole
                    carry.add(u)
                elif kind == "carry further":
                    defer[u] = (d0 or 0.0) + hole + 1.5
                    carry.add(u)
                else:
                    breaks.add(u)
                if document(chapters, pages, version, defer, carry, breaks) != before:
                    if os.environ.get("MANUAL_LAYOUT_DEBUG"):
                        print(f"    figure unit {u}: try {kind} (hole {hole:.1f} in)")
                    return True
                attempt[u] += 1
            restore(u)
            return False

        # One loop does it all: print, read the page numbers back, fix the next figure hole or
        # runt page, and stop when a pass changes nothing and its page numbers are the ones it
        # printed (so the contents and every cross-reference are right).
        refixed: set[int] = set()
        n = 0
        for n in range(1, MAX_LAYOUT_PASSES + 1):
            used = pages
            OUT_HTML.write_text(document(chapters, used, version, defer, carry, breaks), encoding="utf-8")
            out = Path(td) / f"pass{n}.pdf"
            print_pdf(OUT_HTML, out)
            pages = dest_pages(out)
            holes = figure_holes(out, chapters)
            if os.environ.get("MANUAL_LAYOUT_DEBUG"):
                print(f"  pass {n}: holes {sorted(holes)}, working {working}, "
                      f"carrying {sorted(carry)}, numbers {'settled' if pages == used else 'moved'}")
            # A hole that reopens under a figure already placed (later text rewrapped) gets one
            # more round of remedies.
            for u in holes:
                if u in resolved and u not in refixed and u not in working.values():
                    resolved.discard(u)
                    refixed.add(u)
            changed = False
            for ci in range(len(chapters)):
                open_holes = sorted(u for u in holes if chap_of[u] == ci and u not in gave_up)
                w = working.get(ci)
                if w is not None:
                    regressed = any(u in resolved and u < w for u in open_holes)
                    if not regressed and w not in holes:
                        resolved.add(w)            # this remedy worked
                        del working[ci]
                        w = None
                    else:
                        if not regressed and holes[w][0] < best[w][0]:
                            best[w] = (holes[w][0], attempt[w])
                        attempt[w] += 1
                        if apply(w):
                            changed = True
                            continue
                        # Nothing closed it: keep whichever remedy left the smallest hole.
                        if best[w][1] >= 0:
                            attempt[w] = best[w][1]
                            apply(w)
                        gave_up.add(w)
                        del working[ci]
                        w = None
                        changed = True
                        continue
                nxt = [u for u in open_holes if u not in resolved]
                if not nxt:
                    continue
                u = nxt[0]
                hole, fig_h = holes[u]
                origin[u] = (defer.get(u), u in carry, hole, fig_h)
                best[u] = (hole, -1)
                attempt[u] = 0
                if apply(u):
                    working[ci] = u
                else:
                    gave_up.add(u)
                changed = True
            if changed or working:
                continue
            # Figures settled. A chapter that ends on a runt page is set a little closer, once.
            runts = {ci for ci in runt_chapters(out, chapters) if not chapters[ci].snug}
            if runts:
                for ci in runts:
                    chapters[ci].snug = True
                    # The chapter reflows, so its figures are placed again from scratch.
                    for u in [u for u, c in enumerate(chap_of) if c == ci]:
                        resolved.discard(u)
                        gave_up.discard(u)
                        refixed.discard(u)
                        defer.pop(u, None)
                        carry.discard(u)
                print(f"set closer to avoid a runt last page: chapter(s) "
                      f"{', '.join(str(ci + 1) for ci in sorted(runts))}")
                continue
            if pages == used:
                break
        else:
            print("warning: layout did not settle; keeping the last pass", file=sys.stderr)
        shutil.copyfile(out, OUT_PDF)
        if defer or carry:
            print(f"figure placement: {len(defer)} figure(s) moved within their sections")
        if os.environ.get("MANUAL_LAYOUT_DEBUG"):
            print(f"  final placement: moves {defer}, carrying {sorted(carry)}, "
                  f"new page {sorted(breaks)}, "
                  f"set closer: {[c.n for c in chapters if c.snug]}")
    missing = [hid for c in chapters for hid in [c.id] + [h for h, _ in c.sections] if hid not in pages]
    if missing:
        print(f"warning: {len(missing)} heading(s) without a page: {missing[:5]}", file=sys.stderr)
    finish_pdf(OUT_PDF, chapters, version)
    with fitz.open(OUT_PDF) as d:
        n_pages = d.page_count
    print(f"{OUT_PDF.name}: {n_pages} pages, {len(chapters)} chapters, {n} passes")


if __name__ == "__main__":
    main()
