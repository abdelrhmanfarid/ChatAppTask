# Android Implementation Rules

## Kotlin

- Prefer clear idiomatic Kotlin.
- Avoid clever abstractions that reduce readability.
- Keep nullability explicit and meaningful.
- Do not use blocking work on the main thread.
- Preserve structured concurrency.

## Compose

Use standard Material 3 and Compose primitives whenever possible.

Preferred building blocks include:
- `Scaffold`
- Material 3 app bars
- `LazyColumn`
- `Surface`
- `Row`
- `Column`
- `TextField` / `OutlinedTextField`
- `Button`
- `IconButton`
- `CircularProgressIndicator`
- `Snackbar`

## Reusability

Extract a reusable composable when:
- it appears in multiple screens/states, or
- it represents a coherent reusable UI concept, or
- extraction materially improves readability/testing.

Likely reusable components:
- profile avatar/avatar picker
- primary action button
- profile text fields
- chat top app bar
- message bubble
- message status
- chat composer
- offline banner
- common loading state

Do **not** extract every `Row`, `Text`, or small one-off layout into a generic composable.

## State

- ViewModels own screen state.
- UI is a pure renderer of state as much as practical.
- User interactions become actions/events.
- Avoid hidden mutable UI business state.
- Use lifecycle-aware state collection.

## Android-native behavior

Do not reproduce iOS-only visual patterns from design references.

Avoid:
- Dynamic Island
- iOS status/navigation bar assumptions
- Cupertino controls
- iOS spacing conventions copied literally

Use:
- Android window insets
- Material 3 touch targets
- Android system bars
- IME-safe composer behavior
- Android system media picker later

## Accessibility

- minimum practical touch target: 48dp
- readable contrast
- meaningful content descriptions where appropriate
- do not rely on color alone for message failure/status
