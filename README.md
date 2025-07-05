# CiceroAutoPost

This repository contains `SocialToolsApp`, a Kotlin-based Android Studio project extracted from the original `pegiat_medsos_apps` code.
Binary assets such as images have been removed, so the project uses simple vector placeholders for launcher and UI icons.
The Gradle wrapper JAR is also excluded to keep the repository free of binary
artifacts. Running `./gradlew` will automatically download the required
wrapper files.

The app now opens straight to the Instagram tools screen. The previous
ViewPager navigation has been removed so users are taken directly to the IG
automation interface on launch.

### Duplicate Caption Guard

Reposting operations now include a check for duplicate captions. Before
uploading a new post, the app compares its caption against the last twelve
uploads from the logged-in account. If a matching caption is found, the post is
skipped to avoid re-uploading identical content.

### Native Commenting

Comments are now posted using an `AccessibilityService` that opens the
Instagram app and injects the generated text into the comment field. This
pendekatan menggantikan metode `instagram4j` sehingga tidak perlu lagi
memanggil endpoint web secara langsung.

## Configuration

Create a `.env` file in the repository root containing any optional
configuration values. For example:

```
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
