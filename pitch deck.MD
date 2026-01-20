## Eutron (EduDron) — Pitch Deck (Meeting Walkthrough)

> This is a **talk-track + reference doc** you can read from in a meeting. It includes **product features**, **current architecture**, **scaling posture**, **infrastructure**, **unit economics**, and **recommended pricing** (especially for psychometric tests).

---

## 1) One-liner

**Eutron is a multi-tenant learning + career guidance platform**: institutions can manage courses/batches/students and students can learn, track progress, and take an **adaptive psychometric test (RIASEC v2)** that generates curated recommendations and a guidance report.

---

## 2) What exists today (demo-friendly feature list)

### Admin-side (Admin Dashboard)
- **Course creation + publishing**
- **Content management** (sections/lectures, rich content)
- **Batch management** (cohorts per course)
- **User management** (view/search/filter)
- **Bulk student upload** (mentioned in platform features)
- **AI-assisted course generation (optional)** via Azure OpenAI for:
  - extracting course requirements from a prompt/PDF-like structure
  - generating module/lecture structure and lecture content
  - generating exam questions

### Student-side (Student Portal)
- **Auth + protected routes**
- **Course catalog** (browse/search/filter)
- **Enrollment**
- **Learning experience** (consume content, mark completion)
- **Progress tracking** (course + lecture progress)
- **Psychometric Test (v2)**:
  - starts a session, asks ~30 questions (configurable)
  - adaptive behavior + optional early stop
  - results page shows RIASEC scores, explanations, and recommended courses
  - past results list

### Payments (Payment Service)
- **Subscriptions + payments** with **Razorpay integration**
- **Webhook handling** to finalize payment and trigger downstream actions (e.g., enrollment)

---

## 3) The “hero flow” to explain in the meeting

### A) Learning platform flow
1. Admin creates and publishes a course (optionally uses AI to generate structure/content).
2. Student browses catalog, enrolls (free or paid).
3. Student consumes lectures and progress is tracked.
4. Batch-level reporting is available for admins.

### B) Psychometric Test (RIASEC v2) flow (the story you tell)
1. Student starts the test → a session is created.
2. Platform serves the next question (adaptive).
3. Student answers → system updates the confidence/score snapshot.
4. When stable (or max questions reached), the student can **complete early**.
5. Result is persisted (scores + recommendations + stored explanations + report), and the results page **polls until ready** if generation is still in progress.

---

## 4) Current architecture (as built)

### Microservices (Spring Boot 3 + Java 21)
- **Gateway** (API gateway; routing + CORS; high connection pool limit configured)
- **Identity** (authentication + user management + RBAC)
- **Content** (courses/media; AI generation; psych test v2 lives here)
- **Student** (enrollments, progress, assessments; batch-related APIs)
- **Payment** (subscriptions, payments, Razorpay webhook)

### Data stores
- **PostgreSQL 16** as the system of record (multi-schema, multi-tenant)
  - `common` (tenants/audit/outbox)
  - `idp` (users/roles/permissions)
  - `content` (courses/modules; psych test bank + sessions + results)
  - `student` (enrollments/progress/assessments)
  - `payment` (subscriptions/payments)
- **Redis** for caching / fast access paths
- **Azure Blob Storage** (optional but recommended) for media storage and HTTP range support

### Frontend
- Next.js apps
  - **Admin Dashboard** (port 3000 in dev)
  - **Student Portal** (port 3001 in dev)

### Observability + secrets
- **Application Insights + Log Analytics**
- **Key Vault**

---

## 5) Architecture diagram (mental model)

```
 Browser (Student/Admin)
        |
        v
   Next.js Frontends
        |
        v
  Spring Cloud Gateway  (CORS, routing, timeouts, pooling)
        |
        +--> Identity Service (auth, RBAC, /auth, /idp)
        +--> Content Service  (courses/media, psych-test v2, AI generation)
        +--> Student Service  (enrollment/progress/batches)
        +--> Payment Service  (Razorpay, subscriptions, webhooks)
        |
        v
 PostgreSQL (schemas)  +  Redis
        |
        v
 Azure Blob Storage (media)
 Azure OpenAI (optional AI features)
 App Insights (logs/metrics/traces)
```

---

## 6) Psychometric Test v2 — what’s special (and how it works)

### What it is
- **Adaptive RIASEC test (v2)** with **DB-driven** question bank and versioning.
- **Deterministic scoring** for RIASEC (0–100), with confidence computation.
- Optional **AI “polish”** and **AI report generation** (guardrailed) if Azure OpenAI is configured.

