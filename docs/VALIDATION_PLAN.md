# Validation Plan

## Primary endpoint
Absolute error between PulmoAcoustic respiratory rate and a reference respiratory measurement.

## Metrics
- MAE
- RMSE
- bias
- Bland–Altman limits of agreement
- ICC
- correlation
- repeatability
- invalid/rejected rate

## Required stratification
- phone model
- participant
- breathing rate
- chest position
- phone distance
- phone orientation
- clothing condition
- environmental noise
- minor vs major motion

## Dataset rule
Public respiratory datasets are used for physiological algorithm development and reference benchmarking. They do not replace our own paired acoustic recordings.

## Recommended experiment stages
1. Bench test with synthetic acoustic modulation.
2. Quiet-room adult volunteer pilot with a reference respiratory signal.
3. Multiple-phone comparison.
4. Repeatability study.
5. Controlled position/distance robustness study.
6. Only then investigate patient-population data under appropriate institutional approval.

## Clinical status
Until the above validation is performed, report the system as a research prototype and not a medical device.
