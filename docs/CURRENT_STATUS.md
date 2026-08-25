# Current Status

Branch: `feature/text-messaging`. Repository code is authoritative; check `git status` before work.

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
- `ChatRepository` declares observation, paging, text/media send/retry, and Realtime lifecycle operations.
- `UserRepository` supports current local identity, local/remote user lookup, observation, and upsert. Identity is a version-3 name UUID from the device `ANDROID_ID` (UTF-8, trimmed/lowercased). Missing/blank `ANDROID_ID` fails explicitly instead of generating a random UUID. Previous random DataStore identities are obsolete; clear local app data and development Supabase rows before testing.
- `ChatAppDatabase` v1 (`chat_app.db`) contains `users`, `messages`, and `message_media`; schema export is enabled.
- Room message observation is ordered `created_at DESC, id DESC` and combines message/media rows.
- `MessageEntity` owns Android operational fields: send status, attempt count, and last error.

### Text sending

- `DefaultChatRepository.sendTextMessage` creates one local UUID, persists an optimistic `SENDING` Room row, then schedules unique work.
- `TextMessageSendScheduler` uses unique work `send-text-message:<UUID>`, connected-network constraint, exponential minimum backoff, `KEEP` initially, and `REPLACE` for manual retry.
- `SendTextMessageWorker` delegates persisted sending to `DefaultChatRepository` through Hilt.
- Immediately before each remote insert, Room atomically sets `SENDING`, clears the error, and increments `send_attempt_count`.
- Supabase inserts using the same UUID. Success reconciles server `createdAt`/`updatedAt` and marks `SENT` without resetting local attempt metadata.
- Failure retains the row, marks `FAILED`, stores the error, preserves the increment, and causes WorkManager retry for retryable exceptions.
- `ChatApp` supplies `HiltWorkerFactory`; the manifest disables WorkManager's default initializer.

### Network and users

- Supabase Kotlin 3.6.0 uses Ktor OkHttp 3.5.1 with PostgREST, Storage, and Realtime plugins installed.
- Debug builds install Ktor `Logging` at `HEADERS` level under Logcat tag `SupabaseHttp`; credential headers are redacted and bodies are not logged. Release uses a no-op and has no logging dependency.
- `SupabaseChatRemoteDataSource` implements users, text insert, message queries, media RPC/storage primitives, and DTO mapping.
- `DefaultUserRepository` is local-first for lookup, caches remote users, and upserts remote then Room.
- Profile Setup saves username plus optional positive integer age. Optional profile photo uses the system Photo Picker, local preview, then Storage upload of `{userId}/avatar.{ext}` into bucket `profile-images` before user upsert. Keyboard uses Scaffold `safeDrawing` only (no extra `imePadding`).

### Presentation

- `ChatViewModel` observes Room messages unchanged, owns composer state, schedules sends, retries by existing UUID, exposes current user ID via `UserRepository`, and emits one-time errors.
- `ChatRoute` collects state/events lifecycle-aware. `ChatScreen` has Material 3 app bar, empty state, message bubbles, composer, snackbar, Light/Dark previews, and disabled-by-default attachment affordance.
- Messages remain newest-to-oldest in state; `LazyColumn(reverseLayout = true)` puts the newest item at the visual bottom with UUID keys.
- Outgoing bubbles show `SENDING`, `SENT`, or `FAILED`; failed messages retry through `ChatAction.RetryMessage`.
- `MainActivity` hosts `ChatAppRoot` and keeps the Android SplashScreen API visible until startup resolution finishes. `StartupViewModel` resolves the current UUID/profile through `UserRepository`. A found profile goes to Chat; a successful empty lookup goes to Profile Setup; a thrown lookup failure shows a generic retry screen. While resolving after the system splash (including Retry), a splash-colored screen with a small progress indicator is shown instead of the branded Compose splash. Successful Profile Setup navigates to Chat.

## Not Implemented

- `DefaultChatRepository.loadLatestMessages` and `loadOlderMessages` orchestration; both currently throw unsupported-operation errors.
- Pagination UI/triggering and initial remote-to-Room message synchronization.
- Realtime synchronization (`startRealtimeSync`/`stopRealtimeSync` are unsupported); installing the plugin alone does not sync data.
- Media repository orchestration, media worker/retry flow, picker/permissions, upload UI, and media rendering.
- Supabase Auth, FCM, presence, typing indicators, and read receipts.

## Working Conventions

- Flow: feature -> domain repository -> data implementation -> Room/Supabase/WorkManager. Features do not access infrastructure directly.
- Room is the Android source of truth; remote results must converge into Room.
- Do not change Supabase versions or expose service-role credentials in Android.
- Use JDK 21 for reliable full builds in this workspace.
