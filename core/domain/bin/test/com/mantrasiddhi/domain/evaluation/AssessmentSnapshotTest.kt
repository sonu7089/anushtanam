package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.acoustic.*
import com.mantrasiddhi.domain.evaluation.model.*
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssessmentSnapshotTest {
    @Test fun `snapshot preserves repeated word positions confidence pacing and pitch`() {
        val axis = AxisScore(.8f, ScoreBand.values().first())
        val words = listOf(WordResult("om", "om", .9f, null, .8f, 1.1f),
            WordResult("om", null, 0f, WordFeedback.MISSING))
        val a = RecitationAssessment(axis, axis, axis, axis, words,
            listOf(PadaResult(1, .5f, .45f, words)), listOf("extra"), false, true, true, 1000)
        val json = JSONObject(AssessmentSnapshot.encode(a, listOf("om extra" to .6f), .6f,
            AcousticAssessment(listOf(AcousticWordScore(0, .8f, 1.1f)), true, 12f),
            SwaraAssessment(listOf(.7f, null), .7f, SwaraAssessment.Hint.FLAT, true), true, EvaluationConfig.DEFAULT))
        assertEquals(2, json.getJSONArray("words").length())
        assertTrue(json.getJSONArray("words").getJSONObject(1).isNull("spokenWord"))
        assertEquals(1.1, json.getJSONArray("words").getJSONObject(0).getDouble("pacingRatio"), .001)
        assertEquals(.5, json.getJSONArray("padas").getJSONObject(0).getDouble("coverage"))
        assertEquals("FLAT", json.getJSONObject("swara").getString("hint"))
        assertEquals(12.0, json.getDouble("snrDb"))
        assertEquals(.6, json.getDouble("confidence"), .001)
        assertTrue(json.getBoolean("lowConfidence"))
        assertTrue(json.getJSONObject("config").has("passThreshold"))
    }
}
