# Android Architecture

## Architectural style

The project uses:

- Clean Architecture
- MVI / UDF
- modular Gradle structure
- Room-backed local state
- WorkManager for reliable sends

## Expected responsibility boundaries

### Presentation

Responsible for:
- Compose UI
- UI state rendering
- user actions
- collecting ViewModel state/events
- navigation integration

Presentation should not:
- call Supabase directly
- contain database logic
- contain WorkManager orchestration details
- duplicate domain rules

### Domain

Responsible for:
- domain models
- use cases where useful
- repository contracts
- business rules

Domain should remain independent of Android UI and concrete data sources.

### Data

Responsible for:
- repository implementations
- Room/local persistence
- Supabase/network access
- mapping data models to domain models
- synchronization

### Local-first messaging

Room is the Android source of truth.

Preferred flow:

1. User presses Send.
2. Persist optimistic message locally with `SENDING`.
3. UI observes Room and displays it immediately.
4. Reliable send work executes.
5. On success, local state becomes `SENT`.
6. On failure, local state becomes `FAILED`.
7. Retry moves message back through send processing.

Avoid making the UI depend directly on a network response before showing the message.

## Dependency discipline

Keep module dependencies minimal.

Do not add direct dependencies just because a transitive API is convenient.

Before changing module relationships:
1. inspect the relevant Gradle files
2. preserve dependency direction
3. avoid leaking data-layer dependencies upward
