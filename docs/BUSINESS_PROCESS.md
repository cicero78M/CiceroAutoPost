# Business Process Documentation

This document describes the overall business workflow implemented in the **SocialToolsApp** project. The application is an Android-based utility for managing social media content.

## Overview

SocialToolsApp provides utilities for automating social media activity. The application centers on Instagram interactions and incorporates integrations with Twitter and TikTok. Key features include:

- OAuth login for Instagram with an in-app WebView flow.
- Displaying Instagram profile details and statistics.
- Fetching and displaying recent Instagram posts via the Instagram Graph API.
- Tools to like, repost, and comment on daily Instagram posts from selected accounts.
- Background service to run automated posting tasks.
- Optional premium subscription flow handled through remote API calls.
- Secure storage of tokens and session data using encrypted preferences.

## Actors

- **User** – Anyone using the app to manage social accounts.
- **Premium Subscriber** – A user who activates a premium subscription via the provided registration flow.
- **Remote API** – External endpoints used for premium subscription management and for retrieving or validating data (hosted at `papiqo.com`).

## High Level Workflow

1. **Authentication**
   - The user logs in to Instagram through the OAuth flow (`InstaOauthLoginFragment`). The obtained access token is stored locally.
   - The app can also perform Twitter OAuth (`TwitterAuthManager` and `TwitterCallbackActivity`) for fetching or posting tweets if needed.
   - TikTok session data is securely saved via `TiktokSessionManager`.

2. **Instagram Tools**
   - In `InstagramToolsFragment`, the user chooses actions (like, repost, comment) and specifies the target Instagram account or post.
   - The app loads the user profile information using `instagram4j` and shows statistics in the UI.
   - Recent posts for the current day are fetched. For each post, the app can like, repost, or comment after randomized delays to mimic natural activity.
   - Activity logs are written to local files and displayed on screen.

3. **Background Posting Service**
   - `PostService` runs in the foreground as a `Service`. It schedules delays and performs automated posting tasks in the background while displaying a notification.

4. **Premium Subscription Flow**
   - `PremiumRegistrationActivity` allows a logged‑in user to submit registration details (username, bank account, phone number). The data is posted to a remote API.
   - The user can confirm the subscription via `SubscriptionConfirmActivity`. Confirmation is also sent to the remote API.
   - The app checks subscription status using `checkSubscriptionStatus` (in `InstagramToolsFragment`) and adjusts UI indicators (badge icons) based on active status.

5. **Dashboard and Post Viewer**
   - `DashboardFragment` fetches recent Instagram posts using `InstagramGraphApi` and shows them in a RecyclerView with `PostAdapter`.

## Data Storage

- EncryptedSharedPreferences are used for storing tokens (Twitter, TikTok) and session cookies.
- Local files are used for logging Instagram automation results and serialized Instagram client sessions.

## External Dependencies

- **Instagram4j** – Handles low level Instagram API calls for login and posting.
- **Twitter4j** – Handles Twitter OAuth and API interactions.
- **OkHttp** – Used for all HTTP requests, including remote API calls.
- **Coroutines** – Kotlin coroutines manage background operations and asynchronous tasks throughout the app.

## Premium Business Logic

1. The app ensures remote data for each Instagram account by invoking the `papiqo.com` API. If the user record or subscription record does not exist, it is created automatically.
2. Subscription checks are performed when the user profile is displayed. Active subscriptions allow the user to run premium features (indicated with a badge).
3. Registration and confirmation requests post JSON data to the remote endpoints. Success or failure messages are shown to the user.

## Background Automation Sequence

The automation routine (implemented in `InstagramToolsFragment`) performs actions such as liking, reposting, and commenting in a loop over daily posts. Randomized delays are used between actions to avoid suspicious behavior. Status updates are appended to the on‑screen log and saved in a per-user log file.

## Summary

SocialToolsApp streamlines Instagram management by automating repetitive tasks. The business process involves authenticating the user, selecting automation options, executing background actions, and optionally managing a premium subscription via remote API integration. All sensitive data is stored securely, and the app provides real-time logs for transparency.

