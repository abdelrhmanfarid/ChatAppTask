# Next Steps

Required Android implementation and Bonus #1 are complete. Bonus #2 Android stages (Firebase foundation, FID registration/reconciliation, incoming-chat notifications + tap → Chat) are on `feature/fcm-notifications`.

1. Implement/deploy remaining Supabase push backend if needed: Database Webhook → Edge Function → FCM HTTP v1 **data-only** send using the Android contract (`type`, `schema_version`, `message_id`, `sender_id`, `sender_username`, `preview_kind`, `preview_text`). Confirm `register-push` / `push_registrations` remain healthy.
2. Manually verify on two devices: background notification, foreground Chat suppression, foreground non-Chat allow, self-sender suppression, permission denied = no crash/no post, notification tap cold start → startup → Chat, duplicate `message_id` replaces same notification.
3. Run focused JVM tests: `:app:testDebugUnitTest --tests "com.example.chatapptask.fcm.*"`.
4. Finish evaluator/submission documentation if needed, then promote through `development` → `staging` → `master` as usual.
5. Before GitHub submission, confirm repository visibility is **Public**.
6. Optional out-of-scope surfaces (full-screen media viewer, in-bubble video playback, download/save) remain non-mandatory unless promoted explicitly.
