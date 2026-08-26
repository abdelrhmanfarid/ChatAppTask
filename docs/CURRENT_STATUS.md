# Current Status

Branch: `feature/media-messaging`. Repository code is authoritative; check `git status` before work.

## Modules

- `:app`: application entry point, Material 3 theme, Android/Compose splash, Hilt/WorkManager setup.
- `:core:common`: `UserIdentityStore`; `AndroidIdUserIdentityStore` derives a stable UUID from `Settings.Secure.ANDROID_ID` (no identity DataStore).
- `:core:ui`: shared Compose UI utilities, including `clearFocusOnTap()`.
- `:core:domain`: pure Kotlin models and `ChatRepository`/`UserRepository` contracts.
- `:core:database`: Room database, entities, DAOs, converters, and mappings.
- `:core:network`: Supabase client, DTOs, mappings, and debug-only Ktor logging.
- `:data:chat`: Room/Supabase data sources, repositories, DI, and text-send WorkManager flow.
- `:feature:profile`: Profile Setup contract, ViewModel, and Compose UI.
- `:feature:chat`: Chat contract, ViewModel, and text-chat Compose UI.

## Implemented

### Data and domain

- Domain models: `User`, `Message`, `MessageMedia`, `PendingMedia`; send states are `SENDING`, `SENT`, `FAILED`.
- `ChatRepository` declares observation, paging, text/media send/retry, outgoing-send cancel, and Realtime lifecycle operations.
- `UserRepository` supports current local identity, local/remote user lookup, observation, and upsert. Identity is a version-3 name UUID from the device `ANDROID_ID` (UTF-8, trimmed/lowercased). Missing/blank `ANDROID_ID` fails explicitly instead of generating a random UUID. Previous random DataStore identities are obsolete; clear local app data and development Supabase rows before testing.
- `ChatAppDatabase` v1 (`chat_app.db`) contains `users`, `messages`, and `message_media`; schema export is enabled.
- Room message observation is ordered `created_at DESC, id DESC` and combines message/media rows.
- `MessageEntity` owns Android operational fields: send status, attempt count, and last error.

### Text sending

- `DefaultChatRepository.sendTextMessage` creates one local UUID, persists an optimistic `SENDING` Room row, then schedules unique work.
- `TextMessageSendScheduler` uses unique work `send-text-message:<UUID>`, connected-network constraint, exponential minimum backoff, `KEEP` initially, and `REPLACE` for manual retry.
- `SendTextMessageWorker` delegates persisted sending to `DefaultChatRepository` through Hilt. It promotes the existing unique work to a foreground worker (`setForeground` / `getForegroundInfo`) with an ongoing privacy-conscious "Sending message" notification. Notification/work IDs are derived from unique work name `send-text-message:<UUID>`. Success, terminal failure, and backoff remove the active notification; the next execution shows it again. The active notification exposes Cancel only (not Retry) so REPLACE cannot race an in-flight insert. Cancel goes through `MessageSendWorkActionReceiver` → `ChatRepository.cancelOutgoingSend` (same UUID). Worker `CancellationException` is rethrown, not mapped to `Result.retry()`. Chat UI remains the retry path after `FAILED`. Cancel stops unique work and marks a still-`SENDING` row `FAILED`; it does not undo a completed Supabase insert (Realtime/fetch may still reconcile to `SENT`).
- One idempotent notification channel `message_send_work` is created from `ChatApp` and before posting. It is reused later for media-upload progress (`MessageSendWorkProgress.Determinate`). Android 13+ `POST_NOTIFICATIONS` is requested once when Chat opens; denial does not block persist/send. `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` are declared for WorkManager foreground execution.
- Immediately before each remote insert, Room atomically sets `SENDING`, clears the error, and increments `send_attempt_count`.
- Supabase inserts using the same UUID. Success reconciles server `createdAt`/`updatedAt` and marks `SENT` without resetting local attempt metadata.
- Failure retains the row, marks `FAILED`, stores the error, preserves the increment, and causes WorkManager retry for retryable exceptions.
- `ChatApp` supplies `HiltWorkerFactory`; the manifest disables WorkManager's default initializer.

### Local media persist and upload

