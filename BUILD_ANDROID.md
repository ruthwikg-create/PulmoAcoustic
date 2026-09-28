# Build locally with Android Studio

## Requirements
- Android Studio
- Android SDK Platform 35
- JDK 17
- Android phone API 26+
- USB debugging for direct install

## Steps
1. Clone or download the repository.
2. Open it in Android Studio.
3. Let Gradle sync.
4. Select the app run configuration.
5. Connect the Android phone with USB debugging enabled.
6. Press Run.

To create an APK inside Android Studio use Build -> Build APK(s).

The repository does not require a Gradle wrapper for the GitHub Actions path; the workflow installs the specified Gradle distribution.
