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

    @Test fun hardQuestionsAndRetriesAreTheirOwnCommands() {
        assertEquals(PracticeRequest.Marked, PracticeRequest.fromArguments("marked", null, null))
        // An explicit topic ID never becomes a command, even one called "marked".
        assertEquals(PracticeRequest.Topic("marked"), PracticeRequest.fromArguments("topic", "marked", null))
        val ids = listOf("demo-parliament", "imported,with comma", "ääkköset-sv")
        assertEquals(PracticeRequest.Retry(ids), PracticeRequest.fromArguments("retry", null, null, PracticeRequest.retryArgument(ids)))
    }

    @Test fun assessmentMistakesKeepTheSavedAssessmentIdentity() {
        assertEquals(PracticeRequest.Mistakes("saved-assessment"), PracticeRequest.fromArguments("assessment", null, "saved-assessment"))
    }
}
