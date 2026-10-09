package fi.kansalaisuuskoe.domain

/**
 * The single description of the exam format that practice assessments imitate: length, format mix,
 * pass mark and time limit. Migri has not published the official format, so the app uses [UNKNOWN]:
 * our own practice length and mix, no pass mark and no time limit, and therefore no pass/fail verdict
 * or timer anywhere. When the official format is published, only [CURRENT] and a content package
 * should need to change; the verdict and timer UI is to be built against that real profile.
 */
data class ExamProfile(
    val id: String,
    val questionCount: Int,
    val trueFalseShare: Double,
    val passThresholdPercent: Int? = null,
    val timeLimitMinutes: Int? = null,
) {
    init {
        require(id.isNotBlank() && questionCount > 0 && trueFalseShare in 0.0..1.0)
        require(passThresholdPercent == null || passThresholdPercent in 1..100)
        require(timeLimitMinutes == null || timeLimitMinutes > 0)
    }
    val known get() = id != UNKNOWN_ID

    /** Pass or fail against this profile's pass mark; null while no official pass mark exists. */
    fun passed(result: AssessmentResult): Boolean? =
        passThresholdPercent?.let { result.total > 0 && result.correct * 100 >= it * result.total }

    companion object {
        const val UNKNOWN_ID = "unknown"
        /** Practice defaults while the official format is unknown: 20 questions, about 30 % true/false. */
        val UNKNOWN = ExamProfile(UNKNOWN_ID, questionCount = 20, trueFalseShare = 0.3)
        /** The profile the app uses. Replace it only with the published official format. */
        val CURRENT = UNKNOWN
    }
}
