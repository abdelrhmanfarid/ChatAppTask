# Next Steps

1. Implement initial message loading and cursor pagination in `DefaultChatRepository`, persisting remote pages into Room; then add minimal Chat UI paging triggers.
2. Implement Supabase Realtime ingestion into Room with lifecycle-safe start/stop behavior and deduplication by message UUID.
3. Implement media send orchestration and workers using the existing media contracts, DAO operations, Storage methods, and `create_media_message` RPC; then enable picker/upload/rendering UI.
4. Add profile image upload, then Supabase Auth and FCM only when their product flows are defined.
