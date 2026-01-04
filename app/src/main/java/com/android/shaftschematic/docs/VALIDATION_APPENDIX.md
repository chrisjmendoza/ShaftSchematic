# Validation Appendix  
Version: v0.4.x  
Companion to: VALIDATION_RULES.md

This appendix provides a condensed, high-speed reference to all validation behavior in ShaftSchematic.  
Use this when implementing, debugging, or extending component logic.

---

# 1. Validation Matrix (Authoritative)

This table categorizes every validation rule by:
- **Scope** (Input → Component → Spec → Export)
- **Severity** (Blocking / Warning)
- **Behavior** (Reject, Coerce, Allow)

---

## 1.1 Component Parameter Validation

| Condition | Applies To | Severity | Behavior |
|----------|------------|----------|----------|
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

## 1.2 ShaftSpec Structural Validation

| Condition | Severity | Behavior |
|----------|----------|----------|
| overallLengthMm < 0 | Blocking | Reject |
| coverageEndMm > overallLengthMm | Blocking | Reject |
| No bodies present | Warning | Allow (valid for some repairs) |
| Very small free-to-end | Warning | Allow |

---

## 1.3 Export Validation (PDF)

| Condition | Severity | Behavior |
|----------|----------|----------|
| Any blocking component error | Blocking | Cancel export |
| Overlaps | Warning | Export anyway |
| Zero-pitch threads | Warning | Export anyway |
| Thin liners | Warning | Export anyway |

PDF export is the same geometry engine as preview; no special interpretation.

---

# 2. Numeric Input Rules (Quick Reference)

## 2.1 Conversions on commit

| Input | Result |
|-------|--------|
| `""` | 0f |
| `"."` | 0f |
| `"5."` | 5f |
| `".25"` | 0.25f |
| Malformed (`"5..2"`, `"abc"`) | Revert to last valid |

---

## 2.2 Illegal numeric values

| Value | Severity | Behavior |
|-------|----------|----------|
| NaN | Blocking | Reject |
| Infinity | Blocking | Reject |
| >100000f | Blocking | Reject |

---

# 3. Derivation Rules (Authoritative)

## 3.1 Taper

### If both SET & LET present  
→ taperRate ignored.

### If one diameter missing  
- taperRate must be valid
- derivedDia = computeFromRate()
- derivedDia must be ≥ 0

### If both SET & LET absent  
→ **blocking error**

---

## 3.2 Threads

| Provided | Action |
|----------|--------|
| pitch only | compute tpi |
| tpi only | compute pitchMm |
| both | leave both, but validate |
| neither | blocking error |

Conversion rule:
pitchMm = 25.4f / tpi
tpi = 25.4f / pitchMm

yaml
Copy code

Reject if conversion invalid.

---

# 4. Overlap Logic (Fast Rules)

Overlaps **never** block.

| Overlap Case | Valid? | Severity |
|---------------|--------|----------|
| Body ↔ Taper | Yes | Warning |
| Body ↔ Liner | Yes | Warning |
| Body ↔ Threads | Yes | Warning |
| Taper ↔ Threads | Yes | Warning |
| Threads ↔ Liner | Yes | Warning |

Reason: machinists often overlay descriptive geometry.

Renderer draws components in a fixed order; overlaps are visual, not structural errors.

---

# 5. ViewModel Validation Pipeline

### 1. Input commit
- Clean raw text → parse → numeric validation
- If blocking → reject

### 2. Component assembly
- Check fields for physical validity
- Apply derivation (taper/thread)
- If blocking → show inline dialog error

### 3. ShaftSpec rebuild
- Validate structural constraints
- Compute warnings
- Commit state

### 4. Export request
- Run full validation again
- If blocking → cancel export
- If warnings → show advisory but allow user to continue

---

# 6. Invariants (Absolute Rules)

1. **Only ViewModel validates.**  
   UI never holds business logic; Layout/Renderer never validate.

2. **All values are mm before validation.**  
   No unit conversions inside the model or renderer.

3. **Blocking = reject state mutation.**  
   Component must not be added/edited.

4. **Warnings never block.**  
   They exist only for machinist awareness.

5. **Export cannot bypass validation.**

---

# 7. Debugging Checklist

When you see unexpected behavior:

- Check numeric parsing first  
- Check taper derivation (common failure)  
- Check thread pitch/tpi conversion  
- Check overallLength vs coverageEnd  
- Ensure UUID stability across edited components  
- Ensure dialogs are not committing partial values  

---

# 8. Appendix Usage
This appendix is a high-speed reference intended for:

- Implementing new components  
- Writing unit tests  
- Debugging malformed specs  
- Ensuring new UI flows follow validation rules  
- Future contributors needing clarity without reading entire docs suite  

This appendix summarizes the entire validation ecosystem in a single, scannable reference.