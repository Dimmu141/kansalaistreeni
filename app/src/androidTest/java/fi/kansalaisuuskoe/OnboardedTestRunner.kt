package fi.kansalaisuuskoe

import androidx.test.runner.AndroidJUnitRunner
import fi.kansalaisuuskoe.data.SettingsRepository
import kotlinx.coroutines.runBlocking

/** Marks first-run onboarding as seen so journeys start on Home; OnboardingFlowTest resets it itself. */
class OnboardedTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        runBlocking { SettingsRepository(targetContext).setOnboardingSeen(true) }
        super.onStart()
    }
}
