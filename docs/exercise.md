# The problem

Technical interviews open with the same questions: who are you, what problems have you solved, how do you approach your work, and what have you built?

Build an AI assistant that answers those questions on your behalf — a queryable, grounded representation of your own professional background that an interviewer could interact with to learn about your experience, projects, technical decisions, and working style.

The subject is you, nothing resembling unpaid work for us. The exercise is self-contained: no other systems, no production data, and no internal access.

One constraint carries the weight of the role: **The assistant must not invent qualifications, employers, projects, or achievements.** A system that fabricates a role in the financial industry is a failure. We're AI-native and expect to use AI heavily in how we build. We also hold the line that nobody can trace the model's output back to a source. Show us how you hold that line in your own design.

Use whatever stack you think fits — commercial model APIs, open-source models, a search engine, a framework, or none of the above. There is no expected stack, and the reasonable choice scores no better than a justified one.

# Your tasks

Work through all five. Depth beats length. Every section is graded on your reasoning as much as on what runs.

## 1. Personal knowledge layer — 25%

Give the assistant something real to be grounded in.

Build a focused knowledge base from sources such as your CV, LinkedIn profile, writing, talks, GitHub repositories or project documentation, and anything else that reflects how you work. **Your CV is the minimum requirement.** You are not expected to build an integration for every source — show how you would collect, clean, structure, and index this information.

Make clear in your repository:

- What information the assistant can see and how you collected it.
- How it is stored, structured, and maintained.
- How the system retrieves the most relevant context for a given question.
- How an additional source could be added later without redesigning what already exists.
- **One real conflict** in your own material — an overlapping date range, a difference between your CV and LinkedIn, an unfinished project, or another inconsistency — and exactly what the system does when a question depends on it.
- Anything you redacted or replaced with synthetic content, and why.

No minimum public footprint is expected. A thin CV plus clearly labelled synthetic material is a legitimate answer — we're grading the pipeline, not your career.

This ships as a repository we'll read and you'll keep, so **commit only what you're comfortable having us read**. Redacting something costs you nothing, and neither does labelling synthetic content as synthetic.

## 2. AI system and back-end architecture — 25%

Design and implement the intelligence behind the agent or tool-using workflow, structured QA pipeline, prompting, and deterministic logic — your call. Retrieval-augmented generation, a combination of retrieval, routing, and generation, or another approach are all acceptable.

Your write-up should let a reviewer predict the system's behaviour without reading every line. Cover:

- **Why this architecture** — and the simpler approach you considered and rejected, with what decided it.
- **How retrieval, prompting, and any tool use actually work**, concretely.
- **How stated facts are traced back to their sources**, and what the system does when it can't be.
- **Model choice**, and what evidence would make you switch.
- **Failure paths** — the model API is slow, down, or rate-limited; what the user sees; what the deterministic fallback is; and what happens when the model returns something unusable.
- **Security and privacy** — where secrets live, what data leaves your machine, and what a provider may retain or train on.
- **Rough cost per question and measured p95 latency**; give two real numbers and explain what they say about scalability.
- **What you would change before this went to production**, and what breaks first at 100× the traffic.

The repository should take its configuration from environment variables and ship an `.env.example` naming each one. **Please don't commit a real key** — if one slips in, rotate it and tell us; we'd far rather hear it from you.

## 3. Evaluation, reliability, and testing — 30%

The heaviest section, deliberately. Where being wrong is expensive, “it seemed to work when I tried it” is not evidence.

Build an evaluation you actually run, and commit it:

- **An evaluation dataset** — at least **20 questions** an interviewer or recruiter might realistically ask. Each must be labelled with its category and what a correct answer must contain or must never claim. Include all five of these categories:
    - Answerable directly from the CV.
    - Requiring two or more sources combined.
    - Ambiguous or underspecified.
    - **Not answerable from the knowledge base**, where the assistant must say so.
    - **Adversarial** — attempts to make it exaggerate or fabricate your experience, step outside scope, or follow an injected instruction.
- **A runnable evaluation** — one documented command that scores the dataset and prints a table.
- **The results you actually got**, committed, with at least two failures. A submission reporting 72% groundedness with where it broke scores above one reporting 100% with no method.
- **An honest error analysis** of the failures.
- **Metrics you define precisely** — pick a few that matter, such as:
    - Accuracy.
    - Retriever relevance.
    - Answer correctness.
    - Groundedness or citation correctness.
    - Answerability-question accuracy.
    - Hallucination rate.
    - Refusal correctness on unanswerable questions.
    - Consistency across reworded questions.
    - Latency.
    
    State the numerator, denominator, and what counts as a pass. A metric nobody can recompute is not a metric.
    
- If you use **LLM-as-judge**, commit the judge prompt, sample it against your own labels, report the agreement, and name the safeguard — for example, spot-check agreement.
- Explain what you would add to this evaluation if it were guarding money movement rather than a CV, and how it would run continuously rather than once.

## 4. User experience — 10%

Provide a usable way to interact with the assistant. A command-line application, notebook, small web app, or chat UI — a basic interface is fine if the system behind it is well designed. We'd rather see a CLI that shows its sources than a polished chat window that doesn't.

Two behaviours must be visible in whatever you build:

- The **sources behind an answer**.
- A **clear fallback** when the assistant can't answer from what it knows.

Anything else — suggested questions, tone that sounds like you, conversation history, streaming, confidence indicators — is optional. Add one line on the interaction decision you made and who it serves.

## 5. Engineering leadership, decisions, and AI use — 10%

This section is writing, not building. Keep it short and specific.

- **A decision record** — three to five decisions you made, and the constraint or evidence that decided each one. Include the alternative you rejected.
- **What you cut**, why it was the right thing to cut inside the time box, and the next three things you would do.
- **Where you used AI, and where you deliberately did not** — which parts were model-generated, what you changed after reviewing them, and one thing you refused to let a model decide. Using AI heavily is expected here; not being able to account for what it produced is the problem.
- **A self-review** — the two changes you would ask for if this arrived as someone else's pull request.

# Submission

Please send us a **GitHub repository** rather than a ZIP file or an email attachment — we want to read the history as well as the code.

1. **Repository name:** test-`rieu-nguyen` (lowercase and hyphenated)
2. **Visibility:** public, or private
    - Private is OK;
3. **A root README.md that is the entry point.** Another engineer should be able to understand and run your project from it with no further guidance. It must cover:
    - what this is
    - how to install and run it in under ten minutes
    - the architecture and why
    - the evaluation approach and the results you got
    - known limitations and what you would do next
    - your AI-usage note
    - the time you spent
4. **We need to be able to run it.** One documented command path, pinned dependencies, `.env.example` naming every variable, tell us which provider, and no committed secrets. If we'll need an API key, tell us which provider and roughly what one evaluation run costs.
5. **Commit the knowledge sources** — real, runnable, along with your evaluation dataset, evaluation script, and committed results — redacted or synthetic — so the system is actually runnable.
6. **Work in real commits.** A sequence of commits with meaningful messages is part of the submission, and a single "initial commit" containing a finished system tells us much less. The history is genuinely useful to us — it shows how you worked, not just what you landed on.
7. **Declare anything you did not write for this exercise** — a project you built on. Reuse is fine; undisclosed reuse is not.
8. **Tag the commit you want reviewed:** `git tag submission && git push --tags` (so later pushes don't confuse the review).