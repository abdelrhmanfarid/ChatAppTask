# Development Workflow

## AI assistant workflow

Before editing:

1. Read the relevant docs in `docs/`.
2. Inspect only the relevant module/files.
3. Confirm current implementation against repository code.
4. Make the smallest coherent change.
5. Build/test the affected scope.
6. Report exactly what changed.

Do not repeatedly summarize the entire repository.

## Task discipline

For each task:
- identify affected module(s)
- identify existing state/API first
- preserve architecture
- avoid unrelated cleanup
- avoid speculative refactors
- do not silently add product requirements

## Git

Branch flow used by this project:

- `feature/*` — isolated feature implementation
- `development` — integration branch for completed features
- `staging` — final integrated verification / pre-release before stable promotion
- `master` — stable / default submission branch

Promotion path: `feature/*` → `development` → `staging` → `master`.

Do not commit or push unless explicitly instructed.

When asked to prepare a change:
- keep edits focused
- report modified files
- report verification commands/results
- call out any unverified assumptions

## Documentation maintenance

When a durable project decision changes, update the relevant `docs/*.md`.

Do not update `CURRENT_STATUS.md` for tiny implementation details.

Update it when:
- a phase finishes
- a major component lands
- architecture changes
- backend contract changes
- a new major feature starts
