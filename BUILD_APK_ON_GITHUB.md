# Build the Android APK from GitHub

The repository has a GitHub Actions workflow at .github/workflows/build-apk.yml.

## Build
1. Open the PulmoAcoustic repository on GitHub.
2. Open Actions.
3. Select Build PulmoAcoustic APK.
4. Run the workflow from the main branch if it is not already running from a push.
5. Wait for the build job to finish.

The workflow installs Java 17, configures Gradle, builds app:assembleDebug, and uploads the debug APK as an artifact.

## Download to the phone
1. Open the completed workflow run.
2. Under Artifacts, download PulmoAcoustic-debug-apk.
3. Extract the downloaded ZIP.
4. Copy app-debug.apk to the Android phone.
5. Tap the APK.
6. Android may ask you to allow installation from the app you used to open the APK. Enable it only for the file manager/browser you trust.
7. Install and open PulmoAcoustic.
8. Grant microphone permission.

## First run
Follow the in-app guide:
Calibration -> Chest Scan -> Best Position -> Measurement.

## Important
The debug APK is for research/development. It is not a medically cleared application.
