package com.ruthwik.pulmoacoustic.storage

import android.content.Context
import com.ruthwik.pulmoacoustic.model.RespiratoryResult
import com.ruthwik.pulmoacoustic.model.SignalQuality
import org.json.JSONArray
import org.json.JSONObject

class MeasurementStore(context: Context) {
    private val prefs = context.getSharedPreferences("pulmo_history", Context.MODE_PRIVATE)

    fun save(result: RespiratoryResult) {
        val arr = JSONArray(prefs.getString("items", "[]"))
        val obj = JSONObject().apply {
            put("timestamp", result.timestampEpochMs)
            put("rr", result.respiratoryRateBpm ?: JSONObject.NULL)
            put("confidence", result.signalQuality.confidence)
            put("snr", result.signalQuality.snrDb)
            put("periodicity", result.signalQuality.periodicity)
            put("motion", result.signalQuality.motionScore)
            put("carrierStability", result.signalQuality.carrierStability)
            put("agreementBpm", result.signalQuality.estimatorAgreementBpm)
            put("majorMovement", result.signalQuality.majorMovementDetected)
            put("carrierHz", result.carrierHz)
            put("durationSec", result.durationSec)
            put("valid", result.signalQuality.valid)
            put("message", result.message)
        }
        arr.put(obj)

        // Keep the newest 100 local sessions.
        val start = (arr.length() - 100).coerceAtLeast(0)
        val trimmed = JSONArray()
        for (i in start until arr.length()) trimmed.put(arr.getJSONObject(i))

        prefs.edit().putString("items", trimmed.toString()).apply()
    }

    fun loadNewest(limit: Int = 100): List<RespiratoryResult> {
        val arr = JSONArray(prefs.getString("items", "[]"))
        val result = ArrayList<RespiratoryResult>()
        for (i in arr.length() - 1 downTo 0) {
            if (result.size >= limit) break
            val o = arr.getJSONObject(i)
            val rr = if (o.isNull("rr")) null else o.getDouble("rr")
            result += RespiratoryResult(
                respiratoryRateBpm = rr,
                signalQuality = SignalQuality(
                    snrDb = o.optDouble("snr", -99.0),
                    periodicity = o.optDouble("periodicity", 0.0),
                    motionScore = o.optDouble("motion", 1.0),
                    carrierStability = o.optDouble("carrierStability", 0.0),
                    confidence = o.optInt("confidence", 0),
                    valid = o.optBoolean("valid", false),
                    majorMovementDetected = o.optBoolean("majorMovement", false),
                    estimatorAgreementBpm = o.optDouble("agreementBpm", 99.0),
                ),
                carrierHz = o.optDouble("carrierHz", 19_000.0),
                durationSec = o.optDouble("durationSec", 30.0),
                timestampEpochMs = o.optLong("timestamp", 0L),
                message = o.optString("message", "Stored local measurement"),
            )
        }
        return result
    }
}