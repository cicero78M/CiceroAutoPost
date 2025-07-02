# CiceroAutoPost

This repository contains `IgToolsApp`, a Kotlin-based Android Studio project extracted from the original `pegiat_medsos_apps` code.
Binary assets such as images have been removed, so the project uses simple vector placeholders for launcher and UI icons.
The Gradle wrapper JAR is also excluded to keep the repository free of binary
artifacts. Running `./gradlew` will automatically download the required
wrapper files.

To build the app:

```bash
cd igtools_app
./gradlew assembleDebug
```