### What is deterministic vs AI
- **Deterministic (always-on, cheap, stable):**
  - question bank + options stored in Postgres (`content.psych_test_*`)
  - scoring logic: Likert + scenario MCQ affect scores; open-ended is supporting-only
  - mapping to stream suggestion + career fields
  - course recommendations from curated tags
  - fallback report JSON and fallback domain narratives (no AI)
- **Optional AI (only if configured):**
  - choose next question from an eligible set (guardrail: cannot invent questions)
  - rewrite prompts/option labels to be more personal (name, prior answer reference)
  - generate narrative report JSON, per-answer meanings, per-domain narratives

### Auditability + idempotency
- Every served question can be stored as “asked” (`psych_test_question_asked`), including the **rendered prompt/options** and whether it came from **RAW/TEMPLATE/AI**.
- Results store `explanations_json` so the results page doesn’t have to re-run AI or recompute explanations.

### What to say about safety
- Guardrails explicitly avoid sensitive trait inference; language is “guidance, not diagnosis”.
- This is positioned as **career planning guidance**, not clinical diagnosis.

---

## 7) Infrastructure (as defined in repo)

### Azure (target deployment)
- **Azure Container Apps Environment** (runs containerized services; can autoscale)
- **Azure Database for PostgreSQL Flexible Server**
  - configured with backups (7 days) and HA disabled by default (can be enabled for production)
- **Azure Cache for Redis**
- **Azure Storage Account** + private container for media
- **Key Vault**
- **Application Insights + Log Analytics**

### Local/dev parity
- Docker Compose for **Postgres + Redis**
- Docker Compose (dev-full) includes all services with correct dependency ordering and health checks.

---

## 8) Scale today (honest framing) and what limits first

### What the current design is good at
- **Horizontal scaling of stateless services** (Gateway/Identity/Content/Student/Payment) via Container Apps.
- Clear service boundaries for scaling independently (e.g., psych test traffic scales Content; auth scales Identity).
- **Media offload to Blob** instead of serving large binaries from DB.

### What typically becomes the first bottleneck
- **PostgreSQL** (connections, IOPS, hot tables/indexes) under high concurrency.
- **AI-heavy psych test mode** if enabled per-question:
  - personalization can trigger ~1 AI call per question
  - adaptive selection can trigger another AI call per question
  - completion can trigger several more calls (report + explanations)
  - this can dominate cost and latency if not controlled

### Practical “current scale” statement you can use
- **Without AI-per-question**, the psych test path is primarily DB reads/writes and deterministic compute; it should comfortably support **large cohorts** with typical web concurrency patterns, and the services can scale horizontally.
- **With AI-per-question enabled**, throughput is capped by **Azure OpenAI rate limits** and cost; scaling becomes a combination of **queueing + rate limiting + caching**.

---

## 9) How we scale up for “more students”

### A) Application scaling (fastest wins)
- **Autoscale Container Apps** per service based on CPU/concurrency/HTTP queue length.
- **Isolate hot paths**:
  - split psych-test endpoints into a dedicated workload if needed
  - scale Gateway and Content independently
- **Move long-running tasks async**:
  - AI report/explanations generation can be processed in background and the UI already supports polling.
- **Rate limit + circuit break AI calls** (protect cost + latency).

### B) Database scaling (the real lever)
- **Increase Postgres tier + storage + IOPS** as concurrency grows.
- Enable **high availability** for production reliability.
- Add **read replicas** for read-heavy traffic (catalog, results views).
- Add targeted indexes and partitioning if specific tables become hot (e.g., psych sessions/answers).
- For very large tenants: consider **tenant-based sharding** (separate DB per institution) while keeping the same service code.

### C) Media scaling
- Use **direct-to-Blob uploads** via pre-signed URLs (reduce backend bandwidth/CPU).
- Put a CDN in front of Blob for global streaming performance.

### D) AI scaling / cost control (critical)
- **Best practice**: AI at completion, not per question.
  - keep adaptive selection deterministic (it already exists)
  - keep question text template-based
  - run AI once to produce the report/explanations
- If AI-per-question is desired:
  - batch prompts, cache responses, cap personalization frequency
  - use smaller/cheaper models for rewriting, larger models only for the final report

---

## 10) Cost model (what costs money, and why)

### Fixed-ish monthly platform costs (baseline)
- **Container Apps compute**: 5 services + gateway running 24/7 (cost scales with vCPU/memory and always-on vs scale-to-zero).
- **PostgreSQL flexible server**: biggest “always-on” cost driver; HA increases cost.
- **Redis**: moderate monthly cost; improves latency and reduces DB pressure.
- **Blob storage**: depends on media volume + egress (video dominates).
- **Observability**: App Insights/Log Analytics (scales with ingestion GB/day).
- **Network egress**: can be meaningful if video traffic is high.

