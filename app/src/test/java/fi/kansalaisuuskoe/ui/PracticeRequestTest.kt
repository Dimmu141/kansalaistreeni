package fi.kansalaisuuskoe.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeRequestTest {
    @Test fun explicitTopicIdsCannotSelectPracticeCommands() {
        for (id in listOf("review", "all", "assessment", "topic", "my-topic")) {
            assertEquals(PracticeRequest.Topic(id), PracticeRequest.fromArguments("topic", id, null))
        }
    }

    @Test fun legacySavedArgumentsKeepTheirOriginalMeaning() {
        assertEquals(PracticeRequest.Daily, PracticeRequest.fromArguments(null, null, null))
        assertEquals(PracticeRequest.Daily, PracticeRequest.fromArguments("all", null, null))
        assertEquals(PracticeRequest.Review, PracticeRequest.fromArguments("review", null, null))
        assertEquals(PracticeRequest.Topic("democracy"), PracticeRequest.fromArguments("democracy", null, null))
    }

    @Test fun assessmentMistakesKeepTheSavedAssessmentIdentity() {
        assertEquals(PracticeRequest.Mistakes("saved-assessment"), PracticeRequest.fromArguments("assessment", null, "saved-assessment"))
    }
}
