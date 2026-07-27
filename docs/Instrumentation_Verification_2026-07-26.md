# Instrumentation Backlog Verification — TODO §5.2

**Date:** 2026-07-26
**Scope:** verify the four open items under TODO.md §5.2 "Instrumentation (Open)".
**Trigger:** on-device report that "carousel scrolls to selected after tap" already works.
**Status:** verification below, then acted on — see "What was built" at the end.
Suite 719 → 796 tests, all green. One production bug found and fixed.

---

## Headline

The on-device report is correct. The carousel-scroll behavior is fully implemented and
correctly wired end to end — verified in code below.

The reason the box is still unchecked is that **§5.2 is a test-coverage checklist, not a
feature checklist.** Every line in it names an *instrumentation test to write*, not a
behavior to build. Three of the four behaviors are already implemented and shipping; what's
missing is automated verification of them.

And that missing verification is blocked on one shared root cause: **the project has no
working Compose UI test harness.** All four items need one.

---

## Harness state (the shared blocker)

| Piece | State |
|---|---|
| `androidTestImplementation(libs.androidx.compose.ui.test.junit4)` | Declared — `app/build.gradle.kts:146` |
| `debugImplementation(libs.androidx.compose.ui.test.manifest)` | Declared — `app/build.gradle.kts:139` |
| Any test using `createComposeRule` / `onNodeWithTag` | **Zero.** Repo-wide grep finds no usages outside prose in `docs/` |
| Robolectric | **Not configured.** No dependency, and no `testOptions` block at all in `app/build.gradle.kts`, so no `unitTests.isIncludeAndroidResources = true` |
| Connected instrumentation runs | Gated off by default; opt in with `-PallowConnectedAndroidTests=true` (`app/build.gradle.kts:163-191`) |
| androidTest source set | 4 files, none Compose, none UI: `ExampleInstrumentedTest.kt`, `testutil/ClearDataStoreRule.kt`, `ui/viewmodel/InternalDocsMigrationCompletionTest.kt`, `util/PdfOpenIntentTest.kt` |

Relevant history: commit `3ba9992` (2026-07-26) deleted `EditorTopBarExportPdfTest.kt`, the
last Compose UI test in the repo. It had rotted against `ShaftScreen`'s parameter list and was
breaking `compileDebugAndroidTestKotlin`; its logic was ported down to the pure
`ExportPdfGateTest`. That deletion is why the harness count is currently zero, and it's a fair
illustration of the tradeoff: UI tests that reach into composable signatures rot, pure tests
over extracted logic don't.

---

## Item-by-item

### 1. Commit-on-blur correctness

**Behavior: implemented and correct. Coverage: none.**

`ui/input/NumericInputField.kt:114-129` — `onFocusChanged` captures the text into
`textWhenFocused` on focus gain, and on blur calls `commitOrRevert()` only when
`captured == null || text.text != captured`. That matches the invariant in
`docs/NumberField.md` and in CLAUDE.md: a tap-and-leave with no edit is a no-op.

No test file anywhere references `NumericInputField`, `commitOrRevert`, `textWhenFocused`, or
`onCommit`. The comparison is inlined inside the `@Composable`, so there is no pure function
to test — this one genuinely needs a Compose rule, or a small refactor extracting the
"should this blur commit?" predicate.

### 2. Blocking-dialog behavior

**Logic layer: covered, 20 tests. Wiring layer: none.**

Already covered by pure JVM JUnit4 tests:

- `ui/nav/BlockingExportErrorTest.kt` — 7 tests over `blockingExportError(spec)`
- `ui/util/ExportPdfGateTest.kt` — 4 tests over `exportPdfGate(spec, collidingIds)` (the
  toolbar enable/disable decision plus message text)
- `ui/util/StartOverlapValidationTest.kt` — 9 tests over `startOverlapErrorMm(...)`, the
  validator the Add dialogs call

Not covered: that the decisions are actually *honored* by the UI. Nothing asserts that
`blockingErrorMessage` suppresses export and shows the dialog in
`ui/nav/PdfExportRoute.kt:58, 161-163, 178, 191`, and nothing asserts the Add-dialog confirm
buttons are disabled when the validator objects — `ui/screen/AddComponentDialogs.kt:227, 335,
455, 578, 817` each compute an `ok` expression inline in the composable and pass it to
`Button(enabled = ok, ...)`. Those `ok` expressions are the untested part.

