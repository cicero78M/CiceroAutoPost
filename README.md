# CiceroAutoPost

This repository contains `SocialToolsApp`, a Kotlin-based Android Studio project extracted from the original `pegiat_medsos_apps` code.
Binary assets such as images have been removed, so the project uses simple vector placeholders for launcher and UI icons.
The Gradle wrapper JAR is also excluded to keep the repository free of binary
artifacts. Running `./gradlew` will automatically download the required
wrapper files.

The app now opens straight to the Instagram tools screen. The previous
ViewPager navigation has been removed so users are taken directly to the IG
automation interface on launch.

## Configuration

Create a `.env` file in the repository root to provide your Twitter API
credentials. It should contain the following keys:

```
TWITTER_CONSUMER_KEY=your_key
TWITTER_CONSUMER_SECRET=your_secret
OPENAI_API_KEY=sk-...
```

These values will be read by Gradle when building the app. If the OpenAI key is
not provided at build time, the app also checks the `OPENAI_API_KEY` environment
variable at runtime when generating comments.

To build the app:

```bash
cd socialtools_app
./gradlew assembleDebug
```
