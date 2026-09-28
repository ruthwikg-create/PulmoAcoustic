# CardioSonic API

The Android DSP runs locally for low-latency measurement. This API stores measurement metadata and history.

Environment:
DATABASE_URL=postgresql://...
DATABASE_SSL=true
PORT=8080

Run:
npm install
npm start

Endpoints:
GET /health
POST /api/measurements
GET /api/measurements/:userId