This item is the closest to done. The decision logic — the part where a bug would actually
change behavior — is tested; what's left is asserting the plumbing.

### 3. Preview-tap → adds at correct position

**Behavior: implemented. Coverage: none.**

The pipeline: `ui/drawing/compose/ShaftDrawing.kt:214-248` converts the tap to mm
(`tappedMm = layout.xMmFromPx((pos.x - offset.value.x) / scale.value)`, inverting the
pan/zoom transform applied at draw time); on a miss it calls `onTapAtMm`, which reaches
`ShaftViewModel.setTapAddPosition` (`:542-544`) and snaps via `snapRawPositionMm` (`:2323`).
`ShaftRoute.kt:132-133` then derives the prefill length from `gapToNextAnchorMm` (min 50 mm),
and `ShaftScreen.kt:840-873` opens the add chooser seeded with that start.

Grepping both test source sets for `setTapAddPosition`, `pendingAddPositionMm`,
`gapToNextAnchorMm`, `snapRawPositionMm`, and `onTapAtMm` returns zero hits. `SnapEngineTest`
and `SnapUtilsTest` do exist but cover the general snap engine, not `snapRawPositionMm` or the
tap → pending-position → prefilled-dialog path.

Worth noting: much of this *is* testable without a UI harness. `xMmFromPx`
(`ui/drawing/render/ShaftLayout.kt:37`), `snapRawPositionMm`, and `gapToNextAnchorMm` are all
pure or ViewModel-level and could be unit-tested today. Only the gesture itself needs Compose.

### 4. Carousel scrolls to selected after tap in preview

**Behavior: implemented and correctly wired — the on-device report is confirmed.
Coverage: none.**

The full chain, verified link by link:

1. `ShaftDrawing.kt:229-239` — hit-test finds the component whose
   `[startMmPhysical, endMmPhysical)` span contains `tappedMm`
2. `ShaftDrawing.kt:244-245` — `onTap?.invoke(hitId)`
3. `ShaftPreviewPanel.kt:88` → `ShaftScreen.kt:490` (`onTapComponentId = { onSelectComponentById(it) }`)
4. `ShaftRoute.kt:194` → `vm::selectComponentById`
5. `ShaftViewModel.kt:1112-1114` — sets `_selectedComponentId`
6. `ComponentCarousel.kt:200-209` — `LaunchedEffect(selectedComponentId, rowsSorted)` resolves
   the target index and calls `pagerState.animateScrollToPage(targetIndex)`

The guard at `:204-205` re-scrolls when either the page differs or
`currentPageOffsetFraction != 0f`, so a half-swiped pager still settles onto the selection.
The reverse direction (user swipe updates selection) is handled separately at `:212-229`, with
the `pagerScrollStartedByUser` flag keeping the two effects from fighting each other. The
comment at `:187-190` records that an earlier version of this did fight itself — an
initial-load `scrollToPage` racing the selection-following `animateScrollToPage`, producing
visible jumping. Current structure avoids it.

No test references `selectedComponentId`, `animateScrollToPage`, or `pagerState`. The
index-resolution step (`rowsSorted.indexOfFirst { it.component.id == id }`) is inline in the
effect rather than extracted, so even the non-Compose part isn't independently testable.

---

## Incidental finding: coupler bolt slots aren't tappable

Not part of §5.2, recorded here because it surfaced while tracing item 4.

The preview hit-test at `ShaftDrawing.kt:229-239` runs four passes — `ResolvedBody`,
`ResolvedTaper`, `ResolvedThread`, `ResolvedLiner`. `ResolvedCouplerBoltSlot` is absent.
Consequence: tapping a coupler bolt slot in the preview selects the body underneath it, or if
nothing underlies it, falls through to the add-chooser flow. A slot's carousel card can never
be reached by tapping the slot.

This may well be deliberate — slots are reference-only features that deliberately stay out of
collision, OAL, and body geometry, and a slot always sits on top of something else, so making
it win the hit-test would make the underlying body untappable at that spot. Flagging it as a
product question rather than a defect.

Two other properties of the same handler, both benign but undocumented: the tap uses `pos.x`
only, so vertical position is ignored (a tap well above or below the shaft outline still
selects); and `tappedMm` is passed unclamped, so a tap left of the aft face yields a negative
mm that snapping only corrects if it's within tolerance of `0f` (`SnapUtils.kt:50` filters
anchors to `[0, OAL]`).

