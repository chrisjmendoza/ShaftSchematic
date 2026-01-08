# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## Versioning Notes

- Early development used git tags (`v0.2.0`, `v0.3.1`) for milestones.
- App `versionName` wasn’t kept in sync with those tags; starting with `1.1.1` we’re using the changelog + app version together.

## [1.1.1] - 2026-01-08

### Added
- `.shaft` document filenames (content remains JSON), plus legacy `.json` compatibility.
- One-time internal document migration from `.json` → `.shaft`.
- Component snapping (`SnapEngine`) and related workflow scaffolding.
- Component deletion + single-step undo (expanded coverage across components over time).
- Thread “Include in OAL” toggle (end threads can be excluded from the OAL measurement window).
- Preview color presets and B/W mode.
- Shaft position field persisted and reflected in the PDF footer.
- Taper keyway (KW) width/depth fields.

### Changed
- Save/open behavior and filename suggestions improved; overwrite confirmation added.
- PDF export UX improved (including optional auto-open after export).
- Editor titles made more informative/deterministic:
    - Bodies/tapers/liners use physical aft→fwd ordering for display naming.
    - Liners: positional AFT/MID/FWD names, numbers only when needed, optional user override (tap title to edit).
    - Tapers: AFT/FWD direction naming based on diameter trend, numbered only when needed.
- PDF layout and dimension labeling refined over multiple iterations.
- App locked to portrait for more predictable editor layout.
- “Shaft Editor” header typography strengthened for clearer hierarchy.

### Fixed
- Gradle connected-test safety guard adjusted for Kotlin DSL compatibility.
- Multiple PDF/export/preview correctness issues (spacing, labeling, footer rules) addressed over time.

---

## [0.3.1] - 2025-09-16

### Added
- Full-rectangle preview rendering for components and improved editor scaffolding.

### Changed
- Editor structure and input UX improved (including taper handling groundwork).
- PDF title block and layout helpers refactored/cleaned up.

## [0.2.0] - 2025-09-14

### Added
- Coverage chip hint (`coverageChipHint()`) in `ShaftViewModel` for concise, unit-aware messages.
- Settings menu in `TopAppBar` with toggle to choose between **chip-style** or **text-style** coverage hints.
- `SettingsDialog` component with temporary state management (persistence TODO).
- Export PDF action added to `TopAppBar` (optional), keeping Floating Action Button export as well.

### Changed
- Updated `ShaftScreen` scaffold to include Settings and Export actions in the `TopAppBar`.
- Improved overall UI structure and consistency.

---

## [0.1.0] - Initial Commit

### Added
- Project setup with package name `com.android.shaftschematic`.
- Core data models: `ShaftSpecMm`, `BodySegmentSpec`, `KeywaySpec`, `LinerSpec`, `TaperSpec`, `ThreadSpec`.
- `UnitSystem` enum for inches/mm conversion.
- `ShaftViewModel` with state flows for spec + unit handling.
- `ShaftScreen` UI with Compose, including input fields for:
    - Basics (length, diameter, chamfer, shoulder length).
    - Body segments (dynamic add/remove).
    - Keyways (dynamic add/remove).
    - Tapers with ratio handling.
    - Threads (forward + aft).
    - Liners (dynamic add/remove).
- Export-to-PDF feature using `ShaftPdfComposer` with:
    - Span/segment drawing.
    - Tapers, threads, keyways, liners.
    - Dimension arrows and overall length.
    - Simple title block with project info.
- Git integration with `.gitignore`, initial README, and project structure.
