# PulmoAcoustic — User Guide

## Before measuring
- Android phone with working speaker and microphone
- Quiet room
- One person in the sensing area
- Upright seated posture
- Bare upper chest for the initial research protocol
- Keep speaker and microphone openings unobstructed

## Step 1 — Permission
Open PulmoAcoustic and grant microphone permission.

## Step 2 — Calibration
Open Settings and keep Automatic optimization enabled.
Run Calibrate device. The app compares bounded carrier choices around 18–20 kHz and keeps the strongest measured response for the current phone.

Run calibration again after changing phones.

## Step 3 — Chest scan
Open Scan.
For each displayed chest point:
1. Move the phone into position.
2. Hold it steady.
3. Keep the approximate target distance around 40–60 cm.
4. Press "I'm ready - test selected spot".
5. Wait for the test to finish.
6. Move to the next point.

The scan scores SNR, periodicity, motion and overall confidence and recommends the best location. It also records the phone orientation seen by the motion sensors during the best scan.

## Step 4 — Measurement
Open Measure.
Keep the phone at the best location/orientation.
Keep the subject still and silent for the selected duration (30–60 seconds; 45–60 seconds is preferred for research).

## Step 5 — Understand the result
- Respiratory rate: estimated breaths per minute.
- Confidence: signal-quality score, not disease probability.
- SNR: estimated useful acoustic signal strength versus noise.
- Periodicity: repeatability of the respiratory pattern.
- Motion: estimated motion contamination.
- Estimator agreement: agreement among independent respiratory estimators.

A low-confidence or major-motion session is rejected rather than converted into a forced numeric value.

## Research capture
Enable Research raw WAV capture only for supervised research.
Files are stored in the app's private storage under research-captures.

Human-subject data must be collected with appropriate consent and institutional approval.

## Datasets
The Data tab contains official links to public respiratory/cardiorespiratory datasets. Those are physiological reference datasets and do not replace the paired phone-acoustic dataset needed for final validation.

## Safety
PulmoAcoustic is a research prototype and is not a medical device. It must not be used to diagnose, treat, or rule out disease or to replace clinical monitoring equipment.
