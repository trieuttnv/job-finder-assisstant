# 05: Retrieval filters and expansion

**What to build:** Retrieval finds the right Entries for a question. When a question names a known employer, technology or Entry type, results are filtered or boosted by it; the name lists are built from the Entries, never hand-coded. Every retrieved bullet brings its role header, so answers always know the employer and dates.

**Blocked by:** 02 (CV knowledge base).

**Status:** ready-for-agent

- [ ] "What did you build at Tamara?" retrieves Tamara Entries only
- [ ] Each retrieved bullet is accompanied by its `parent` role Entry
- [ ] Employer, technology and type names are derived from the Entries at startup
- [ ] `RETRIEVAL_TOP_K` (default 6) is honoured
- [ ] Tests through the `Ask` use case on fixture Entries for filtering and expansion
- [ ] AI-usage log entry appended
