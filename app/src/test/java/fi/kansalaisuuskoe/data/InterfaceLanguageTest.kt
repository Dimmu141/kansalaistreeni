package fi.kansalaisuuskoe.data

import fi.kansalaisuuskoe.domain.StudyLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceLanguageTest {
    @Test fun `without a choice the interface follows Swedish study and otherwise stays Finnish`() {
        assertEquals(InterfaceLanguage.SWEDISH, InterfaceLanguage.defaultFor(StudyLanguage.SWEDISH))
        assertEquals(InterfaceLanguage.FINNISH, InterfaceLanguage.defaultFor(StudyLanguage.FINNISH))
        assertEquals(listOf("fi", "sv", "en", "ru", "ar", "fa", "so", "uk", "tr"), InterfaceLanguage.entries.map { it.tag })
    }
}
