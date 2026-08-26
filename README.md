# Chat App Task

Android real-time chat application built with Kotlin, Coroutines, Flow, Jetpack Compose, Material 3, Clean Architecture, MVI/UDF, Room, WorkManager, Supabase, and FCM.

## Required Features Implemented

- Deterministic device-based identity (`ANDROID_ID` → stable UUID; no auth UI)
- Profile setup (username + optional profile image)
- Shared chat room
- Text messaging
- Image/video media selection (Android Photo Picker)
- Up to 10 attachments per media message
- Media + optional text messages
- Sender username and profile image on messages
- Message timestamps
- Optimistic send states: `SENDING` / `SENT` / `FAILED`
- Retry and cancel for outgoing sends
- WorkManager reliable / background sending
- Foreground ongoing sending notification
- Supabase Realtime live updates
- Room as Android source of truth
- Scoped media access via Photo Picker (no broad storage permission for selection)
- Older-message pagination
- Cached Room messages shown at startup without a false empty-state flash
- User-friendly Chat error mapping (raw Supabase/HTTP errors not shown to users)

## Architecture

Multi-module Clean Architecture with feature → domain repository → data:

| Layer | Modules (examples) |
| --- | --- |
| App | `:app` |
| Features | `:feature:chat`, `:feature:profile` |
| Domain | `:core:domain` |
| Data / infra | `:data:chat`, `:core:database`, `:core:network`, `:core:common`, `:core:ui` |

- Presentation uses MVI/UDF (immutable UI state, actions, one-time events).
- Room is the Android source of truth; Compose observes Room, not live network payloads.
- Supabase is the remote backend (PostgREST, Storage, Realtime).
- Realtime events are resolved in the repository, then upserted into Room.
- WorkManager runs durable outgoing text and media sends.
- Outgoing chat media is copied into app-owned durable storage before background upload.