---

## Recommendation

Split §5.2 rather than working it as a block. The four items aren't the same kind of work:

**Testable today, no new infrastructure** — pull these out and write them as ordinary JVM
tests this week:
- `snapRawPositionMm` and `gapToNextAnchorMm` (item 3's math, the part most likely to regress)
- `ShaftLayout.xMmFromPx` round-trip against `xPxFromMm`
- extract the carousel's target-index resolution (item 4) and the blur-commit predicate
  (item 1) into pure helpers, then test those

That covers the regression-prone logic in all four items without touching the build.

**Needs a harness decision** — the genuinely gestural and lifecycle parts (real tap dispatch,
focus traversal, pager animation settling). Two options:

- *Robolectric + JVM Compose tests*: add the Robolectric dependency and a `testOptions {
  unitTests { isIncludeAndroidResources = true } }` block, move `ui-test-junit4` to
  `testImplementation`. Runs in CI with no device. This is the better fit given CI already
  runs unit tests and connected tests are gated off.
- *Restore androidTest*: dependencies are already declared, but runs need a device or emulator
  and the opt-in flag, and `3ba9992` is recent evidence that tests reaching into composable
  signatures rot quickly.

Recommend Robolectric, and recommend that whatever UI tests get written assert against
`testTag`s rather than composable parameters — that's the rot the deleted test hit.

---

# What was built

The recommendation above was carried out the same day. Suite went 719 → 796 tests
(77 new), all green.

## The bug this found

**`NumericInputField` fired `onCommit` on every composition** — no user interaction
required. The very first Compose test written (`merely composing the field does not
commit`) caught it.

Cause: Compose delivers an initial `onFocusChanged` callback with `isFocused = false` when
the modifier attaches. The blur branch read its focus baseline as null and, under a
"commit defensively when we have no baseline" rule, committed. So every numeric field in
every carousel card committed its current value once per composition — and the carousel is
a pager, so this fired again for each card swiped into view.

Why it went unnoticed: the committed value is the one already in the model, so both places
that would surface it compare state and absorb it — the dirty gate is a full-snapshot
comparison, and `SessionHistory.record` no-ops on a state equal to its head. But
`ShaftViewModel.updateBody` also calls `rememberBodyDefaults(lengthMm, diaMm)`, which is
**not** state-compared.

Net effect, traced end to end: composing a body card → `updateBody` with the body's own
current values → `rememberBodyDefaults` → `sessionAddDefaults.bodyLenMm` → the Length
prefill in the next tap-to-add Add Body dialog (`ShaftScreen.kt:765`, used when OAL is not
manual or the tap sits past it). The designed rule is "the last length you *typed* becomes
the default"; the bug made it "the last body card that happened to *compose*" — and since
`HorizontalPager` composes pages ahead of the visible one, that can be a card never shown.

On the explicit-body card the scope is narrower than the symptom first suggested:
`bodyDiaMm` is written by `rememberBodyDefaults` but never read — the Add Body dialog's Ø
comes from `rememberAddDialogDefaults`, derived from the spec (first body's diameter). Only
the length prefill was affected. The same composition-time commit also ran `ensureOverall()`
and the equivalent `remember*Defaults` for tapers, threads, liners and slots on their cards.

### The worse case: the auto-body card

The **auto-body** card is where this actually bit, and it is not absorbed by anything.

Its Ø field (`ComponentCarousel.kt:465-467`) is editable and commits to
`onSetAutoBodyDia` → `ShaftViewModel.setAutoBodyDiaMm` → `ShaftSpec.autoBodyDiaMm`. The
field *displays* `component.diaMm`, the **resolved** diameter — which, when
`autoBodyDiaMm == 0` (unset), is the value derived from neighboring components.

So composing an auto-body card fired `setAutoBodyDiaMm(derivedValue)`, and
`setAutoBodyDiaMm` has no no-op guard — it writes unconditionally. That flips
`autoBodyDiaMm` from 0 to non-zero, and per `ResolvedComponent.kt:166` an override > 0
**wins over neighbor derivation**. Consequences, none of them absorbed:

- The bare-shaft Ø silently changes from *derived* to *pinned*. It looks identical at that
  instant (the pinned value equals what was being derived), but the auto spans stop
  following their neighbors from then on — change an adjacent body's Ø and the bare shaft
  no longer tracks it.
- `ShaftSpec` genuinely changed, so this **does** pass the dirty gate: opening a saved
  document and scrolling to an auto-body card marks it dirty, asterisk and all, with no
  user edit. It also records a real undo entry.

This is the most likely explanation for any "document went dirty on its own" behavior, and
it is worth a specific look on-device.

The fix is at the shared level — no composition-time commit fires from any
`NumericInputField` now — so all of these call sites are covered at once, and
`NumericInputFieldBlurTest.merely composing the field does not commit` is the regression
pin for all of them.

Fix: `shouldCommitOnBlur` now requires a non-null focus baseline. A null baseline means
focus was never gained, so there is nothing to commit. Contract updated in
`NumberField.md`, with an explicit "do not restore the defensive-commit rule" note.

## Harness

Compose UI tests now run on the JVM through `testDebugUnitTest` — no device, no emulator,
no opt-in flag:

- `gradle/libs.versions.toml` — `robolectric = "4.16"`
- `app/build.gradle.kts` — `testImplementation` of `robolectric`, `androidx-junit`, the
  Compose BOM and `ui-test-junit4`; `testOptions { unitTests { isIncludeAndroidResources
  = true } }`. `ui-test-manifest` already arrives via `debugImplementation`.
- Tests pin `@Config(sdk = [34], qualifiers = "w400dp-h800dp")`.

The androidTest source set is untouched and still gated behind
`-PallowConnectedAndroidTests=true`.

## Extractions (behavior-preserving)

Logic pulled out of composables so it is testable without a harness at all — and so it
stops being coupled to parameter lists, which is what rotted the deleted androidTest:

| New file | Extracted from |
|---|---|
| `ui/input/BlurCommitPolicy.kt` | the `onFocusChanged` lambda in `NumericInputField` |
| `ui/screen/CarouselSelectionSync.kt` | the two selection/pager `LaunchedEffect`s in `ComponentCarousel` |
| `ui/screen/AddDialogGates.kt` | the five Add-dialog confirm-button `ok` expressions |
| `ui/viewmodel/SnapUtils.kt` (additions) | `snapRawPositionMm` / `gapToNextAnchorMm` / snap tolerance, out of `ShaftViewModel` |

The `ShaftViewModel` methods remain as thin delegates, so no call site changed.

## Tests added

| File | Tests | Item |
|---|---|---|
| `ui/input/BlurCommitPolicyTest` | 8 | 1 |
| `ui/input/NumericInputFieldBlurTest` (Robolectric) | 7 | 1 |
| `ui/screen/AddDialogGatesTest` | 22 | 2 |
| `ui/viewmodel/TapAddPositionTest` | 17 | 3 |
| `ui/drawing/render/ShaftLayoutMappingTest` | 7 | 3 |
| `ui/screen/CarouselSelectionSyncTest` | 16 | 4 |

`NumericInputFieldBlurTest` is the one that needs the harness: it verifies the predicate is
actually *wired into* the field — real focus, blur, IME and re-focus events. A correct
predicate wired to nothing passes the pure test and fails that one, which is precisely how
the composition bug surfaced.

## Deliberately not done

- **No Compose test for the carousel pager.** `ComponentCarouselPager` takes ~35
  parameters, almost all callbacks. A test constructing that is exactly the coupling that
  killed `EditorTopBarExportPdfTest`, and it would mostly be asserting that Compose's
  `HorizontalPager` scrolls. The extracted sync predicates cover our logic; the wiring is a
  mechanical substitution.
- **No Compose test for the Add dialogs.** Same reason — the gates are now pure and
  covered; asserting `Button(enabled = ok)` renders disabled tests Material3, not us.
- **CI was not changed.** See below.

## Open recommendation: CI does not run tests

Worth flagging, because it blunts the point of choosing Robolectric. Neither workflow runs
the suite:

- `.github/workflows/distribute.yml` runs `./gradlew assembleDebug` and ships to Firebase.
- `.github/workflows/merge-on-green.yml` only automerges; it runs no build of its own.

So all 796 tests are local-only. Adding `./gradlew testDebugUnitTest` to `distribute.yml`
before the assemble step would make them gate distribution — which is the natural thing to
want, but it is a policy change to the release pipeline (a red test would block a build),
so it is left as a decision rather than done unasked.
