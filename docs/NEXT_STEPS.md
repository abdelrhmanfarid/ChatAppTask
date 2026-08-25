# Next Steps

1. Enable Android Photo Picker, attachment composer behavior, and Chat media rendering. Do not treat media messaging as complete until those UI surfaces exist. Confirm on the deployed Supabase project that `chat-media` allows overwrite of `{messageId}/{mediaId}.{ext}` and that `create_media_message` is safe to retry with the same message UUID (Android already skips RPC when `getMessage` finds that UUID).
2. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
