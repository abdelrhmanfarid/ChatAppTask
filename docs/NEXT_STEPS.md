# Next Steps

Required Android implementation and Bonus #1 are complete. Bonus #2 Stage 1B (Firebase Android foundation) is on `feature/fcm-notifications`.

1. Place a valid local `app/google-services.json` (gitignored) and Sync/build `:app` to confirm Google Services + Messaging resolve.
2. Next FCM stage: register Firebase Installation ID (FID) with Supabase (no deprecated FCM registration-token APIs); keep Room/Realtime/send pipeline unchanged.
3. Later FCM stages: Database Webhook → Edge Function → FCM HTTP v1 send; distinct incoming-chat notification channel (not `message_send_work`); tap navigation into Chat.
4. Finish evaluator/submission documentation if needed, then promote through `development` → `staging` → `master` as usual.
5. Before GitHub submission, confirm repository visibility is **Public**.
6. Optional out-of-scope surfaces (full-screen media viewer, in-bubble video playback, download/save) remain non-mandatory unless promoted explicitly.
