# Next Steps

1. Add minimal Chat UI older-page pagination triggers that call `ChatRepository.loadOlderMessages` using the oldest visible Room message cursor; keep Room as the source of truth.
2. Implement media send orchestration and workers using the existing media contracts, DAO operations, Storage methods, and `create_media_message` RPC; reuse the `message_send_work` notification channel and `MessageSendWorkProgress.Determinate` for upload progress; then enable picker/upload/rendering UI.
3. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
