package com.example.fingerdetector.data.repository

import com.example.fingerdetector.data.model.FingerCaptureRecord
import com.example.fingerdetector.data.model.PalmRecord
import com.example.fingerdetector.data.model.SessionSnapshot

object CaptureSessionRepository {
    private var palmRecord: PalmRecord? = null
    private val fingerRecords = linkedMapOf<String, FingerCaptureRecord>()

    fun reset() {
        palmRecord = null
        fingerRecords.clear()
    }

    fun savePalm(record: PalmRecord) {
        palmRecord = record
    }

    fun saveFinger(record: FingerCaptureRecord) {
        fingerRecords[record.fingerType.name] = record
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        palmRecord = palmRecord,
        fingerRecords = fingerRecords.values.toList()
    )
}