Details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/CURRENT_STATUS.md`](docs/CURRENT_STATUS.md).

## Data Flow

**Remote ingest**

`Supabase → repository/data layer → Room → domain → ViewModel → Compose`

**Outgoing send**

`Compose → ViewModel → Room (SENDING) → WorkManager → Supabase → Room reconciliation (SENT / FAILED)`

## Local Setup

1. Clone this repository.
2. Open the project in Android Studio.
3. Configure `local.properties` (see [Supabase Configuration](#supabase-configuration)).
4. Add Firebase `app/google-services.json` (see [Firebase Configuration](#firebase-configuration)).
5. Let Android Studio perform Gradle Sync.
6. Run the `:app` configuration on an Android device or emulator (`minSdk` 26).

Use Android Studio’s configured Gradle JDK. Project sources target Java 11 compatibility (`compileSdk` / `targetSdk` 37).

## Supabase Configuration

The Android client reads these names from the repository-root `local.properties` file (or the same names as environment variables).  
`:core:network` injects them into `BuildConfig` at build time (`core/network/build.gradle.kts`).

Add to **root** `local.properties` (create the file if needed):

```properties
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
SUPABASE_PUBLISHABLE_KEY=...
```

- Do **not** commit `local.properties` (listed in `.gitignore`).
- Do **not** put the service-role key in the Android app or in this repository.
- Missing values fail at runtime with a clear configuration error.

## Firebase Configuration

Incoming chat push uses Firebase Cloud Messaging.

1. Create or open a Firebase project and register this Android app.
2. Download `google-services.json` from the Firebase console.
3. Place it at **`app/google-services.json`**.
4. `app/google-services.json` is listed in `.gitignore`. **Never commit it.**

Firebase **service-account** credentials stay on the server (Supabase Edge Function secrets). They must **never** be committed to this repository or packaged into the APK.

## FCM Backend Setup

Push delivery is automatic: a Database Webhook on `public.messages` **INSERT** calls the `send-chat-push` Edge Function, which sends FCM HTTP v1 data-only messages. The sender does not receive their own push.

Backend pieces (already used by this project):

- Table **`push_registrations`**
- Edge Function **`register-push`** (Android FID registration)
- Edge Function **`send-chat-push`**
- Database Webhook on **`public.messages` INSERT** → **`send-chat-push`**
- Webhook header **`x-chat-push-secret`**

Server-side secret **names** only (set in the Supabase project; never commit values):

- `FIREBASE_PROJECT_ID`
- `FIREBASE_PRIVATE_KEY`
- `FIREBASE_CLIENT_EMAIL`
- `CHAT_PUSH_WEBHOOK_SECRET`

## Supabase Backend Requirements

Backend concepts used by the app:

- **`users`** — profile rows keyed by the local UUID (`id`, `username`, `profile_image_path`, `age`, timestamps)
- **`messages`** — chat rows (`id`, `sender_id`, `text_content`, timestamps); shared room (no per-conversation table)
- **`message_media`** — attachment rows linked to `messages` (`storage_path`, `media_type`, `mime_type`, `position`, optional size/dimensions)
- **Storage** — profile and chat media objects
- **Realtime** — live change notifications for message tables
- **`create_media_message` RPC** — creates a media message and its media rows atomically after Storage uploads
- **RLS / policies** — must allow the **anon** key to perform the reads/writes/uploads the client uses (no Supabase Auth)

Full setup notes: [`docs/BACKEND_SUPABASE.md`](docs/BACKEND_SUPABASE.md).

## Storage Buckets

Required bucket names (exact):

| Bucket | Purpose | Access model |
| --- | --- | --- |
| `chat-media` | Chat image/video objects (`{messageId}/{mediaId}.{ext}`) | **Public** |
| `profile-images` | Profile avatars (`{userId}/avatar.{ext}`) | **Public** |

The app resolves display URLs with Supabase Storage **public URL** APIs. Both buckets must be **PUBLIC** for the current implementation.

## Realtime Configuration

Enable Realtime for:

- `public.messages` — `INSERT` and `UPDATE`
- `public.message_media` — `INSERT` and `UPDATE`

(The client does not rely on `DELETE` events.)

Realtime delivers ID-oriented change signals. The repository fetches the full message (+ media) and upserts Room. Compose continues to observe Room only.

## Media Access and Permissions

- Profile and chat media selection uses the Android system Photo Picker.
- Broad/full storage access (`READ_MEDIA_*` / legacy external storage) is not required for selection.
- On send, outgoing chat media is copied into app-owned durable files under the app’s private storage so WorkManager can upload after the picker URI may no longer be available.
- Android 13+ may prompt for notification permission so ongoing send notifications can appear; denial does not block persist/send.

## Reliable Sending

1. Outgoing message (and media metadata) is persisted locally as `SENDING`.
2. Unique WorkManager work runs with a connected-network constraint and backoff retry.
3. A foreground / ongoing “sending” notification is shown while work executes (Cancel from notification; Retry from Chat UI after `FAILED`).
4. Success reconciles Room to `SENT`; terminal failure marks `FAILED`.
5. Media uploads use durable local copies and the same message/media UUIDs for idempotent retry (`Storage` upsert + existing remote row reconciliation).

## Offline / Local-First Behavior

- Room is the Android source of truth for the conversation UI.
- Cached messages can render before remote refresh finishes.
- Initial sync, pagination, and Realtime update Room; they do not replace Room as the UI source.
- Temporary network failures leave existing Room rows in place.
- Technical backend/HTTP exceptions are mapped to friendly Chat snackbar strings.

Outgoing work still depends on WorkManager + connectivity for remote delivery; this is local-first persistence with reliable background send, not a claim of full offline multi-device chat semantics.

## Testing

Automated coverage is primarily **JVM unit tests**, including:

- Chat / Profile ViewModels and Chat UI error mapping
- Chat repository / media-upload failure classification
- Text and media send workers and schedulers
- Outgoing media store and public-URL path helpers
- Deterministic `ANDROID_ID` identity store

There is a template `ExampleInstrumentedTest` only; product instrumentation / UI tests are **not** implemented (see Bonus).

## Manual Verification

Development verification on this project has covered:

- Two device / emulator instances
- Live text and media Realtime
- Older-message pagination
- Retry / cancel
- App restart persistence
- Background / offline send recovery
- Sender identity (username, avatar, timestamp)
- Cached Room startup without a false empty-state flash
- Incoming FCM when the app is in background or killed
- Foreground Chat suppresses incoming notifications
- Sender does not receive their own push
- Notification tap opens the existing Chat screen
- Incoming notifications are grouped / expandable

## Bonus Features

| Item | Status |
| --- | --- |
| Exceptional visual polish / delightful interactions | Implemented |
| FCM push notifications | Implemented |
| Audio / voice messages | Not implemented |
| Instrumentation / UI tests | Not implemented (template only) |

## Git Workflow

- `feature/*` — isolated feature implementation
- `development` — integration branch for completed features
- `staging` — final integrated verification / pre-release before stable promotion
- `master` — stable / default submission branch

Promotion path: `feature/*` → `development` → `staging` → `master`.

Details: [`docs/WORKFLOW_AND_GIT.md`](docs/WORKFLOW_AND_GIT.md).

## Additional Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture boundaries
- [`docs/BACKEND_SUPABASE.md`](docs/BACKEND_SUPABASE.md) — Supabase backend setup
- [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) — product / tech context
- [`docs/PRODUCT_REQUIREMENTS.md`](docs/PRODUCT_REQUIREMENTS.md) — product requirements
- [`docs/WORKFLOW_AND_GIT.md`](docs/WORKFLOW_AND_GIT.md) — workflow and Git
- [`docs/CURRENT_STATUS.md`](docs/CURRENT_STATUS.md) — implemented project state
- [`docs/NEXT_STEPS.md`](docs/NEXT_STEPS.md) — remaining submission actions
- [`docs/README.md`](docs/README.md) — docs index for deeper context

## Submission

The assignment expects a **public** GitHub repository that includes complete source code, this README, architecture documentation, and API/configuration instructions.

Before emailing the recruiter, ensure the GitHub repository visibility is set to **Public** and share the repository link.
