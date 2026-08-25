# Next Steps

1. Add minimal Chat UI older-page pagination triggers that call `ChatRepository.loadOlderMessages` using the oldest visible Room message cursor; keep Room as the observed source of truth.
2. Implement Supabase Realtime ingestion into Room with lifecycle-safe start/stop behavior and deduplication by message UUID.
3. Implement media send orchestration and workers using the existing media contracts, DAO operations, Storage methods, and `create_media_message` RPC; then enable picker/upload/rendering UI.
4. Add Supabase Auth and FCM only when their product flows are defined.
