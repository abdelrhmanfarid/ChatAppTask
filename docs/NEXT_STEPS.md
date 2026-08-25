# Next Steps

1. Implement media send orchestration and workers using the existing media contracts, DAO operations, Storage methods, and `create_media_message` RPC; reuse the `message_send_work` notification channel and `MessageSendWorkProgress.Determinate` for upload progress; then enable picker/upload/rendering UI.
2. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
