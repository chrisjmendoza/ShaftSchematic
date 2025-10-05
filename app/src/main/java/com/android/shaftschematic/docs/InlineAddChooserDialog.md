# InlineAddChooserDialog – Contract v1.0

## Purpose
Modal chooser to add a new component to the current ShaftSpec. Lives outside `ShaftScreen` so screen refactors do not remove it.

## Responsibilities
- Present four primary actions: Add **Body**, **Liner**, **Thread**, **Taper**.
- Invoke the corresponding callback and then dismiss.
- Provide a “Close” button that only dismisses.

## API (lambda-based, stable)
```kotlin
@Composable
fun InlineAddChooserDialog(
  onDismiss: () -> Unit,
  onAddBody: () -> Unit,
  onAddLiner: () -> Unit,
  onAddThread: () -> Unit,
  onAddTaper: () -> Unit
)
