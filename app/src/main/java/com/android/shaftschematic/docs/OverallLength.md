# Overall Length — Auto vs Manual (v1.1)

## Scope
Defines how the **Overall Length** field behaves, how it interacts with components, and how the UI signals errors. Applies to `ShaftViewModel`, `ShaftRoute`, and `ShaftScreen`.

---

## UX Summary
- **Default**: Overall starts at **0** with a **ghost “0”** placeholder in the input.
- **Auto mode** (default): Overall automatically grows to the **last occupied end** of all components.
- **Manual lock**: When the user types a value and commits (IME Done or blur), Overall switches to **manual** and holds that exact value.
- **Unlock**: Clearing the field (empty) switches back to **auto** and snaps Overall to the current last occupied end.
- **Oversize**: If components extend past a **manual** Overall, the input shows an **error** and the Free-to-End badge turns **red** with a **negative** value.

---

## Business Rules
- Canonical units are **millimeters** (mm). All computations are in mm; formatting happens at the UI edge.
- Auto growth is **one-way**: It never shrinks below the last occupied end while in auto mode.
- Manual mode **disables auto growth** completely until the user clears the field or edits it again.
- Oversize is allowed but surfaced as an error state (no automatic correction).

---

## Computation

### Last occupied end (mm)
```
/** Latest occupied end (in mm) from all components. */
fun lastOccupiedEndMm(spec: ShaftSpec): Float {
    var end = 0f
    spec.bodies.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    return end
}
