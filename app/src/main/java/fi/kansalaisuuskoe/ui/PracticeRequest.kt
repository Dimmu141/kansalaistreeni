package fi.kansalaisuuskoe.ui

/** Topic IDs have their own argument; they never share the command namespace. */
internal sealed interface PracticeRequest {
    data object Daily : PracticeRequest
    data object Review : PracticeRequest
    data class Topic(val id: String) : PracticeRequest
    data class Mistakes(val assessmentId: String) : PracticeRequest

    companion object {
        fun fromArguments(mode: String?, topicId: String?, assessmentId: String?): PracticeRequest = when {
            assessmentId != null -> Mistakes(assessmentId.also { require(it.isNotBlank()) })
            topicId != null -> Topic(topicId.also { require(it.isNotBlank()) })
            mode == null || mode == "all" -> Daily
            mode == "review" -> Review
            // Support sessions saved before topic IDs became a separate argument.
            else -> Topic(mode)
        }
    }
}
