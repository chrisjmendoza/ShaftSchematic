# InlineAddChooserDialog – Contract v1.0

## Purpose
Modal chooser to add a new component to the current ShaftSpec. Lives outside `ShaftScreen` so screen refactors do not remove it.

## Responsibilities
- Present five primary actions: Add **Body**, **Liner**, **Taper**, **Thread**, **Coupler Bolt Slot**.
- Invoke the corresponding callback and then dismiss.
- Provide a “Close” button that only dismisses.

## API (lambda-based, stable)
```
@Composable
fun InlineAddChooserDialog(
  onDismiss: () -> Unit,
  onAddBody: () -> Unit,
  onAddLiner: () -> Unit,
  onAddThread: () -> Unit,
  onAddTaper: () -> Unit,
  onAddCouplerBoltSlot: () -> Unit
)