- `DefaultChatRepository.sendMediaMessage` validates 1–10 items, copies each picker URI into `filesDir/outgoing-media/{messageId}/{mediaId}.{ext}` via `OutgoingMediaStore` / `FileOutgoingMediaStore`, upserts one optimistic Room `Message` (`SENDING`) plus `MessageMedia` rows (`PENDING`, durable `localUri`, no `storagePath`), then schedules unique work `send-media-message:<UUID>` (`KEEP`, connected network, exponential min backoff). Copy or persist failure deletes the message directory and Room row and does not schedule work. Scheduling failure keeps the row and marks it `FAILED`.
- Each attachment is capped at `MAX_MEDIA_ITEM_BYTES` (`50 * 1024 * 1024`, matching the Supabase Free 50 MB upload quota). Photo Picker resolution reads size from `OpenableColumns.SIZE` then `AssetFileDescriptor.length` when available and skips oversized items with a snackbar before enqueue. `sendMediaMessage` also rejects a declared or copied size above that cap without persisting/scheduling. Worker/repository size checks on durable copies treat an oversized file, Storage HTTP 413, and Storage HTTP 400 payload-too-large as `PermanentMediaUploadException` (`Result.failure()`, Room `FAILED`). Transient network/server errors still `Result.retry()`. `CancellationException` is still rethrown.
- `SendMediaMessageWorker` loads that UUID from WorkManager input only, reconstructs the Room media message, validates 1–10 ordered attachments and readable durable files for items not yet `UPLOADED`, then `beginMessageSendAttempt`. Remaining attachments are uploaded through `ChatRemoteDataSource.uploadChatMedia` in `position` order using the same message/media UUIDs and `{messageId}/{mediaId}.{ext}` Storage paths. Already `UPLOADED` rows with a persisted `storagePath` are skipped. After every required object is `UPLOADED`, `create_media_message` is invoked with that same message UUID. Success reconciles the optimistic Room row to `SENT` without inserting a duplicate. Storage upload uses `upsert = true` so a crash after Storage success but before Room `UPLOADED` can retry the same object. If `create_media_message` fails, attachment `UPLOADED` paths are kept and a later retry skips Storage. If the remote message already exists for that UUID (`getMessage`), remote creation is skipped and the local row is reconciled. Foreground notification reuses channel `message_send_work` and shows attachment-level `Uploading n of m` progress (Cancel only). `cancelOutgoingSend` also cancels media unique work. `CancellationException` is rethrown; already-`UPLOADED` attachments are kept and RPC is not called. Durable outgoing files are deleted best-effort only after Room `SENT`.

### Network and users

- Supabase Kotlin 3.6.0 uses Ktor OkHttp 3.5.1 with PostgREST, Storage, and Realtime plugins installed.
- Debug builds install Ktor `Logging` at `HEADERS` level under Logcat tag `SupabaseHttp`; credential headers are redacted and bodies are not logged. Release uses a no-op and has no logging dependency.
- `SupabaseChatRemoteDataSource` implements users, text insert, message queries, Realtime `messages` and `message_media` INSERT/UPDATE observation (ID-only payloads), media RPC/storage primitives, and DTO mapping.
- `DefaultUserRepository` is local-first for lookup, caches remote users, and upserts remote then Room.
- Profile Setup saves username plus optional positive integer age. Optional profile photo uses the system Photo Picker, local preview, then Storage upload of `{userId}/avatar.{ext}` into bucket `profile-images` before user upsert. Keyboard uses Scaffold `safeDrawing` only (no extra `imePadding`).
- `DefaultChatRepository.loadLatestMessages` / `loadOlderMessages` fetch existing remote pages (`created_at DESC, id DESC`; older uses `created_at < cursor` OR same timestamp and `id < cursor`), mark messages `SENT`, upsert missing senders then messages/media into Room, and leave existing Room rows untouched when a fetch fails. Duplicate UUIDs are upserted by primary key. `loadOlderMessages` takes the oldest Room `SENT` message as the remote cursor (not `SENDING`/`FAILED` locals), returns the remote page size so Chat can detect exhausted history, and returns `0` without a remote fetch when no `SENT` cursor exists. Chat UI still only observes Room.
- `DefaultChatRepository.startRealtimeSync` subscribes to Supabase `messages` and `message_media` INSERT and UPDATE (not DELETE). Each event is resolved through `getMessage` plus grouped media, then missing senders and Room rows are upserted. New remote rows are upserted as `SENT` with media. An existing optimistic outgoing row is reconciled to `SENT` without resetting send-attempt metadata and without replacing Android-only media fields (`localUri`, upload status/attempts/progress). An existing remote row missing media is later filled from a follow-up `getMessage` (same UUID, no duplicate). A `getMessage` miss leaves Room unchanged until a later event. `stopRealtimeSync` cancels the in-flight collection. Subscription failures do not clear Room. Realtime is owned by `ChatViewModel` (`viewModelScope`); Compose does not subscribe to Supabase.

### Presentation

