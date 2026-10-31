package fi.kansalaisuuskoe.data

import fi.kansalaisuuskoe.domain.StudyLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceLanguageTest {
    @Test fun `without a choice the interface follows Swedish study and otherwise stays Finnish`() {
        assertEquals(InterfaceLanguage.SWEDISH, InterfaceLanguage.defaultFor(StudyLanguage.SWEDISH))
        assertEquals(InterfaceLanguage.FINNISH, InterfaceLanguage.defaultFor(StudyLanguage.FINNISH))
        assertEquals(listOf("fi", "sv", "en", "ru", "ar", "fa", "so", "uk", "tr", "th"), InterfaceLanguage.entries.map { it.tag })
    }
    @Test fun `first run picks support in the interface language, and none for the study languages`() {
        assertEquals(ExplanationPreference.STUDY_LANGUAGE, ExplanationPreference.matching(InterfaceLanguage.FINNISH))
        assertEquals(ExplanationPreference.STUDY_LANGUAGE, ExplanationPreference.matching(InterfaceLanguage.SWEDISH))
        // Every other interface language has its own support language.
        InterfaceLanguage.entries.drop(2).forEach { assertEquals(it.tag, ExplanationPreference.matching(it).tag) }
    }
}
