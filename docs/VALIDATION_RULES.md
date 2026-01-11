# Validation Rules  
Version: v0.4.x

## Purpose
This document defines all validation behavior used by ShaftSchematic.  
Validation ensures data consistency, machining plausibility, and clean export conditions—without restricting legitimate edge-case layouts.

Validation is performed **only in the ViewModel**.  
Neither the Layout Engine nor the Renderer performs validation.

---

# 1. Validation Categories

Validation is divided into two tiers:

## 1.1 Blocking Errors (Hard Fail)
These prevent:
- Saving a component
- Committing edits in dialogs
- Exporting to PDF

Examples:
- Negative lengths or diameters
- startFromAft < 0
- endFromAft > overallLengthMm
- Missing taper parameters (SET/LET/taperRate insufficient)
- Invalid thread pitch/tpi values (NaN, Infinity)
- Derived taper diameters < 0
- overallLengthMm < coverageEndMm

Blocking means **the dialog stays open** and the change is not accepted.

---

## 1.2 Non-Blocking Warnings (Soft Issues)
These allow saving and exporting but are visually flagged.

Examples:
- Component overlaps
- Very small features (e.g., extremely short bodies)
- Zero-pitch threads
- Large diameter discontinuities between components
- Liner OD ≤ shaft body diameter
- Small free-to-end space (<10 mm)

Warnings appear in the component list or via icons; they do **not** block workflow.

---

# 2. Numeric Input Validation

Validation of raw input values occurs during commit-on-blur inside dialogs or number fields.

## 2.1 Accepted Input Patterns
- `"123"`
- `"123."`
- `".25"`
- `"0.5"`
- `""` → interpreted as **0**
- `"."` → interpreted as **0**

## 2.2 Invalid Input
If user commits:
- alphabetic text,
- multiple decimals,
- malformed numbers,

…then:
1. The change is **not committed**, and  
2. UI reverts to last valid committed value.

## 2.3 Safety Filters
Reject immediately (blocking):
- `Float.NaN`
- `Float.POSITIVE_INFINITY`
- `Float.NEGATIVE_INFINITY`
- Negative physical values

Reject values above a sanity maximum:
value > 100000f → blocking

yaml
Copy code

---

# 3. Component-Level Validation

All components share core validation constraints.

## 3.1 Shared Rules for All Segments
startFromAftMm >= 0
lengthMm >= 0
endFromAftMm <= overallLengthMm

yaml
Copy code
If any part is violated → **blocking**.

---

## 3.2 Body Validation
- diaMm ≥ 0 (blocking)
- diaMm may be 0 (degenerate visual case, but allowed)

Non-blocking warning:
- Zero-length body
- Diameter discontinuity vs neighbors

---

## 3.3 Taper Validation
Required:
- lengthMm > 0
- startDiaMm ≥ 0
- endDiaMm ≥ 0

Keyway (taper-hosted feature):
- keywayLengthMm ≥ 0 (blocking)
- keywayLengthMm ≤ taper.lengthMm (blocking)
- Spoon is optional and non-blocking.
- Spoon is allowed only when a keyway exists:
	- If keywayLengthMm == 0, keywaySpooned must be false (blocking)

Missing keyway data is valid (all keyway fields may be 0/false).

TODO: Body keyway validation not yet implemented.

### Taper Rate Behavior
- If **both** SET & LET provided → taperRate ignored  
- If **one** diameter missing → taperRate is required  
- Derived diameter must be ≥ 0  
- Missing both SET and LET with no taperRate → **blocking**

Non-blocking warnings:
- Extremely steep tapers
- Large mismatch with adjacent body diameter

---

## 3.4 Thread Validation
Required:
- majorDiaMm ≥ 0 (blocking)
- lengthMm ≥ 0 (blocking)

### Pitch ↔ TPI Rules
Normalization rules:
- If pitch present & tpi missing → compute tpi
- If tpi present & pitch missing → compute pitch
- If both present → leave unchanged, but validate consistency
- If neither present → blocking

Invalid cases (blocking):
- conversion leads to NaN
- conversion leads to Infinity
- tpi or pitch < 0

Non-blocking warnings:
- pitchMm = 0 (thread rendered flat, allowed)

---

## 3.5 Liner Validation
Required:
- odMm ≥ 0 (blocking)
- endFromAftMm ≤ overallLengthMm (blocking)

Non-blocking warnings:
- odMm < underlying shaft diameter
- Very thin liner vs body diameter

---

# 4. ShaftSpec-Level Validation

### 4.1 Global Requirements
- overallLengthMm ≥ 0 (blocking)
- coverageEndMm ≤ overallLengthMm (blocking)

### 4.2 Full-Spec Blocking Errors
- Any component in an invalid state
- Invalid numeric values
- Invalid taper derivation parameters
- Unbounded thread or liner

### 4.3 Full-Spec Non-Blocking Warnings
- Component overlaps (machinist may intend)
- Rapid diameter changes
- Tiny segments (e.g., < 1 mm)
- Free-to-end space < 10 mm
- Zero-body coverage (shaft with only linters/tapers/threads)

---

# 5. Overlap Rules

Overlaps **never** block validation.

Allowed with warnings:
- Body ↔ Taper
- Body ↔ Liner
- Body ↔ Threads
- Taper ↔ Thread  
- Thread ↔ Liner (rare but permissible)

Reasoning: marine machining workflows often use stacked geometry, nested regions, or overlapping descriptive elements.

---

# 6. Export Validation (PDF)

Before exporting:
1. ViewModel runs full validation.
2. If **any blocking error** exists → cancel export.
3. If only warnings remain → export continues.

PDF export does not interpret warnings; UI handles presentation.

---

# 7. Validation Invariants (Required)

1. Validation occurs **only** before state update or export.
2. Renderer/Layout must never throw validation errors.
3. UI never performs validation logic beyond string formatting.
4. Warnings do not affect behavior, only UI hints.
5. Blocking errors prevent both save and export.
6. Derivation (taper rate, pitch/tpi) is validated before application.

---

# 8. Summary

Validation ensures:
- Consistency of geometric data  
- Prevention of impossible machining configurations  
- A clean PDF export state  
- Freedom for machinists to intentionally create overlaps or complex geometry  

Blocking errors prevent data corruption.  
Warnings inform but do not restrict the workflow.

This document defines the authoritative validation system used across all components and the entire ShaftSpec.