### Variable costs (scale with usage)
- **Azure OpenAI** (tokens + rate limits): course generation + psych test narratives/explanations if enabled.
- **Payment gateway fees** (Razorpay): percentage per transaction + GST and settlement rules.

---

## 11) Unit economics: psychometric test “cost per attempt”

### Cost components per test attempt
- **Core compute (deterministic)**: effectively negligible per attempt (a few dozen API calls + DB writes).
- **Database**: a few KB writes + indexed reads. Cost shows up in DB tier/IOPS, not per-request billing.
- **AI (optional)**: this is the swing factor.

### Two recommended operating modes

#### Mode 1: “AI-light” (recommended default for scale + margin)
- No AI calls per question.
- Optional AI only at completion:
  - one call for narrative report JSON
  - optionally one call for domain narratives
  - optionally one call for per-answer meanings (can be merged/reduced)
- **Result**: predictable latency/cost, easy to scale for large student numbers.

#### Mode 2: “AI-rich” (premium experience)
- AI-assisted selection and/or personalization per question.
- **Result**: better UX, but cost and rate-limit constrained; must add throttling/queueing.

### Simple rule of thumb (so you can quote something in the meeting)
- **AI-light**: per test attempt cost is dominated by shared infra, so marginal cost is very low.
- **AI-rich**: marginal cost becomes “(AI calls per attempt) × (tokens per call) × (model price)”, and needs strict guardrails.

---

## 12) Recommended pricing: psychometric test (what you should charge)

> Pricing depends on whether you sell B2C or B2B (schools), and whether you include AI narratives.

### Suggested price points (India-focused, INR)

#### B2C (individual student)
- **₹149–₹299 per attempt** (recommended default: **₹199**)
  - includes adaptive test + results + course recommendations + downloadable report (if you later add PDF)
- Premium upsell:
  - **₹399–₹799**: includes counselor-style narrative + “next 30 days plan” + multiple retakes (e.g., 2 retakes)

#### B2B (schools/institutions)
- Sell in bundles (volume discount) to align with procurement:
  - **₹60–₹120 per student** for a single test attempt (recommended default: **₹99**) at 1,000+ volume
  - **₹150–₹250 per student/year** for 2 attempts + analytics dashboards + cohort insights

### Why this pricing makes sense
- It’s in a range where schools can buy at scale and students can buy impulsively.
- Leaves room for:
  - infra and support
  - model costs (if AI report is included)
  - channel margin (school/counselor partnerships)

---

## 13) Pricing: AI course generation (if you monetize it)

### What it is
Admin uses prompts/docs to generate:
- course requirements
- module/lecture structure
- lecture content (markdown)
- optionally exam questions

### Suggested pricing models
- **Per generated course**: ₹499–₹1,999 depending on size (modules × lectures) and whether it includes exams.
- **Credits**:
  - 1 credit = generate 1 course structure
  - 1 credit = generate 1 lecture content
  - 1 credit = generate 1 exam (N questions)
- **Institution subscription**: fixed monthly + usage-based AI add-on (safer margin)

### Talk-track: cost control
- “We can bound AI cost by limiting lecture length, caching, and running generation async with admin approval gates.”

---

## 14) Roadmap (credible next steps)

### Scale & reliability
- Add async job queue for AI workloads (report generation, course generation)
- Introduce resilience patterns: retries, circuit breakers, backpressure
- Postgres HA + read replicas, tenant-level sharding path for large institutions

### Product expansion
- Psych test analytics for counselors/institutions (cohort-level trends)
- PDF report generation and share links
- More assessment types and proctoring-lite options

---

## 15) FAQ bullets you will be asked (and good answers)

### “Is the psych test scientifically valid?”
- “We use the **RIASEC framework** with deterministic scoring and versioned question banks. We present it as **guidance, not diagnosis**, and we can iterate the bank with audits and outcome studies.”

### “Is AI mandatory?”
- “No. The system works fully without AI; AI is an optional enhancement for personalization and narrative reporting.”

### “Can this handle 10× more students?”
- “Yes—the services are stateless and designed to **scale horizontally**. The main work is sizing **Postgres**, adding replicas/HA, and throttling AI workloads if enabled.”

### “What’s the biggest cost driver?”
- “Video bandwidth/storage and database sizing at high concurrency; and if AI is enabled heavily, Azure OpenAI becomes a major variable cost.”

---

## Appendix: concrete technical anchors (for credibility)

- **Gateway routes** include `/api/psych-test/**` → Content Service.
- **Psych test tables** include session/answer/result + asked-question audit.
- **Large video uploads** are supported up to ~2GB per request in Content Service and streamed to storage (to avoid memory OOM).
- **Results generation** is designed to be persisted once and fetched/polled from the UI.

