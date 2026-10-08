package fi.kansalaisuuskoe.ui

import android.util.Log

/** Learners see a short message; this keeps the cause for debugging. Log failures, never answers or content. */
internal object Diagnostics {
    fun error(where: String, cause: Throwable) {
        // Plain JVM unit tests have no Android logger.
        runCatching { Log.e("Kansalaisuuskoe", where, cause) }
    }
}
