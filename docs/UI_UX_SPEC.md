# UI / UX Specification

## Visual direction

Modern, minimal, polished Material 3 Android UI.

Primary design reference came from a Figma Make concept, but the visible iPhone/device frames are **presentation only** and must never be implemented.

## Initial palette direction

- Primary: `#5B5FEF`
- On Primary: `#FFFFFF`
- Primary Container: light indigo
- Background: `#FAFAFC`
- Surface: `#FFFFFF`
- Surface Variant: `#F1F1F6`
- Primary text: dark charcoal
- Secondary text: muted gray
- Error: Material-style error red

Final values should be mapped into a proper Material 3 color scheme and checked for contrast.

## Spacing

Use a simple 4dp-based scale:

- 4dp
- 8dp
- 12dp
- 16dp
- 24dp
- 32dp

## Shape direction

- small: about 8dp
- medium: about 12dp
- large: about 16dp
- message bubbles: about 18–20dp
- avatars/send action: circular

## Startup

Use Android's system SplashScreen API for the actual OS splash.

If initialization/profile resolution remains visible after system splash, show a lightweight startup/resolving screen with:
- app icon
- app name
- short supporting copy
- small Material progress indicator

Do not turn it into authentication/onboarding.

## Profile setup/edit

Shared visual components:
- avatar selector
- username field
- age field
- primary action button

Creation and editing should intentionally reuse these components.

## Empty chat

Show:
- app bar
- centered simple empty-state icon
- `Start the conversation`
- short supporting text
- active composer

## Messages

### Incoming
- left aligned
- neutral/light surface
- dark text

### Outgoing
- right aligned
- primary surface
- white/on-primary text

No custom speech-bubble tails.

### Sending
- keep the normal outgoing bubble
- show a small progress indicator near metadata/status

### Sent
- subtle single check/status marker
- do not invent delivered/read states

### Failed
- keep the message visible
- show an error icon/text such as `Failed · Tap to retry`
- make retry target easy to tap
- do not use a modal dialog

### Retry
Retry returns the UI to the same `SENDING` representation.

## Offline

Use a compact non-blocking banner below the app bar.

Example meaning:
`You're offline — messages will send when you're connected.`

Keep messages and composer usable.

## Loading

Use full/central loading only when there is genuinely no local content yet.

If Room already has messages, continue displaying them while background sync happens.

## Composer

Concept:
`[attachment] [message field] [send]`

Requirements:
- keyboard-safe
- multiline-capable
- attachment is future placeholder
- send visually enabled only when appropriate
- Material 3 touch targets
