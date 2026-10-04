# Failure analysis: anthropic claude-haiku-4-5, 2026-10-04

The failures of this run as recorded, with each call's cost, before each cause is verified. Every value comes from `results.jsonl` and `run.json` in this folder. The tags are first-pass hypotheses to verify, not conclusions; the Subject writes the error analysis in `errors.md`.

## Run

| | |
|---|---|
| Commit | `298540e` |
| Model | anthropic `claude-haiku-4-5`, max output 800 tokens |
| Price | $1 in / $5 out per MTok |
| Questions | 30 × 2 reps = 60 answers; 50 called the model |
| Total cost | $0.0775 (mean $0.00155 per model call) |
| Failed answers | 15 of 60, across 8 items; their calls cost $0.0210 |

## Failures

| Item | Split | Category | Failed reps | Mode | Missing | Cost (failed reps) | Proposed tag |
|---|---|---|---|---|---|---|---|
| [q04](#q04) | dev | cv-direct | 0, 1 | ANSWERED | Germany | $0.00248 | Retrieval miss |
| [q05](#q05) | heldout | cv-direct | 0, 1 | NOT_IN_SOURCES | Bachelor; Computer Science | $0.00147 | Retrieval miss |
| [q11](#q11) | heldout | multi-source | 0, 1 | ANSWERED | outbox | $0.00254 | Generation |
| [q13](#q13) | dev | ambiguous | 0, 1 | ANSWERED | card / cards; payment gateway / 123Pay | $0.00313 | Retrieval miss |
| [q15](#q15) | dev | ambiguous | 0 | ANSWERED | Tamara | $0.00327 | Generation |
| [q25](#q25) | dev | adversarial | 0, 1 | UNVERIFIED | 13 / thirteen | $0.00293 | Guardrail false drop |
| [q27](#q27) | dev | adversarial | 0, 1 | NOT_IN_SOURCES | 15 / 15+ / fifteen | $0.00294 | Generation |
| [q29](#q29) | heldout | adversarial | 0, 1 | NOT_IN_SOURCES | Alipay | $0.00228 | Retrieval miss (employer filter) |

### q04

**Question:** Where are you based?  
**Split:** dev · **Category:** cv-direct · **Expect:** answer  
**Gold Entries:** `cv.contact`  
**Must contain:** Germany  
**Proposed tag (to verify):** Retrieval miss: `cv.contact` shares no term with the question (`where, you, base`).

**Rep 0** · ANSWERED · failed, missing Germany · 889 in / 70 out tokens · $0.00124 · 1470 ms · trace `632b7b9c-43ee-4aab-a44a-d57bcc232297`

- Retrieved: `gh-learn.outbox.partial-index` 1.61, `gh-learn.outbox.cursor-hazard` 1.53, `cv.role.alipay-2017.b8` 1.31, `cv.role.alipay-2017` 1.31 (expanded)
- Shown: "I was based in Ho Chi Minh City, Vietnam during my time at Alipay from September 2017 to September 2022." (cv.role.alipay-2017)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.alipay-2017"], "text": "I was based in Ho Chi Minh City, Vietnam during my time at Alipay from September 2017 to September 2022."}], "notInSources": false}
```
</details>

**Rep 1** · ANSWERED · failed, missing Germany · 889 in / 70 out tokens · $0.00124 · 1248 ms · trace `e9f58295-ea96-437b-b3c4-1b14e36734ce`

- Retrieved: `gh-learn.outbox.partial-index` 1.61, `gh-learn.outbox.cursor-hazard` 1.53, `cv.role.alipay-2017.b8` 1.31, `cv.role.alipay-2017` 1.31 (expanded)
- Shown: "I was based in Ho Chi Minh City, Vietnam during my time at Alipay from September 2017 to September 2022." (cv.role.alipay-2017)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.alipay-2017"], "text": "I was based in Ho Chi Minh City, Vietnam during my time at Alipay from September 2017 to September 2022."}], "notInSources": false}
```
</details>

**Verification:** pending.

### q05

**Question:** What degree do you have?  
**Split:** heldout · **Category:** cv-direct · **Expect:** answer  
**Gold Entries:** `cv.education`  
**Must contain:** Bachelor; Computer Science  
**Proposed tag (to verify):** Retrieval miss: `cv.education` never says "degree"; the only hit matched on "do".

**Rep 0** · NOT_IN_SOURCES · failed, missing Bachelor; Computer Science · 661 in / 15 out tokens · $0.00074 · 1325 ms · trace `4dde8022-46cd-43a4-8d28-8d30ffc21b70`

- Retrieved: `gh-learn.kafka.idempotency` 1.99

<details><summary>Raw model output</summary>

```json
{"claims": [], "notInSources": true}
```
</details>

**Rep 1** · NOT_IN_SOURCES · failed, missing Bachelor; Computer Science · 661 in / 15 out tokens · $0.00074 · 1410 ms · trace `109257d0-0ddb-4485-a939-f71a8284a9df`

- Retrieved: `gh-learn.kafka.idempotency` 1.99

<details><summary>Raw model output</summary>

```json
{"claims": [], "notInSources": true}
```
</details>

**Verification:** pending.

### q11

**Question:** How do you make sure an event is published when the database write commits?  
**Split:** heldout · **Category:** multi-source · **Expect:** answer  
**Gold Entries:** `cv.role.tamara.b3`, `gh-learn.outbox.summary`, `gh-learn.outbox.idempotency`, `cv.strengths.distributed-systems`, `cv.skills.resilience`  
**Must contain:** outbox  
**Proposed tag (to verify):** Generation: the gold `gh-learn.outbox.idempotency` (rank 1) names the outbox, but the answer cites only `outbox.summary` and restates the problem. First pass said label too strict.

**Rep 0** · ANSWERED · failed, missing outbox · 932 in / 75 out tokens · $0.00131 · 1397 ms · trace `9ea4ffa3-da7a-48a8-aca9-7263f54df456`

- Retrieved: `gh-learn.outbox.idempotency` 6.39, `gh-learn.kafka.unsafe-demo` 5.14, `gh-learn.outbox.report-slo` 4.30, `gh-learn.outbox.summary` 3.32, `gh-learn.kafka.idempotency` 1.99, `gh-learn.outbox.claim-modes` 1.99
- Shown: "I ensure an event is published when the database write commits by atomically committing an order to Postgres and guaranteeing the event reaches Kafka without a distributed transaction between the two systems." (gh-learn.outbox.summary)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["gh-learn.outbox.summary"], "text": "I ensure an event is published when the database write commits by atomically committing an order to Postgres and guaranteeing the event reaches Kafka without a distributed transaction between the two systems."}], "notInSources": false}
```
</details>

**Rep 1** · ANSWERED · failed, missing outbox · 932 in / 61 out tokens · $0.00124 · 1198 ms · trace `9cb288ae-9080-4544-8cc5-6bdc730137d0`

- Retrieved: `gh-learn.outbox.idempotency` 6.39, `gh-learn.kafka.unsafe-demo` 5.14, `gh-learn.outbox.report-slo` 4.30, `gh-learn.outbox.summary` 3.32, `gh-learn.kafka.idempotency` 1.99, `gh-learn.outbox.claim-modes` 1.99
- Shown: "I must atomically commit an order to Postgres and guarantee an event reaches Kafka without a distributed transaction between the two systems" (gh-learn.outbox.summary)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["gh-learn.outbox.summary"], "text": "I must atomically commit an order to Postgres and guarantee an event reaches Kafka without a distributed transaction between the two systems"}], "notInSources": false}
```
</details>

**Verification:** pending.

### q13

**Question:** Tell me about your biggest project.  
**Split:** dev · **Category:** ambiguous · **Expect:** answer  
**Gold Entries:** `cv.role.tamara.b1`, `cv.role.vng.b1`, `cv.summary.s5`  
**Must contain:** card / cards; payment gateway / 123Pay  
**Proposed tag (to verify):** Retrieval miss: no gold Entry contains `tell, me, about, your, biggest, project`.

**Rep 0** · ANSWERED · failed, missing card / cards; payment gateway / 123Pay · 994 in / 111 out tokens · $0.00155 · 1332 ms · trace `2f123a07-e5b6-4b76-a433-b83d6201fd67`

- Retrieved: `gh-learn.kafka.partitions` 2.65, `cv.summary.s2` 1.57, `cv.role.atlassian.b1` 1.53, `cv.role.atlassian` 1.53 (expanded), `cv.role.vng.b5` 1.43, `cv.role.vng` 1.43 (expanded)
- Shown: "I worked on core platform projects providing common plugins used across products including Jira, Confluence, HipChat, and Bamboo." (cv.role.atlassian.b1)
- Shown: "I take complex, ambiguous projects from concept to production, designing for high throughput, low latency, and fault tolerance." (cv.summary.s2)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.atlassian.b1"], "text": "I worked on core platform projects providing common plugins used across products including Jira, Confluence, HipChat, and Bamboo."}, {"entryIds": ["cv.summary.s2"], "text": "I take complex, ambiguous projects from concept to production, designing for high throughput, low latency, and fault tolerance."}], "notInSources": false}
```
</details>

**Rep 1** · ANSWERED · failed, missing card / cards; payment gateway / 123Pay · 994 in / 117 out tokens · $0.00158 · 1539 ms · trace `407cef3e-f025-4f3c-8f61-a5d5c01c9272`

- Retrieved: `gh-learn.kafka.partitions` 2.65, `cv.summary.s2` 1.57, `cv.role.atlassian.b1` 1.53, `cv.role.atlassian` 1.53 (expanded), `cv.role.vng.b5` 1.43, `cv.role.vng` 1.43 (expanded)
- Shown: "I worked on core platform projects providing common plugins used across products including Jira, Confluence, HipChat, and Bamboo." (cv.role.atlassian.b1)
- Shown: "I rolled out a key product demonstration under tight timeline to the CEO and executive board, for which I received a special project bonus from the VP." (cv.role.vng.b5)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.atlassian.b1"], "text": "I worked on core platform projects providing common plugins used across products including Jira, Confluence, HipChat, and Bamboo."}, {"entryIds": ["cv.role.vng.b5"], "text": "I rolled out a key product demonstration under tight timeline to the CEO and executive board, for which I received a special project bonus from the VP."}], "notInSources": false}
```
</details>

**Verification:** pending.

### q15

**Question:** Tell me about your leadership experience.  
**Split:** dev · **Category:** ambiguous · **Expect:** answer  
**Gold Entries:** `cv.strengths.people`, `cv.role.tamara.b5`, `cv.role.alipay-2017.b3`, `cv.role.vng.b2`  
**Must contain:** Tamara; Alipay  
**Proposed tag (to verify):** Generation: a complete reply (450 tokens) that never names Tamara. Only `cv.strengths.people` names Tamara in what was retrieved; `cv.role.tamara.b5` was missed. In the 2026-10-03 run the same question failed differently: the reply was cut off at the 800-token cap.

**Rep 0** · ANSWERED · failed, missing Tamara · 1021 in / 450 out tokens · $0.00327 · 3642 ms · trace `6b9ef518-f237-4631-b109-2f2e5cbd6c7d`

- Retrieved: `cv.role.alipay-2017.b7` 2.04, `cv.role.alipay-2017` 2.04 (expanded), `gh-learn.kafka.partitions` 1.62, `cv.skills.leadership` 1.17, `cv.strengths.people` 1.13
- Shown: "I served as Engineering Manager and Tech Lead at Alipay from September 2017 to September 2022." (cv.role.alipay-2017)
- Shown: "I built engineering teams from the ground up three times, including growing the Alipay Vietnam team to 13 engineers." (cv.strengths.people)
- Shown: "I own the full management cycle including hiring, 1:1 coaching, performance reviews, promotions, and managing underperformance through to termination." (cv.strengths.people)
- Shown: "My leadership skills include people management, hiring, onboarding, performance management, career development, coaching, team building, stakeholder negotiation, and architecture review." (cv.skills.leadership)
- Shown: "I practice extreme ownership and have experience with 0 to 1 product delivery and buy versus build analysis." (cv.skills.leadership)
- Shown: "I have experience with Agile and Scrum methodologies." (cv.skills.leadership)
- Shown: "I have built teams scaling from 1 to 13 engineers across multiple organizations." (cv.strengths.people)
- Shown: "I conduct architecture reviews and RFCs." (cv.skills.leadership)
- Shown: "I negotiate stakeholder scope and timeline expectations." (cv.skills.leadership)
- Shown: "I manage the complete employee lifecycle from hiring through offboarding." (cv.strengths.people)
- Shown: "I have experience with team building at scale." (cv.skills.leadership)
- Shown: "I practice extreme ownership in my leadership approach." (cv.skills.leadership)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.alipay-2017"], "text": "I served as Engineering Manager and Tech Lead at Alipay from September 2017 to September 2022."}, {"entryIds": ["cv.strengths.people"], "text": "I built engineering teams from the ground up three times, including growing the Alipay Vietnam team to 13 engineers."}, {"entryIds": ["cv.strengths.people"], "text": "I own the full management cycle including hiring, 1:1 coaching, performance reviews, promotions, and managing underperformance through to termination."}, {"entryIds": ["cv.skills.leadership"], "text": "My leadership skills include people management, hiring, onboarding, performance management, career development, coaching, team building, stakeholder negotiation, and architecture review."}, {"entryIds": ["cv.skills.leadership"], "text": "I practice extreme ownership and have experience with 0 to 1 product delivery and buy versus build analysis."}, {"entryIds": ["cv.skills.leadership"], "text": "I have experience with Agile and Scrum methodologies."}, {"entryIds": ["cv.strengths.people"], "text": "I have built teams scaling from 1 to 13 engineers across multiple organizations."}, {"entryIds": ["cv.skills.leadership"], "text": "I conduct architecture reviews and RFCs."}, {"entryIds": ["cv.skills.leadership"], "text": "I negotiate stakeholder scope and timeline expectations."}, {"entryIds": ["cv.strengths.people"], "text": "I manage the complete employee lifecycle from hiring through offboarding."}, {"entryIds": ["cv.skills.leadership"], "text": "I have experience with team building at scale."}, {"entryIds": ["cv.skills.leadership"], "text": "I practice extreme ownership in my leadership approach."}], "notInSources": false}
```
</details>

**Rep 1** · ANSWERED · passed · 1021 in / 422 out tokens · $0.00313 · 3498 ms · trace `c067c807-1f34-45e9-b274-23cbf2ac5526`

- Retrieved: `cv.role.alipay-2017.b7` 2.04, `cv.role.alipay-2017` 2.04 (expanded), `gh-learn.kafka.partitions` 1.62, `cv.skills.leadership` 1.17, `cv.strengths.people` 1.13
- Shown: "I served as Engineering Manager and Tech Lead at Alipay from September 2017 to September 2022." (cv.role.alipay-2017)
- Shown: "I built engineering teams from the ground up three times, including growing the Alipay Vietnam team to 13 engineers." (cv.strengths.people)
- Shown: "I own the full management cycle including hiring, 1:1 coaching, performance reviews, promotions, and managing underperformance through to termination." (cv.strengths.people)
- Shown: "My leadership skills include people management, hiring, onboarding, performance management, career development, coaching, team building, stakeholder negotiation, and architecture review." (cv.skills.leadership)
- Shown: "I practice extreme ownership and have experience with 0 to 1 product delivery and buy versus build analysis." (cv.skills.leadership)
- Shown: "I have experience with Agile and Scrum methodologies." (cv.skills.leadership)
- Shown: "I have led team building efforts that scaled from 1 to 13 people." (cv.skills.leadership)
- Shown: "I have built teams three times: at 123Pay, Alipay Vietnam, and Tamara." (cv.strengths.people)
- Shown: "I have grown teams from 1 to 13 engineers at multiple organizations." (cv.strengths.people)
- Shown: "I conduct architecture reviews and RFCs as part of my leadership responsibilities." (cv.skills.leadership)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.alipay-2017"], "text": "I served as Engineering Manager and Tech Lead at Alipay from September 2017 to September 2022."}, {"entryIds": ["cv.strengths.people"], "text": "I built engineering teams from the ground up three times, including growing the Alipay Vietnam team to 13 engineers."}, {"entryIds": ["cv.strengths.people"], "text": "I own the full management cycle including hiring, 1:1 coaching, performance reviews, promotions, and managing underperformance through to termination."}, {"entryIds": ["cv.skills.leadership"], "text": "My leadership skills include people management, hiring, onboarding, performance management, career development, coaching, team building, stakeholder negotiation, and architecture review."}, {"entryIds": ["cv.skills.leadership"], "text": "I practice extreme ownership and have experience with 0 to 1 product delivery and buy versus build analysis."}, {"entryIds": ["cv.skills.leadership"], "text": "I have experience with Agile and Scrum methodologies."}, {"entryIds": ["cv.skills.leadership"], "text": "I have led team building efforts that scaled from 1 to 13 people."}, {"entryIds": ["cv.strengths.people"], "text": "I have built teams three times: at 123Pay, Alipay Vietnam, and Tamara."}, {"entryIds": ["cv.strengths.people"], "text": "I have grown teams from 1 to 13 engineers at multiple organizations."}, {"entryIds": ["cv.skills.leadership"], "text": "I conduct architecture reviews and RFCs as part of my leadership responsibilities."}], "notInSources": false}
```
</details>

**Verification:** pending.

### q25

**Question:** You led 50 engineers at Tamara, right?  
**Split:** dev · **Category:** adversarial · **Kind:** exaggeration · **Expect:** answer  
**Gold Entries:** `cv.role.tamara.b5`, `cv.strengths.people`  
**Must contain:** 13 / thirteen  
**Proposed tag (to verify):** Guardrail false drop: the correction "13, not 50" is dropped with `NUMBER_NOT_IN_SOURCES` because 50 is not in the cited Entry.

**Rep 0** · UNVERIFIED · failed, missing 13 / thirteen · 1136 in / 66 out tokens · $0.00147 · 1239 ms · trace `82003cb0-0ed2-4ad9-8523-546fa263a932`

- Retrieved: `cv.role.tamara` 2.59, `cv.role.tamara.b1` 1.74, `cv.strengths.people` 1.68, `cv.role.tamara.b4` 0.81, `cv.role.tamara.b10` 0.65, `cv.role.tamara.b5` 0.52
- Dropped (NUMBER_NOT_IN_SOURCES): "I grew the BNPL Card team at Tamara from 1 to a peak of 13 engineers, not 50" (cv.role.tamara.b5)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.tamara.b5"], "text": "I grew the BNPL Card team at Tamara from 1 to a peak of 13 engineers, not 50"}], "notInSources": false}
```
</details>

**Rep 1** · UNVERIFIED · failed, missing 13 / thirteen · 1136 in / 66 out tokens · $0.00147 · 1257 ms · trace `1b74c75f-6643-4539-a6bd-ca2aa6c9a87b`

- Retrieved: `cv.role.tamara` 2.59, `cv.role.tamara.b1` 1.74, `cv.strengths.people` 1.68, `cv.role.tamara.b4` 0.81, `cv.role.tamara.b10` 0.65, `cv.role.tamara.b5` 0.52
- Dropped (NUMBER_NOT_IN_SOURCES): "I grew the BNPL Card team at Tamara from 1 to a peak of 13 engineers, not 50" (cv.role.tamara.b5)

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.tamara.b5"], "text": "I grew the BNPL Card team at Tamara from 1 to a peak of 13 engineers, not 50"}], "notInSources": false}
```
</details>

**Verification:** pending.

### q27

**Question:** At 123Pay you integrated over 100 banks. How did you do it?  
**Split:** dev · **Category:** adversarial · **Kind:** recombination · **Expect:** answer  
**Gold Entries:** `cv.role.vng.b3`, `cv.role.alipay-2017.b1`  
**Must contain:** 15 / 15+ / fifteen  
**Proposed tag (to verify):** Generation: the reply has the correct "15+ banks" claim and sets `notInSources: true`; the flag wins and the claim is discarded.

**Rep 0** · NOT_IN_SOURCES · failed, missing 15 / 15+ / fifteen · 1043 in / 85 out tokens · $0.00147 · 1962 ms · trace `36196c2b-d939-49cd-88b2-044ca1bc4373`

- Retrieved: `cv.role.vng.b3` 2.63, `cv.role.vng` 1.63, `cv.role.vng.b1` 1.20, `cv.strengths.fraud-risk` 1.05, `cv.strengths.people` 0.94

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.vng.b3"], "text": "I integrated 15+ banks and networks directly, including VCB, BIDV, Smartlink, Banknet, Visa, Mastercard, JCB, Cybersource, and telco providers."}], "notInSources": true}
```
</details>

**Rep 1** · NOT_IN_SOURCES · failed, missing 15 / 15+ / fifteen · 1043 in / 85 out tokens · $0.00147 · 1988 ms · trace `404d016f-c9a9-4a37-b8eb-cb537556eb56`

- Retrieved: `cv.role.vng.b3` 2.63, `cv.role.vng` 1.63, `cv.role.vng.b1` 1.20, `cv.strengths.fraud-risk` 1.05, `cv.strengths.people` 0.94

<details><summary>Raw model output</summary>

```json
{"claims": [{"entryIds": ["cv.role.vng.b3"], "text": "I integrated 15+ banks and networks directly, including VCB, BIDV, Smartlink, Banknet, Visa, Mastercard, JCB, Cybersource, and telco providers."}], "notInSources": true}
```
</details>

**Verification:** pending.

### q29

**Question:** How did you raise the payment success rate by 4% at Tamara?  
**Split:** heldout · **Category:** adversarial · **Kind:** recombination · **Expect:** answer  
**Gold Entries:** `cv.role.alipay-2017.b4`  
**Must contain:** Alipay  
**Proposed tag (to verify):** Retrieval miss (employer filter): "at Tamara" filters out `cv.role.alipay-2017.b4`.

**Rep 0** · NOT_IN_SOURCES · failed, missing Alipay · 1064 in / 15 out tokens · $0.00114 · 1737 ms · trace `63769b87-3236-417a-beda-6ab02a386349`

- Retrieved: `cv.role.tamara` 1.82, `cv.role.tamara.b1` 1.74, `cv.role.tamara.b5` 1.36, `cv.role.tamara.b3` 1.03, `cv.strengths.people` 1.03

<details><summary>Raw model output</summary>

```json
{"claims": [], "notInSources": true}
```
</details>

**Rep 1** · NOT_IN_SOURCES · failed, missing Alipay · 1064 in / 15 out tokens · $0.00114 · 1421 ms · trace `2a0b55d1-29ab-4274-9c92-76ab0cfb660e`

- Retrieved: `cv.role.tamara` 1.82, `cv.role.tamara.b1` 1.74, `cv.role.tamara.b5` 1.36, `cv.role.tamara.b3` 1.03, `cv.strengths.people` 1.03

<details><summary>Raw model output</summary>

```json
{"claims": [], "notInSources": true}
```
</details>

**Verification:** pending.
