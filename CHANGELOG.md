# Changelog

All notable changes to **ShaftSchematic** will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/) and follows [Semantic Versioning](https://semver.org/).

---

## [1.1.1] - 2026-01-08

### Added
- New document filename extension: `.shaft` (content remains JSON) with legacy `.json` compatibility.
- One-time internal document migration from `.json` to `.shaft`.
- Inline component titling improvements:
    - Liners: positional AFT/MID/FWD names with optional user override (tap title to edit).
    - Tapers: AFT/FWD direction naming based on diameter trend.

### Changed
- Editor component card titles are now deterministic by physical position (more stable than list index).
- Shaft Editor header typography updated for a stronger visual hierarchy.

### Fixed
- Gradle connected-test safety guard adjusted for Kotlin DSL compatibility.

## [0.2.0] - 2025-09-14

### Added
- Coverage chip hint (`coverageChipHint()`) in `ShaftViewModel` for concise, unit-aware messages.
- Settings menu in `TopAppBar` with toggle to choose between **chip-style** or **text-style** coverage hints.
- `SettingsDialog` component with temporary state management (persistence TODO).
- Export PDF action added to `TopAppBar` (optional), keeping Floating Action Button export as well.

### Changed
- Updated `ShaftScreen` scaffold to include Settings and Export actions in the `TopAppBar`.
- Improved overall UI structure and consistency.

### TODO
- Persist user settings (hint style preference) across sessions.

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
