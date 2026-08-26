# Product Requirements

## First-use flow

`App launch -> local UUID exists/generated -> profile lookup -> profile setup if missing -> chat`

The UUID is identity only, **not authentication**.

## Required UI states

- Startup / initialization
- Profile creation
- Profile validation
- Profile saving
- Profile viewing
- Profile editing
- Empty chat
- Chat with messages
- Text composer
- Sending message
- Sent message
- Failed message
- Retry interaction
- Initial loading
- Offline state

## Chat behavior

The chat screen should support:

- Material 3 top app bar
- profile access
- message list
- incoming/outgoing message distinction
- timestamps where useful
- text composer
- send button
- attachment placeholder
- `SENDING` indicator
- `FAILED` indicator
- tap-to-retry
- keyboard-safe layout

The message list should map naturally to Compose `LazyColumn`.

## Offline behavior

Offline state must not become a blocking full-screen error.

Expected UX:
- keep local messages visible
- keep composer enabled
- show compact offline information
- allow locally queued sending
- background delivery can occur when connectivity returns

## Future media compatibility

Future support should allow:

- selecting multiple images/videos
- grouped media messages
- media upload progress

Do not complicate the current text-only UI in anticipation of those features.

## Product consistency rules

Do not invent unsupported product behavior.

Examples:
- Do not show `online` presence unless presence is implemented.
- Do not show `Member since` unless such data exists.
- Do not invent read receipts if the domain model only supports `SENT`.
- Do not impose arbitrary age rules unless defined by requirements/domain logic.
- Do not make profile image mandatory unless the product requirement explicitly says so.
