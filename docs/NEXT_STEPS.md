# Next Steps

1. Implement media send WorkManager orchestration (upload remaining `PENDING` items, then `create_media_message`) using existing Storage methods, DAO upload updates, and `MessageSendWorkProgress.Determinate`; then enable picker/upload/rendering UI.
2. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
