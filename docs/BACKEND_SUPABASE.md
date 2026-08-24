# Supabase Backend Decisions

## Services

Use Supabase for:

- database
- realtime where needed
- storage for media
- Kotlin SDK access

Do **not** use Supabase Auth for the current project.

Firebase is not the primary backend. It may be used later only for FCM notifications if needed.

## Identity

The Android app owns a locally generated UUID.

The backend user record is associated with that UUID.

This should not be represented to the user as authentication.

## Profile

Known profile data:

- id
- username
- profile image
- age

## Messaging

Text messages are the current implementation priority.

Media support is future-facing.

The Android app should preserve local-first behavior even when backend/network work is delayed or unavailable.

## Backend change policy

Do not redesign tables, RLS, storage, or realtime behavior from an Android/UI task unless the task explicitly requires backend changes.

When backend changes are necessary:
1. identify the exact existing schema first
2. explain migration impact
3. keep Android/domain contracts aligned
