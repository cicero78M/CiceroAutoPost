# CiceroAutoPost

This repository contains `SocialToolsApp`, a Kotlin-based Android Studio project extracted from the original `pegiat_medsos_apps` code.
Binary assets such as images have been removed, so the project uses simple vector placeholders for launcher and UI icons.
The Gradle wrapper JAR is also excluded to keep the repository free of binary
artifacts. Running `./gradlew` will automatically download the required
wrapper files.

## Configuration

Create a `.env` file in the repository root to provide your Twitter API
credentials. It should contain the following keys:

```
TWITTER_CONSUMER_KEY=your_key
TWITTER_CONSUMER_SECRET=your_secret
```

These values will be read by Gradle when building the app.

To build the app:

```bash
cd socialtools_app
./gradlew assembleDebug
```
