# Interview Assistant

A queryable, grounded representation of one person's professional background, answering interviewer questions from committed sources.

## Language

### People

**Subject**:
The one real person the assistant represents.
_Avoid_: user, candidate, owner

**Interviewer**:
The person asking the assistant questions.
_Avoid_: user, client, recruiter

### Knowledge

**Source**:
One original document the knowledge base is built from (the CV, one repository snapshot), with fixed provenance.
_Avoid_: document, file, input

**Snapshot**:
The committed copy of a **Source** as it was on a recorded date (and commit, for a repository), which is all the assistant ever reads.
_Avoid_: export, dump, cache

**Entry**:
One typed, self-contained statement from a **Source** (a role, a bullet, a skill group, an education line) with a stable id and the verbatim **Quote** it came from. The smallest thing a **Citation** can point at.
_Avoid_: chunk, fact, passage, snippet

**Quote**:
The exact text in a **Snapshot** that an **Entry** was drawn from.
_Avoid_: excerpt, source text

**Conflict**:
A recorded disagreement between two or more **Entries** about the same topic, together with the rule for answering when a question depends on it.
_Avoid_: inconsistency, discrepancy

**Redaction**:
Text removed from a **Snapshot** before commit, replaced by a labelled placeholder.
_Avoid_: masking, anonymisation

**Synthetic**:
Content written for testing rather than taken from the **Subject**'s real material; always labelled as such.
_Avoid_: fake, mock

### Answering

**Input Guardrail**:
A deterministic rule applied to a question before any model sees it, deciding whether it is refused, answered as "not in my sources", or let through.
_Avoid_: input check, filter, pre-check

**Output Guardrail**:
A deterministic check, with no model, of everything the model proposes against the **Entries** it cites; only what passes is shown to the **Interviewer**.
_Avoid_: code check, validator, post-check

### Evaluation

**Gold Label**:
The checkable statement of what a correct answer to one eval question must cite, must contain and must never claim, verified by the **Subject**.
_Avoid_: expected answer, ground truth

**Hard Line**:
A result that fails the eval run outright: any hallucination on an adversarial question, or any **Entry** whose **Quote** is not in its **Snapshot**.
_Avoid_: threshold, gate
