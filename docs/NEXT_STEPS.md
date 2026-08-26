# Next Steps

1. Manually verify two-device live Realtime for remote text, media-only, and media+text (no app restart), then commit the media-completion work on `feature/media-messaging` if that check passes.
2. After that commit, the next product surfaces are the full-screen media viewer, then video playback. Those were out of scope for the media-completion pass.
3. Add Supabase Auth and FCM only when their product flows are defined. Do not treat FCM as the send-work notification path already implemented.
