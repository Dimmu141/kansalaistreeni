# Short adaptive learning sessions — v0.12.0

Home should give the learner a manageable next action, even when reviews have accumulated. Its main action starts up to five eligible questions. This is a session size, not a daily quota, official exam format or proven learning prescription. Another session remains available immediately when eligible material remains.

## Selection policy

`domain/DailyLearningPlan.kt` is a pure, deterministic planner. Callers first scope their snapshot to the selected study language. Finnish and Swedish history never influence each other.

1. Collect due questions with current-revision attempted progress. Prioritise the oldest review deadline; at equal deadlines, lower question mastery comes first. Stable input order resolves remaining ties.
2. Reserve up to three of the five slots for reviews. The other slots can introduce unseen questions. If there are fewer reviews, new questions fill the available slots.
3. Rank familiar topics by average mastery of **practised current-revision questions**, then least recent study, then explicit topic order. Unseen questions do not lower this comparison: lack of exposure is different from evidence of difficulty. A topic with practised mastery below 100 is provisionally a focus topic.
4. Interleave these focus topics with unexplored topics, then add topics whose practised questions have mastery 100. Round-robin selection gives each topic one unseen question before selecting another from it. Source order resolves question choice within a topic. When at least two new slots exist, both the first focus topic and an unexplored topic receive a slot if available.
5. Return unused new slots to due reviews. Never fill a short session with an early repeat merely because mastery is low. A review-only session is also capped at five. Explicit topic practice still permits extra practice under the existing rules.

Home keeps the material provenance label beside the heading and shows the actual review/new mix, an optional focus topic and remaining backlog. The secondary action opens the entire due queue; the Review tab also retains that queue. The remaining count describes the backlog after answering the selected reviews; newly missed questions may become due later.

Each question explains why it was selected: due review, new question in a focus topic, first exposure to a topic, or more new material from a previously practised topic. Reasons describe selection at session start, not a live diagnosis of the learner.

## Persistence and compatibility

The planner writes nothing. Existing Room attempts, mastery rules, review scheduling, content schema, package version, backup format and assessment storage are unchanged.

`QuestionViewModel` saves chosen IDs, revisions, reason identifiers, position and study language in `SavedStateHandle`. Answer feedback retains the original reason even after progress changes. Activity or saved-task process restoration keeps that plan; it does not silently choose a different question. A practised-content revision change rejects an active restored session. Completed recaps retain counts and withhold incompatible answer details. Older saved sessions without reasons keep their original order and show no invented rationale.

## Verification and limitations

Domain tests exercise backlog limits, slot reuse, topic coverage, weak/unexplored fairness, mastery comparisons, review deadlines, absent topic metadata, small/empty pools, revised/removed progress, language isolation, determinism and successive new sessions without repetition. Device tests exercise the Home/full-backlog paths, actual answer persistence, focus copy, recreation and saved-state restoration. See [the versioned verification record](VERIFICATION-0.12.0.md).

Five questions and the three/two review/new allocation are transparent product heuristics. A single mistake can make a tiny demo topic a focus topic; the wording asks for more practice without calling the learner weak. The app does not claim that this allocation improves retention or predicts passing. Independent content review and learner evaluation remain postponed. Review dates still use the device clock, mastery does not decay, and overdue questions are not repeatedly forced into Home ahead of all new learning. A larger reviewed content pool is needed to judge this policy usefully.
