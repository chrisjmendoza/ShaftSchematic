# Validation Appendix (Future Design)
Version: v0.5 (2026-02-01)
Companion to: VALIDATION_RULES.md

**This document describes potential future validation architecture. It is not a binding contract for current behavior.**

This appendix is a condensed, forward-looking reference for how validation could be structured in a future release.

---

# 1. Validation Matrix (Future / Illustrative)

This table illustrates how validation could be categorized by:
- Scope (Input → Component → Spec → Export)
- Severity (Blocking / Warning)
- Behavior (Reject, Coerce, Allow)

---

## 1.1 Component Parameter Validation (Intended)

| Condition | Applies To | Intended Severity | Intended Behavior |
|----------|------------|-------------------|-------------------|
| startFromAft < 0 | All components | Blocking | Reject update |
| lengthMm < 0 | All | Blocking | Reject |
| endFromAftMm > overallLengthMm | All | Blocking | Reject |
| Negative diameters | Body/Taper/Threads/Liner | Blocking | Reject |
| Zero-length component | All | Warning | Allow |
| Missing taper SET+LET+taperRate | Taper | Blocking | Reject |
| Derived taper diameter < 0 | Taper | Blocking | Reject |
| Missing pitch & missing tpi | Threads | Blocking | Reject |
| Pitch ↔ TPI conversion yields NaN/∞ | Threads | Blocking | Reject |
| Liner OD < shaft OD | Liner | Warning | Allow |
| Taper extremely steep | Taper | Warning | Allow |
| Diameter jump (body→taper→body) | Body/Taper | Warning | Allow |

---

## 1.2 ShaftSpec Structural Validation (Intended)

| Condition | Intended Severity | Intended Behavior |
|----------|-------------------|-------------------|
| overallLengthMm < 0 | Blocking | Reject |
| coverageEndMm > overallLengthMm | Blocking | Reject |
| No bodies present | Warning | Allow (valid for some repairs) |
| Very small free-to-end | Warning | Allow |

---

## 1.3 Export Validation (PDF) (Intended)

| Condition | Intended Severity | Intended Behavior |
|----------|-------------------|-------------------|
| Any blocking component error | Blocking | Cancel export |
| Overlaps | Warning | Export anyway |
| Zero-pitch threads | Warning | Export anyway |
| Thin liners | Warning | Export anyway |

PDF export is the same geometry engine as preview; no special interpretation.

---

# 2. Numeric Input Rules (Quick Reference, Intended)

## 2.1 Conversions on commit

| Input | Result |
|-------|--------|
| "" | 0f |
| "." | 0f |
| "5." | 5f |
| ".25" | 0.25f |
| Malformed ("5..2", "abc") | Revert to last valid |

---

## 2.2 Illegal numeric values

| Value | Intended Severity | Intended Behavior |
|-------|-------------------|-------------------|
| NaN | Blocking | Reject |
| Infinity | Blocking | Reject |
| >100000f | Blocking | Reject |

---

# 3. Derivation Rules (Intended)

## 3.1 Taper

### If both SET & LET present
→ taperRate ignored.

### If one diameter missing
- taperRate would need to be valid
- derivedDia = computeFromRate()
- derivedDia would need to be ≥ 0

### If both SET & LET absent
→ intended blocking error

---

## 3.2 Threads (Intended)

| Provided | Action |
|----------|--------|
| pitch only | would compute tpi |
| tpi only | would compute pitchMm |
| both | would leave both, but validate |
| neither | blocking error |

Conversion rule:
- pitchMm = 25.4f / tpi
- tpi = 25.4f / pitchMm

Reject if conversion invalid (intended).

---

# 4. Overlap Logic (Intended)

Overlaps would not block (intended).

| Overlap Case | Valid? | Intended Severity |
|---------------|--------|------------------|
| Body ↔ Taper | Yes | Warning |
| Body ↔ Liner | Yes | Warning |
| Body ↔ Threads | Yes | Warning |
| Taper ↔ Threads | Yes | Warning |
| Threads ↔ Liner | Yes | Warning |

Reason: machinists often overlay descriptive geometry.

Renderer draws components in a fixed order; overlaps are visual, not structural errors.

---

# 5. ViewModel Validation Pipeline (Intended)

### 1. Input commit
- Clean raw text → parse → numeric validation
- If blocking → reject (intended)

### 2. Component assembly
- Check fields for physical validity
- Apply derivation (taper/thread)
- If blocking → show inline dialog error (intended)

### 3. ShaftSpec rebuild
- Validate structural constraints
- Compute warnings (intended)
- Commit state

### 4. Export request
- Run full validation again (intended)
- If blocking → cancel export (intended)
- If warnings → show advisory but allow user to continue (intended)

---

# 6. Invariants (Intended)

1. ViewModel would validate.
   UI would not hold business logic; Layout/Renderer would not validate.

2. All values are mm before validation.
   No unit conversions inside the model or renderer.

3. Blocking = reject state mutation.
   Component would not be added/edited.

4. Warnings never block.
   They would exist only for machinist awareness.

5. Export cannot bypass validation.

---

# 7. Debugging Checklist (When Implemented)

- Check numeric parsing first
- Check taper derivation (common failure)
- Check thread pitch/tpi conversion
- Check overallLength vs coverageEnd
- Ensure UUID stability across edited components
- Ensure dialogs are not committing partial values

---

# 8. Appendix Usage

This appendix is a high-speed reference intended for future implementation work:
- Implementing new components
- Writing unit tests
- Debugging malformed specs
- Ensuring new UI flows follow validation rules
- Future contributors needing clarity without reading entire docs suite
