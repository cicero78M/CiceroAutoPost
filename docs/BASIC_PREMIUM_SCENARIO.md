# User Tiers Scenario

This document describes the differences between **Basic** and **Premium** users when using the SocialToolsApp backend from [Cicero_V2](https://github.com/cicero78M/Cicero_V2.git).

## Basic User

- Can run the auto-like feature but is limited to **3 likes** on a single target account within a rolling window of **3 days**.
- Once the limit is reached the like routine stops and a log entry is displayed.
- Other features such as commenting or reposting remain inaccessible unless upgraded.

## Premium User

- Has access to all automation features with no like limits.
- Subscription status is checked via the remote API and indicated with a premium badge.

## Backend

The mobile app communicates with the backend described in the `Cicero_V2` repository for managing user accounts and premium subscriptions.
