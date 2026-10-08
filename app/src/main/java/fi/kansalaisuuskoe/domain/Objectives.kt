package fi.kansalaisuuskoe.domain

/**
 * A learning objective that questions practise. Objectives are the mapping layer for official
 * learning material once it is published; they are not exam topics or an official syllabus.
 */
data class LearningObjective(val id: String, val topicId: String, val statement: Map<String, String>, val sourceRef: String? = null) {
    /** Interface or study language first, then Finnish, which every objective has. */
    fun statementFor(language: String) = statement[language] ?: statement.getValue("fi")
}

data class ObjectiveCoverage(val objective: LearningObjective, val questionCounts: Map<String, Int>) {
    fun count(language: String) = questionCounts[language] ?: 0
    /** Thin in any language that the package teaches. */
    val thin get() = questionCounts.values.any { it < Coverage.MIN_QUESTIONS }
}

data class TopicCoverage(val topic: Topic, val objectives: List<ObjectiveCoverage>) {
    val thinCount get() = objectives.count { it.thin }
}

object Coverage {
    /** Below this many questions in a language, an objective is highlighted for editors. */
    const val MIN_QUESTIONS = 3

    /** Counts are per study language present in the package; an untaught language is not reported as a gap. */
    fun report(topics: List<Topic>, objectives: List<LearningObjective>, questions: List<Question>): List<TopicCoverage> {
        val languages = questions.map { it.language }.toSortedSet()
        return topics.sortedBy { it.order }.mapNotNull { topic ->
            val items = objectives.filter { it.topicId == topic.id }.map { objective ->
                ObjectiveCoverage(objective, languages.associateWith { language ->
                    questions.count { it.language == language && objective.id in it.objectiveIds }
                })
            }
            if (items.isEmpty()) null else TopicCoverage(topic, items)
        }
    }
}
