# Supabase Backend Decisions

## Services

Use Supabase for:

- database
- realtime where needed
- storage for media
- Kotlin SDK access

Do **not** use Supabase Auth for the current project.

Firebase is not the primary backend. FCM is used only for incoming chat push notifications (Bonus #2).

## Identity

The Android app owns a locally generated UUID (deterministic from `ANDROID_ID`).

The backend user record is associated with that UUID.

This should not be represented to the user as authentication.

## Profile

Known profile data (`users` table):

- `id` (UUID)
- `username`
- `profile_image_path` (bucket-relative Storage path, or null)
- `age` (optional)
- `created_at` / `updated_at`

## Messaging

Implemented messaging uses a single shared room (no conversation/room table).

### `messages`

- `id` (UUID; client-supplied for idempotent inserts)
- `sender_id` (UUID → `users.id`)
- `text_content` (nullable for media-only messages; required for text-only inserts)
- `created_at` / `updated_at`

Text messages are inserted via PostgREST insert of `id`, `sender_id`, and `text_content`.

### `message_media`

- `id` (UUID)
- `message_id` (UUID → `messages.id`)
- `storage_path` (bucket-relative path under `chat-media`)
- `media_type` / `mime_type`
- `position` (ordering within the message)
- optional `size_bytes`, `width`, `height`
- `created_at`

Media messages are created through the `create_media_message` RPC after objects are uploaded to Storage (not via a plain message insert alone).

The Android app preserves local-first behavior even when backend/network work is delayed or unavailable.

## Storage buckets

Exact bucket names required by the Android client:

| Bucket | Object path pattern | Read model |
| --- | --- | --- |
| `chat-media` | `{messageId}/{mediaId}.{ext}` | Public URL |
| `profile-images` | `{userId}/avatar.{ext}` | Public URL |

Both buckets must be configured **PUBLIC**. The app resolves display URLs with Supabase Storage public URL APIs (`publicUrl`). Signed-URL-only private buckets are not compatible with the current implementation.

Uploads use the anon key with `upsert = true` on deterministic paths so retries can overwrite the same object.

## Realtime

Enable Realtime (publication) for:

- `public.messages` — `INSERT`, `UPDATE`
- `public.message_media` — `INSERT`, `UPDATE`

The Android client does not depend on `DELETE` events.

Realtime is not the UI source of truth: events trigger a repository fetch of the full message (+ media), then Room upsert; Compose observes Room.

## RPC: `create_media_message`

After all chat-media objects for a message are uploaded, the client calls RPC `create_media_message` with parameters:

- `p_message_id` (UUID)
- `p_sender_id` (UUID)
- `p_text_content` (nullable text)
- `p_media` (JSON array of media items: `id`, `storage_path`, `media_type`, `mime_type`, `position`, optional `size_bytes` / `width` / `height`)

The RPC must persist the `messages` row and related `message_media` rows and return the message UUID. The Android client then reloads that message via PostgREST.

## RLS / policies

There is no Supabase Auth. The Android app uses the **anon** key only.

Policies (or equivalent open-anon task configuration) must allow the anon role to:

- select / upsert `users`
- select / insert `messages`
- select `message_media`
- execute `create_media_message`
- upload (and public-read) objects in `chat-media` and `profile-images`

Do not put the service-role key in the Android app or commit it to this repository.

## FCM push (Bonus #2)

Incoming chat pushes use FCM HTTP v1 from Supabase, not from the Android APK:

- Table `push_registrations`
- Edge Function `register-push`
- Edge Function `send-chat-push`
- Database Webhook on `public.messages` INSERT → `send-chat-push`
- Webhook header `x-chat-push-secret`

Server-side secret names only (`FIREBASE_PROJECT_ID`, `FIREBASE_PRIVATE_KEY`, `FIREBASE_CLIENT_EMAIL`, `CHAT_PUSH_WEBHOOK_SECRET`). Firebase service-account credentials remain server-side and must never be committed or included in the APK.

Android client setup: see the repository README.

Exact SQL for tables, RPC body, and policies is maintained in the Supabase project; keep Android DTOs (`UserDto`, `MessageDto`, `MessageMediaDto`, `CreateMediaMessageParams`) aligned when changing the backend.

## Backend change policy

Do not redesign tables, RLS, storage, or realtime behavior from an Android/UI task unless the task explicitly requires backend changes.

When backend changes are necessary:
1. identify the exact existing schema first
2. explain migration impact
3. keep Android/domain contracts aligned
