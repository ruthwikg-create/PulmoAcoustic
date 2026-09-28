# Accuracy Strategy

## Goal

Minimize error against a reference respiratory measurement while maximizing the rate at which the app correctly rejects poor-quality sessions.

## Signal pipeline

1. Device capability check
2. Bounded acoustic carrier calibration
3. Guided chest-position scan
4. Carrier-aligned I/Q demodulation
5. Phase extraction and unwrapping
6. Detrending
7. Respiratory-band analysis (0.08–0.80 Hz)
8. Spectral estimator
9. Autocorrelation estimator
10. Time-domain peak estimator
11. Estimator consensus
12. Motion/artifact gate
13. SNR + periodicity + carrier-stability scoring
14. Confidence threshold
15. Accept or reject

## Dataset roles

### Ground-truth/reference datasets
Use impedance respiration, airflow, thoracic effort, pressure/flow and annotated breath events to benchmark RR algorithms and establish target physiology.

### Audio/auxiliary datasets
BreathMY_v2 is useful for audio denoising and respiratory-audio robustness. It is not the same physical modality as active smartphone sonar, so it must not be treated as direct training data for the speaker-to-chest reflection channel.

### Non-contact auxiliary datasets
mmWave/radar data can help study non-contact respiration patterns and motion robustness, but the sensing physics differs from our phone-acoustic channel.

### Sleep/apnea datasets
Use for event detection, abnormal breathing patterns and sleep-specific validation. They do not automatically make the phone an apnea diagnostic device.

### Pulmonary-mechanics datasets
Use to study relationships among chest/abdomen motion, pressure, flow and respiratory timing.

## No data leakage

Never split randomly by window when windows originate from the same subject. Split by subject first, then create windows inside each split.

Recommended:
- train: 70% subjects
- validation: 15% subjects
- test: 15% subjects

For multi-phone experiments, also maintain a phone-held-out test set.

## Metrics

Report:
- MAE
- RMSE
- median absolute error
- bias
- Bland–Altman limits of agreement
- ICC
- Pearson/Spearman correlation where appropriate
- accepted-session rate
- rejected-session rate
- false-accept rate
- false-reject rate

## Reference-device study

For every paired session, store:
- phone model
- Android version
- carrier frequency
- output setting
- sampling rate
- chest location
- phone orientation
- phone-to-chest distance
- environmental noise condition
- motion condition
- smartphone acoustic waveform
- reference respiration waveform
- reference RR
- final app estimate
- quality metrics

The paired acoustic dataset is the key missing ingredient for clinical-grade claims. Public datasets can improve physiology modelling, but they cannot replace this paired measurement study.
