# Project Context

## Project

**Chat App Task** is a native Android chat application implemented in Kotlin with Jetpack Compose.

The goal is a professional, practical coding-task-quality application rather than an over-engineered production messenger.

## Core technology

- Kotlin
- Jetpack Compose
- Material 3
- Clean Architecture
- MVI / Unidirectional Data Flow
- Room as the Android source of truth
- WorkManager for reliable background message sending
- Supabase backend
- Supabase Realtime planned/used for live updates as appropriate
- Supabase Storage for media
- No Supabase Auth
- Firebase Cloud Messaging for incoming chat push notifications

## Identity model

There is **no authentication flow**.

On first launch:

1. Generate or load a locally persisted UUID.
2. Use that UUID as the user's identifier.
3. Determine whether a backend profile exists.
4. If no profile exists, show profile setup.
5. Save the profile.
6. Enter chat.

Never introduce:
- login
- signup
- email/password
- OTP
- social auth
- fake authentication screens

## User profile

Backend user fields:

- `id`
- `username`
- `profile image`
- `age`

Do not invent additional persisted profile fields without an explicit requirement.

## Messaging

Current priority: **text messaging first**.

A message can eventually support:
- text
- image media
- video media

Delivery states:
- `SENDING`
- `SENT`
- `FAILED`

Messages are written locally first so optimistic messages can appear immediately.

Failed messages must be visibly retryable.

## Development philosophy

Prefer:
- simple
- correct
- maintainable
- testable
- Compose-friendly
- Material 3-native
- minimal dependency surface

Avoid:
- unnecessary abstractions
- speculative features
- custom UI drawing where Material/Compose primitives are sufficient
- backend redesign unless explicitly requested
