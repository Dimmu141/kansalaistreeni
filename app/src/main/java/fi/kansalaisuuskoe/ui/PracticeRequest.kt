package fi.kansalaisuuskoe.ui

import java.net.URLDecoder
import java.net.URLEncoder

/** Topic IDs have their own argument; they never share the command namespace. */
internal sealed interface PracticeRequest {
    data object Daily : PracticeRequest
    data object Review : PracticeRequest
    data class Topic(val id: String) : PracticeRequest
    data class Mistakes(val assessmentId: String) : PracticeRequest
    /** The learner's own hard-questions list in the study language. */
    data object Marked : PracticeRequest
    /** Missed questions from a finished session, practised again straight away. */
    data class Retry(val questionIds: List<String>) : PracticeRequest

    companion object {
        private const val ID_SEPARATOR = ","
        /** Each ID is encoded on its own, so an imported ID containing the separator survives the round trip. */
        fun retryArgument(ids: List<String>) = ids.joinToString(ID_SEPARATOR) { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        private fun retryIds(argument: String) = argument.split(ID_SEPARATOR).map { URLDecoder.decode(it, Charsets.UTF_8.name()) }

        fun fromArguments(mode: String?, topicId: String?, assessmentId: String?, questionIds: String? = null): PracticeRequest = when {
            assessmentId != null -> Mistakes(assessmentId.also { require(it.isNotBlank()) })
            topicId != null -> Topic(topicId.also { require(it.isNotBlank()) })
            questionIds != null -> Retry(retryIds(questionIds).also { ids -> require(ids.isNotEmpty() && ids.all { it.isNotBlank() }) })
            mode == null || mode == "all" -> Daily
            mode == "review" -> Review
            mode == "marked" -> Marked
            // Support sessions saved before topic IDs became a separate argument.
            else -> Topic(mode)
        }
    }
}
