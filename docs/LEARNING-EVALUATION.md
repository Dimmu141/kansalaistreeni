# What the mastery score can mean

The score measures progress through the installed practice material using a transparent heuristic. It is **not a probability of passing the citizenship test**. Official coverage and learner-outcome evidence are missing.

The added deterministic simulations exercise 6,000 mixed correct/incorrect answers across early repeats, due reviews and long gaps. They check score bounds, count consistency, review scheduling, queue size and equivalence between incremental scoring and replaying stored attempts. They validate implementation consistency, not learning effectiveness.

Explicit counterexamples are retained as tests:

- A perfect first pass gives 100% answer accuracy but only 25% mastery. Recognising an answer once does not establish lasting recall.
- One hundred immediate correct repeats still give 25% mastery and do not postpone review.
- Four successful due reviews can yield 100% mastery. Leaving review overdue for 90 days currently keeps that score at 100%, while the app directs the learner to review. The current score is not a reliable measure of current recall.

The last case is intentional documentation of a limitation, not proof that the algorithm is calibrated. Avoid relabelling this percentage “ready to pass”. Keep material mastery, accuracy, coverage and overdue reviews visible as separate measures.

## Proposed learner evaluation

Before changing the score, recruit learners using Finnish and Swedish material at different language levels. Use a small qualitative pilot to check comprehension and feasibility, followed by a larger study sized for the prediction being evaluated. Do not infer validity from an arbitrary small participant count.

With voluntary participation, record initial answers, whether the learner guessed, repeated exposure, and recall on different wording after delays such as one, three and seven days. Compare observed delayed recall with the displayed mastery, and check whether supporting English improves understanding without removing practice in the study language. This can use manually collected, anonymous records; no analytics SDK is needed.

Use the results to decide whether to add confidence/guess handling, overdue-score decay, different intervals or a clearer presentation. Change one meaningful rule at a time. A rule change must version the engine and replay retained attempts, with migration tests. Avoid silently changing stored history or treating early repetition as delayed recall.

A future readiness estimate additionally needs verified official coverage, representative practice content and outcome data from the real test or a justified comparable assessment. Review calibration separately for each study language. Until that evidence exists, retain the current wording and publish its limits plainly.
