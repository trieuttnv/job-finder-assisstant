# Open discussion

## 1. We ask the model not to repeat the wrong number (q25)

**The point to discuss:** to get q25 past the Output Guardrail, `answer-v2` tells the model to drop the "50" from its correction. A person would say "No, not 50. I grew the team to 13." The model now says "I grew the team to 13.", which is less natural in conversation. The Output Guardrail is not loosened to allow the natural wording.

### Cause

"You led 50 engineers at Tamara, right?" The model answered correctly, "I grew the BNPL Card team at Tamara from 1 to a peak of 13 engineers, not 50", but the Interviewer saw "I couldn't put together a verified answer."

- The Output Guardrail requires every number in a claim to be in its cited Entries ([`OutputGuardrail.java:103`](core/src/main/java/io/github/trieunv/assistant/domain/answer/OutputGuardrail.java)). It cannot tell a denied number ("not 50") from a claimed one, so the claim is dropped with `NUMBER_NOT_IN_SOURCES`.
- With no claim kept, the answer is withheld (`OutputGuardrail.java:57`). `GUARD_MAX_DROP_RATIO` plays no part.
- `answer-v1` never said how to correct a false premise, and the natural way, repeating the wrong value, is exactly what the guardrail drops.

Any exaggeration question can hit this, not only q25.