- `ChatViewModel` observes Room messages unchanged, starts repository Realtime sync then loads the latest remote page at start, owns composer text/attachment state, schedules text or media sends, retries by existing UUID, exposes current user ID via `UserRepository`, and emits one-time errors / Photo Picker open requests.
- Chat older-history pagination is presentation-only: `ChatAction.LoadOlderMessages` runs when the reversed list approaches the oldest Room message. The ViewModel calls `ChatRepository.loadOlderMessages` (cursor chosen in the data layer from the oldest `SENT` row), keeps `isLoadingOlder` / `hasMoreOlderMessages` in `ChatUiState`, and still only observes Room for the message list. A short/empty remote page (size `< 20`) marks history exhausted. A small spinner can appear at the oldest edge; it does not replace the thread.
- `ChatRoute` collects state/events lifecycle-aware and owns the Android Photo Picker launchers (`PickMultipleVisualMedia(maxItems = 10)` and single `PickVisualMedia` when one slot remains) with `ImageAndVideo`. Picker URIs are resolved to `ComposerAttachment` via `ContentResolver.getType` (image/* / video/*) and size via `OpenableColumns.SIZE` then `AssetFileDescriptor.length`. Unsupported types and items larger than 50 MiB are skipped with a snackbar and do not crash. No broad storage / `READ_MEDIA_*` permissions are requested.
- `ChatScreen` has Material 3 app bar, empty state, message bubbles with sent/received image and video-preview attachments, composer with compact attachment preview strip (ordered thumbnails, video badge/placeholder, per-item remove with a 48dp touch target, `n / 10` count), snackbar, Light/Dark previews, enabled attachment affordance, and `clearFocusOnTap()` on chat content (not on the composer). The composer uses a Material 3 `Button` for Send. The TextField is not disabled or made read-only during send, so the IME is not restarted. Send routes text-only through `sendTextMessage` and media (+ optional text) through `sendMediaMessage`; successful media handoff clears selection and text. Sending a new outgoing optimistic `SENDING` message animates the reversed list to index `0`. A live incoming Room message also scrolls to index `0` only when the list is already at/near the newest end (`firstVisibleItemIndex <= 1`); reading older history is not yanked. Older-page Room updates do not scroll.
- Chat bubbles render `Message.media` from the existing Room observation path (same composables for outgoing, incoming, paginated, and Realtime-ingested rows). Images load with Coil 3 from a local durable URI while `SENDING`/`FAILED`, or from a public `chat-media` URL once `SENT`. Videos render as a non-playing placeholder with a play indicator. Remote URLs are resolved through domain `ChatMediaPublicUrlFactory` (Supabase Storage `publicUrl` in data); `feature:chat` does not call the Supabase SDK.
- Selected attachments are temporary composer state only (not Room-persisted). Durable copies remain repository-owned after Send.
- Messages remain newest-to-oldest in state; `LazyColumn(reverseLayout = true)` puts the newest item at the visual bottom with UUID keys.
- Outgoing bubbles show `SENDING`, `SENT`, or `FAILED`; failed messages retry through `ChatAction.RetryMessage`. `ChatRepository.retryMessage` routes by persisted type: text-only rows reuse unique work `send-text-message:<UUID>` (`REPLACE`); media rows (including media + optional text) reuse unique work `send-media-message:<UUID>` (`REPLACE`) without creating a new Room message. Already `UPLOADED` attachments and their storage paths are left unchanged until the existing media worker runs.
- `MainActivity` hosts `ChatAppRoot` and keeps the Android SplashScreen API visible until startup resolution finishes. `StartupViewModel` resolves the current UUID/profile through `UserRepository`. A found profile goes to Chat; a successful empty lookup goes to Profile Setup; a thrown lookup failure shows a generic retry screen. While resolving after the system splash (including Retry), a splash-colored screen with a small progress indicator is shown instead of the branded Compose splash. Successful Profile Setup navigates to Chat.

## Not Implemented

- Full-screen media viewer, video playback, download/save, per-attachment retry UI, and upload byte-progress UI. Photo Picker + composer preview + send handoff + Storage upload + `create_media_message` + Room `SENT` reconciliation + chat bubble rendering are implemented.
- Incoming-message push / FCM, Supabase Auth, presence, typing indicators, and read receipts.

## Working Conventions

- Flow: feature -> domain repository -> data implementation -> Room/Supabase/WorkManager. Features do not access infrastructure directly.
- Room is the Android source of truth; remote results must converge into Room.
- Do not change Supabase versions or expose service-role credentials in Android.
- Use JDK 21 for reliable full builds in this workspace.
