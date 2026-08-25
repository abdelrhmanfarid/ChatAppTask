# Next Steps

1. Replace the Stage-3 `sendPersistedMediaMessage` prepare-only path with Storage upload of remaining `PENDING` items and `create_media_message`, using DAO upload updates and `MessageSendWorkProgress.Determinate`; then enable picker/upload/rendering UI.
2. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
