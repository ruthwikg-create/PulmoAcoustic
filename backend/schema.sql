CREATE TABLE IF NOT EXISTS measurements (
 id BIGSERIAL PRIMARY KEY,
 user_id TEXT,
 bpm INTEGER,
 confidence INTEGER NOT NULL DEFAULT 0,
 quality INTEGER NOT NULL DEFAULT 0,
 snr_db DOUBLE PRECISION NOT NULL DEFAULT 0,
 duration_ms BIGINT NOT NULL DEFAULT 0,
 device_name TEXT,
 sample_rate INTEGER NOT NULL DEFAULT 16000,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_measurements_user_created ON measurements(user_id,created_at DESC);