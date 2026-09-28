# PulmoAcoustic Architecture

## Sensing
Speaker -> controlled acoustic carrier -> chest -> reflected signal -> phone microphone.

## Device adaptation
Device calibration tests bounded carrier choices. Chest scanning evaluates multiple positions and stores the best measured location/orientation.

## Signal processing
Microphone PCM
-> carrier-aligned I/Q demodulation
-> coherent phase
-> phase unwrapping
-> detrending
-> 0.08–0.80 Hz respiratory analysis
-> spectral estimator + autocorrelation + time-domain peak estimator
-> estimator consensus
-> SNR/periodicity/carrier-stability scoring
-> accelerometer/gyroscope motion gate
-> confidence gate
-> respiratory-rate result.

## Data
Local measurement history is stored on-device. Optional research WAV capture is stored locally in app-private storage.

## Validation
The final scientific validation requires paired smartphone acoustic recordings and an accepted reference respiratory measurement, with repeated tests across subjects and phone models.
