# CiceroAutoPost

This repository contains `IgToolsApp`, a Kotlin-based Android Studio project extracted from the original `pegiat_medsos_apps` code.
Binary assets such as images have been removed, so the project uses simple vector placeholders for launcher and UI icons.
The Gradle wrapper JAR is also excluded so the repository contains no binary
artifacts. Running `./gradlew` will automatically download the required wrapper
files.

Create a `.env` file in the project root containing your Twitter API
credentials. The build script will read these values and expose them through
`BuildConfig`:

```bash
TWITTER_CONSUMER_KEY=your_consumer_key
TWITTER_CONSUMER_SECRET=your_consumer_secret
```

To build the app:

```bash
cd igtools_app
./gradlew assembleDebug
```
