# Next Steps

Required Android implementation and Bonus #1 are complete. Bonus #2 Stages 1B–2 (Firebase foundation + Android FID registration/reconciliation) are on `feature/fcm-notifications`.

1. Implement/deploy Supabase `register-push` Edge Function and `push_registrations` (Android invokes the function only; FID is cached locally and reconciled after profile exists).
2. Next FCM stages: Database Webhook → Edge Function → FCM HTTP v1 send; distinct incoming-chat notification channel (not `message_send_work`); tap navigation into Chat.
3. Manually verify: early `onRegistered` before Profile Setup still succeeds after profile save/startup Chat via cached FID reconcile.
4. Finish evaluator/submission documentation if needed, then promote through `development` → `staging` → `master` as usual.
5. Before GitHub submission, confirm repository visibility is **Public**.
6. Optional out-of-scope surfaces (full-screen media viewer, in-bubble video playback, download/save) remain non-mandatory unless promoted explicitly.
