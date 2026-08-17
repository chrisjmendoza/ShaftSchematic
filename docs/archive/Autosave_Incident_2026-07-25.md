# Autosave Data-Loss Incident — 2026-07-25

## What was reported
Chris edited the bundled **Aleutian Spray** sample heavily, closed out, went back into the
shaft, and found none of the edits — the shaft was back to its pristine state.

## Root cause (confirmed in code)
Three interacting behaviors, none individually wrong-looking:

1. **Single-slot autosave.** `AutosaveManager` stores exactly one `SessionSnapshot` under one
   DataStore key (`autosave_last_session`). No document identity, no history.
2. **Unconditional continuous writes.** `ShaftViewModel.init` combines 12 session flows,
   debounces 1500 ms, and writes the snapshot to that slot on *every* change — including the
   change produced by *loading a document*. There is no dirty check: a freshly-opened,
   untouched file is written to the slot just like real edits.
3. **Sample/file edits live only in the slot.** Opening a shaft never writes edits back to its
   file until an explicit save. For an edited-but-never-saved session, the autosave slot is the
   only copy in existence.
4. **`hasUnsavedWork()` compared too little.** The pre-existing "unsaved changes" editor guard
   compared only `ShaftSpec` plus four metadata strings (job/customer/vessel/notes) against
   saved copies of those same fields. Wear records, runout readings/config, shaft position,
   unit-lock, and OAL-mode were invisible to it. Wear-mark edits (dye-pen pits, TIR readings)
   left the session "clean" by this check even while genuinely unsaved, so the guard stayed
   silent exactly where it was needed.

**Loss sequence:** edit sample (draft slot = your edits) → leave editor → reopen the shaft from
the file list (not "Continue Draft") → `importJson` loads the pristine file → ≤1.5 s later the
observer overwrites the slot with the pristine state → edits destroyed. Tapping **New Drawing**
destroys the draft the same way. The data is unrecoverable once overwritten.

## Fix (built 2026-07-25, branch `feat/draft-history`)
Four changes — two autosave-side, two editor-guard-side:

1. **Dirty gate (the actual bug fix).** The autosave observer only writes when the session
   differs from the last saved/loaded state (full-snapshot comparison seeded by
   `importJson`/`markDocumentSaved`/`newDocument`). A freshly-loaded pristine document can
   never clobber anything again. When the state returns to clean (explicit save), the
   document's draft entry is removed — saved work isn't also a "draft".
2. **Draft history, last 3 unsaved sessions.** The single slot becomes a ring of up to
   **3 `DraftEntry` records** (`draftId`, `documentName`, `updatedAtEpochMs`, snapshot), keyed
   per editing session: a new identity is minted on `newDocument()` and on every document
   open, so working on shaft B upserts B's entry and *cannot* touch A's. Eviction is
   oldest-by-timestamp only when a *fourth distinct* draft appears. Legacy single-slot drafts
   migrate into the ring on first read. The StartScreen "Continue Draft" button becomes a
   drafts list (up to 3: name, relative age, continue / discard each).
3. **`hasUnsavedWork()` now uses the full snapshot.** It returns
   `shouldWriteDraft(buildCurrentSnapshot(), savedSnapshot)` — the exact same comparison as the
   autosave dirty gate, so *every* tracked field (spec, metadata, position, unit-lock, OAL mode,
   wear record, runout readings/config) counts as unsaved work. The old per-field
   `_savedSpec`/`_savedJobNumber`/`_savedCustomer`/`_savedVessel`/`_savedNotes` comparison is
   gone; `markDocumentSaved()` now sets only `savedSnapshot`.
4. **Universal unsaved-changes guard + Close Document.** Every session-replacing entry point —
   Start's New/Open/Open-recent, the editor's New/Open, and a new "Close Document" overflow-menu
   item — routes through one `runGuarded(action)` helper and one shared `UnsavedChangesDialog`
   hoisted to NavHost scope in `AppNav.kt`. A dirty session prompts Save / Don't save / Cancel
   before the action runs; Save reuses the `pendingPostSaveAction` continuation so it also works
   when the document has no name yet (routes through `saveLocal` first). "Don't save" is worded
   deliberately, not "Discard" — the draft-ring entry is left in place as the safety net.

## Invariants going forward
- A draft entry is written **only** for dirty (unsaved) sessions.
- Opening/creating a document must never mutate another document's draft entry.
- Explicit save removes the session's draft entry; discard removes exactly one entry.
- Ring capacity 3; eviction strictly oldest-first, and only on insertion of a new identity.
