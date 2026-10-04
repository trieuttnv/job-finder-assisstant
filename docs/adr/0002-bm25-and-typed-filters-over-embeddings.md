# BM25 and typed filters, not embeddings

Retrieval is Lucene BM25 over Entries, with a filter when the question names a known employer and a boost when it names a known technology or Entry type (a type filter would hide the Entries that answer "What skills did you use at Alipay?"), plus expansion to each hit's role header and to the other side of any Conflict. We rejected embeddings and hybrid search because the corpus is small and the questions turn on exact names, dates and technologies (where dense retrieval is weakest). BM25 is deterministic and explainable, and needs no embedding model. Recall@k per question category is measured; if paraphrase misses show up there, hybrid search is the first upgrade.

An Entry type is named by the words an interviewer uses for it ("degree" or "university" for education, "job" or "position" for a role, "achievement" for a bullet), not only by its code. "project" is not a word for the `project` type: those Entries are learning prototypes ("prototype" names them), and an interviewer's "project" means work. These words do not fix paraphrase misses that share no word at all with the Entry ("Where are you based?" against "Germany"); the 2026-10-04 runs show five such dev questions, so hybrid search remains the next step.

## Considered Options

- **Whole corpus in the prompt.** It fits today, so nothing can be missed, but the brief asks how retrieval picks context, it scales badly with more Sources, and a larger context invites cross-Entry invention.
- **Dense or hybrid (BM25 + vectors, RRF).** Better on paraphrase, but adds an embedding model and its own failure mode, which buys little on this corpus.
