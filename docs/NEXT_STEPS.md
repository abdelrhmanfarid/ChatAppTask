# Next Steps

1. Implement Chat sent/received media bubble rendering (and related media UI such as viewer / retry surfaces as needed). Do not treat media messaging as complete until conversation media is visible. Confirm on the deployed Supabase project that `chat-media` allows overwrite of `{messageId}/{mediaId}.{ext}` and that `create_media_message` is safe to retry with the same message UUID (Android already skips RPC when `getMessage` finds that UUID).
2. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
