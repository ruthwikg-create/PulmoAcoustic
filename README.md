# PulmoAcoustic

**Adaptive smartphone-only contactless respiratory monitoring research app**

PulmoAcoustic uses a compatible Android phone's **built-in speaker + built-in microphone** as an active acoustic sensing pair. The app emits a bounded high-frequency carrier, captures the reflected acoustic response from the chest, extracts a respiratory-related signal, and only reports a respiratory rate when multiple estimators and the quality gate agree.

## Current goal

Build the most reliable smartphone-only respiratory-rate prototype possible, then validate it against a reference respiratory measurement device. The project is a **research prototype**, not a medical device.

## App features

- Automatic microphone permission flow
- Device-specific acoustic calibration across a bounded 18–20 kHz candidate set
- Automatic optimization mode
- Guided chest-position scan
- Six guided chest locations
- User-confirmed point-by-point scanning so repositioning is not treated as measurement motion
- Best-position scoring using acoustic quality, periodicity and confidence
- Saved best chest location and measured phone orientation
- 30–60 second measurement duration; 45 seconds is the default research setting
- Speaker + microphone active acoustic sensing
- I/Q demodulation
- Carrier-aligned coherent phase extraction
- Phase unwrapping and detrending
- Respiratory-band analysis
- Spectral respiratory-rate estimator
- Autocorrelation estimator
- Time-domain peak estimator
- Multi-estimator agreement/consensus
- Session-wide accelerometer and gyroscope motion tracking
- Small-motion quality penalty
- Major-motion rejection
- Acoustic SNR calculation
- Periodicity calculation
- Carrier-stability calculation
- Confidence scoring
- Low-confidence result rejection
- Local measurement history
- Optional raw WAV capture for supervised research
- Curated public respiratory/cardiorespiratory dataset hub
- GitHub Actions debug-APK build
- In-app operating guide

## Recommended first-use procedure

1. Open the Android app and grant microphone permission.
2. Go to **Settings → Automatic optimization** and keep it ON.
3. Run **Calibrate device** once on the current phone.
4. Run the **guided chest scan**.
5. For each point, move the phone into position, hold it steady, then press **I'm ready - test selected spot**.
6. Use the highest-quality position and reproduce the saved phone orientation.
7. Use a quiet room, one person, upright seated posture and bare upper chest for the initial research protocol.
8. Keep the phone roughly 40–60 cm away as a standardized setup target, not as a clinically validated ranging value.
9. Run a 30–60 second measurement; use 45–60 seconds for research.
10. Remain still and silent.
11. Trust a result only when the app reports sufficient confidence. Major movement or poor estimator agreement causes rejection rather than a forced value.

## Why the app rejects measurements

The target is not to always output a number. It is to minimize wrong numbers.

The confidence engine combines:

- acoustic SNR
- respiratory periodicity
- carrier stability
- accelerometer/gyroscope motion
- agreement between independent respiratory estimators

The app should prefer **"measurement rejected"** over a convincing but unreliable result.

## Research data

The app includes official links for open respiratory/cardiorespiratory datasets. These data are useful for:

- respiratory-rate algorithm development
- signal-processing experiments
- reference physiological waveforms
- annotation and benchmarking
- obstructive/apnea research

They do **not** reproduce our exact speaker → chest → microphone acoustic channel.

For final calibration and ML validation we need a paired dataset:

**smartphone acoustic recording + reference respiratory measurement + phone model + distance + orientation + chest location + subject/session metadata**

## Validation

Before claiming medical-grade or clinical accuracy, compare the phone against a reference respiratory device and report:

- MAE
- RMSE
- bias
- Bland–Altman limits of agreement
- ICC/correlation
- repeatability
- rejected-measurement rate
- performance by phone model
- performance by subject
- performance under different distances/angles/noise levels

The included synthetic benchmarks are **software regression tests only** and are not clinical validation.

## Build the APK

The repository contains a GitHub Actions workflow at:

`.github/workflows/build-apk.yml`

The workflow uses Java 17 and a configured Gradle distribution to build:

`app/build/outputs/apk/debug/app-debug.apk`

After a successful GitHub Actions run:

1. Open the workflow run.
2. Open **Artifacts**.
3. Download **PulmoAcoustic-debug-apk**.
4. Extract the ZIP.
5. Transfer `app-debug.apk` to the Android phone.
6. Allow installation from the file manager/browser when Android asks.
7. Install and open PulmoAcoustic.

Detailed installation and research-use instructions are in:
- `APP_USER_GUIDE.md`
- `BUILD_APK_ON_GITHUB.md`
- `ANDROID_DEVICE_CHECKLIST.md`
- `docs/VALIDATION_PLAN.md`

## Safety

The acoustic output is software-bounded. Do not increase it beyond the app's safe research limits. Stop the test if the sound is uncomfortable.

PulmoAcoustic must not be used to diagnose, treat, rule out disease, or replace clinical equipment.
