# CLAUDE.md - Project Context & Development Guide

## Project Overview
EduDron is a multi-tenant educational platform with:
- **Admin Dashboard** (Next.js, port 3000) — `frontend/apps/admin-dashboard/`
- **Student Portal** (Next.js, port 3001) — `frontend/apps/student-portal/`
- **Backend** (Spring Boot microservices, gateway port 8080) — root-level modules
- **Shared Utils** (`@kunal-ak23/edudron-shared-utils`) — `frontend/packages/shared-utils/`
- **UI Components** (`@kunal-ak23/edudron-ui-components`) — `frontend/packages/ui-components/`

## Architecture Overview

### Service Map
```
                    ┌─────────────────┐
   Frontend ──────► │  Gateway (8080) │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼                              ▼
   ┌──────────────────┐          ┌──────────────────┐
   │  Core API (8085) │          │  Content (8082)   │
   │  ┌─ Identity     │          │  Courses, Lectures│
   │  ├─ Student      │          │  AI Generation    │
   │  └─ Payment      │          │  Media Uploads    │
   └──────────────────┘          └──────────────────┘
```

- **Gateway**: Spring Cloud Gateway (reactive). Routes requests, logs traffic, propagates headers. Does NOT validate JWTs — only logs them.
- **Core API**: Bundles identity, student, and payment modules into a single deployable. Manages users, tenants, enrollments, payments.
- **Content**: Standalone service for courses, lectures, exams, AI generation, media uploads. Heaviest workload (AI + video processing).
- **Common**: Shared module (not a service) — base classes, utilities, domain entities used by all services.

### Gateway Routing
| Path Pattern | Target Service |
|---|---|
| `/auth/**`, `/idp/**` | Core API (identity) |
| `/api/tenant/**`, `/api/institutes/**` | Core API (identity) |
| `/api/students/**`, `/api/enrollments/**`, `/api/classes/**`, `/api/sections/**`, `/api/batches/**` | Core API (student) |
| `/payment/**`, `/api/payment/**` | Core API (payment) |
| `/content/**`, `/api/content/**`, `/api/courses/**` | Content (8082) |
| `/api/exams/**`, `/api/psych-test/**`, `/api/question-bank/**` | Content (8082) |

When adding a new endpoint, ensure the gateway `application.yml` has a route for it — requests won't reach the service otherwise.

### Service Startup Order
Identity → Student → Payment → Content → Core API → Gateway

Use `scripts/edudron.sh start` to launch all services in order, or start individually.

## Multi-Tenancy

### How It Works
Every request is scoped to a tenant via `clientId` (UUID). The flow:

1. **Frontend** stores tenant ID in localStorage (keys: `clientId`, `selectedTenantId`, `tenant_id`) and sends it as `X-Client-Id` header via the axios `ApiClient` interceptor.
2. **Gateway** `TenantContextFilter` reads `X-Client-Id` and stores it in the reactive context + MDC for logging.
3. **Service** `JwtAuthenticationFilter` reads `X-Client-Id` (or falls back to JWT `tenant` claim) and sets `TenantContext.setCurrentTenant(clientId)`.
4. **Service code** queries using `TenantContext.getCurrentTenant()` — all queries MUST filter by `clientId`.
5. **Finally block** in the filter clears `TenantContext` to prevent leaking between requests.

### Key Rules
- **Always filter by clientId** in repository queries. Never return data across tenants.
- **Always clear TenantContext** in a finally block. It's ThreadLocal — leaking it causes cross-tenant data exposure.
- **Inter-service calls** use `TenantContextRestTemplateInterceptor` (in `common/`) which automatically adds `X-Client-Id` to outgoing RestTemplate requests. Always use the injected `RestTemplate` bean, never create a new one.
- **System admin** uses special clientId values: `"SYSTEM"` or `"PENDING_TENANT_SELECTION"` — handle these as edge cases.

### Key Files
- `common/.../TenantContext.java` — ThreadLocal holder
- `common/.../TenantContextRestTemplateInterceptor.java` — header propagation for RestTemplate
- `gateway/.../filter/TenantContextFilter.java` — gateway-level extraction
- `identity/.../security/JwtAuthenticationFilter.java` — service-level extraction (each service has its own copy)

## Gateway & Middleware

### Filter Chain (ordered)
| Order | Filter | Purpose |
|---|---|---|
| `HIGHEST_PRECEDENCE` | `RequestIdFilter` | Generates/propagates `X-Request-Id` for distributed tracing |
| `-100` | `JwtAuthenticationFilter` | Logs JWT info (does NOT validate — downstream services validate) |
| `-99` | `TenantContextFilter` | Extracts `X-Client-Id` into context |
| `-98` | `HeaderForwardingFilter` | Ensures custom headers (e.g., `X-Sync-Timestamp`) reach downstream |
| `LOWEST_PRECEDENCE` | `RequestLoggingFilter` | Logs method, path, status, duration for every request |

### Adding a New Gateway Filter
1. Create a class implementing `WebFilter` in `gateway/.../filter/`
2. Annotate with `@Component` and `@Order(n)` — choose order relative to existing filters
3. Use the reactive `WebFilterChain` pattern (gateway is reactive, NOT servlet-based)
4. For logging, use MDC fields: `clientId`, `traceId`, `spanId`

### Gateway vs Service Filters
- **Gateway** uses reactive `WebFilter` (Spring WebFlux) — non-blocking
- **Services** use servlet `OncePerRequestFilter` — blocking, standard Spring MVC
- Do NOT mix these patterns. The gateway cannot use servlet filters.

## Security & Authentication

### JWT Setup
- **Library**: jjwt 0.11.5
- **Algorithm**: HS256 (HMAC SHA-256)
- **Access token**: 24h expiration (configurable via `jwt.expiration`)
- **Refresh token**: 7 days (configurable via `jwt.refresh-expiration`)
- **Secret**: Min 32 characters, loaded from env/Key Vault
- **Claims**: `sub` (username/email), `tenant` (clientId), `role` (user role)

### Auth Flow
```
Frontend login → POST /auth/login → Identity service validates credentials
  → Returns { token, refreshToken, user, tenantId }
  → Frontend stores in localStorage via TokenManager
  → All subsequent requests include Authorization: Bearer <token>
  → On 401/403, ApiClient automatically attempts token refresh
  → If refresh fails, redirects to /login
```

### User Roles
```java
SYSTEM_ADMIN, TENANT_ADMIN, CONTENT_MANAGER, INSTRUCTOR, STUDENT, SUPPORT_STAFF
```
The `User` entity has permission methods: `isSystemAdmin()`, `hasAdminPrivileges()`, `canManageContent()`, `canManageUsers()`, etc. Use these instead of checking role strings directly.

### Spring Security Config
- **Session**: Stateless (`SessionCreationPolicy.STATELESS`)
- **CSRF**: Disabled (JWT-based auth)
- **CORS**: Enabled (configurable origins in production via `cors.allowed-origins`)
- **Public endpoints**: `/auth/**`, `/api/webhooks/**`, `/actuator/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- **All other endpoints**: Authenticated

### Adding a New Public Endpoint
Add the path to `SecurityConfig.java` in the relevant service's `securityFilterChain` method under `.requestMatchers(...).permitAll()`.

## Backend Patterns & Conventions

### Controller Pattern
```java
@RestController
@RequestMapping("/api/resource")
public class ResourceController {
    private final ResourceService resourceService;
    // Constructor injection (preferred over @Autowired on fields)

    @GetMapping
    public ResponseEntity<Page<ResourceDTO>> list(Pageable pageable) { ... }

    @PostMapping
    public ResponseEntity<ResourceDTO> create(@Valid @RequestBody CreateResourceRequest request) { ... }
}
```
- Use `ResponseEntity<T>` for all responses
- Use `@Valid` on request bodies for validation
- Use `Pageable` for list endpoints (Spring Data pagination)
- DTOs for responses, request objects for inputs — never expose domain entities directly

### Service Pattern
- `@Service` with constructor injection
- `@Transactional` on write methods, `@Transactional(readOnly = true)` on read-only methods
- `@Async("executorName")` for background tasks — always specify the executor name
- Throw `IllegalArgumentException` for validation errors, `IllegalStateException` for business rule violations, `RuntimeException` for unexpected errors — the `GlobalExceptionHandler` catches these

### Repository Pattern
- Extend `JpaRepository<Entity, String>` (String because IDs are ULIDs)
- Add `JpaSpecificationExecutor<Entity>` when dynamic filtering is needed
- Name queries with Spring Data conventions: `findByClientIdAndActiveTrue(UUID clientId)`
- For complex queries, use JPA Criteria API via `Specification<Entity>` (see `AuditLogQueryService` for example)

### Exception Handling
Every service has a `GlobalExceptionHandler` (`@RestControllerAdvice`) that returns:
```json
{ "error": "message", "code": "ERROR_CODE", "status": 400 }
```
For validation errors, adds `"errors": [{ "field": "name", "message": "must not be blank" }]`.

When adding a new service, copy an existing `GlobalExceptionHandler` and adjust the package.

### DTO Conventions
- Use Java **records** for simple, immutable DTOs: `public record AuthResponse(String token, UserInfo user) {}`
- Use **classes** for DTOs that need builders or complex construction
- Keep DTOs in a `dto/` package within each service

## Database & Migrations

### Schema Layout
| Schema | Service | Purpose |
|---|---|---|
| `idp` | Identity | Users, tenants, branding, features, audit logs |
| `student` | Student | Classes, sections, enrollments, batches |
| `payment` | Payment | Payment records, Razorpay integration |
| `content` | Content | Courses, lectures, exams, media, AI jobs |
| `common` | All | Events, audit logs (shared tables) |

### Entity Pattern
```java
@Entity
@Table(name = "resources", schema = "content")
public class Resource {
    @Id
    private String id;  // ULID — use UlidGenerator.generate()

    @Column(nullable = false)
    private UUID clientId;  // Multi-tenancy — ALWAYS include

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;  // For JSON columns

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.generate();
    }
}
```
- **IDs**: ULID strings (time-sortable, random) via `UlidGenerator` in common module
- **Tenant column**: Every entity MUST have `clientId` (UUID)
- **JSON columns**: Use `@JdbcTypeCode(SqlTypes.JSON)` with PostgreSQL `jsonb`
- **Enums**: Use `@Enumerated(EnumType.STRING)` — never store as ordinals

### Liquibase Migrations
- Format: YAML changelogs in `src/main/resources/db/changelog/`
- Naming: `db.changelog-NNNN-description.yaml` (sequential numbering)
- Master file: `{schema}-master.yaml` includes all changelogs in order
- **Core API gotcha**: `LiquibaseConfig.java` manages multiple schemas (idp, student, payment) with separate `SpringLiquibase` beans and `@DependsOn` ordering
- Set `liquibase.duplicateFileMode=WARN` in application.yml when shared changelogs appear in multiple JARs

### Connection Pool
- HikariCP with 35 max connections, 20s connection timeout, 300s idle timeout
- Configured in each service's `application.yml`

## Common Module Utilities

The `common/` module provides shared code used by all services. Add code here only if it's genuinely needed by multiple services.

### TenantContext
ThreadLocal holder for current tenant. Set in filters, read in services, cleared after request. See [Multi-Tenancy](#multi-tenancy) section.

### UlidGenerator
```java
String id = UlidGenerator.generate(); // Time-sortable unique ID
```
Use for all new entity IDs. Prefer over UUID for sortability.

### EventService (Abstract)
Base class for structured event logging. Each service extends it with its own repository and service name.
```java
// Typed methods available:
eventService.logHttpRequest(method, path, status, duration, userId, email, traceId);
eventService.logUserAction(userId, email, action, eventData, traceId);
eventService.logLogin(userId, email, traceId);
eventService.logError(errorMessage, stackTrace, traceId);
eventService.logFileUpload(userId, email, fileName, fileSize, traceId);
// ... and more (video watch, assessment, search, lecture completion)
```
Events are stored in `common.events` table with comprehensive indexing.

### AuditService (Abstract)
CRUD audit trail. Each service extends it. Logs asynchronously to `common.audit_logs` table.
```java
auditService.logCreate("Course", courseId, adminEmail, metadata);
auditService.logUpdate("Course", courseId, adminEmail, metadata);
auditService.logDelete("Course", courseId, adminEmail, metadata);
```
JSON metadata is truncated at 32KB to prevent storage bloat.

### OpenApiConfig
Shared Swagger/OpenAPI configuration. Each service gets `/swagger-ui.html` and `/v3/api-docs` automatically.

## Frontend Architecture

### Monorepo Structure
```
frontend/
├── apps/
│   ├── admin-dashboard/    # Next.js 14 (port 3000)
│   └── student-portal/     # Next.js 14 (port 3001)
├── packages/
│   ├── shared-utils/       # @kunal-ak23/edudron-shared-utils (v1.0.29)
│   └── ui-components/      # @kunal-ak23/edudron-ui-components (v1.0.23)
├── package.json            # pnpm workspaces
├── pnpm-workspace.yaml
└── turbo.json              # Turbo build pipeline
```
- **Package manager**: pnpm
- **Build orchestration**: Turbo (`turbo run build` builds packages first, then apps)
- Both shared packages are published to GitHub npm registry — import via package name, NOT relative paths

### API Client (`ApiClient.ts` in shared-utils)
Central axios-based HTTP client used by all API classes.

**Key behaviors:**
- Automatically adds `Authorization: Bearer <token>` from localStorage
- Automatically adds `X-Client-Id` from localStorage (tenant context)
- On 401/403, attempts token refresh and retries the original request
- If refresh fails, clears tokens and redirects to `/login`
- Methods: `get()`, `post()`, `put()`, `patch()`, `delete()`, `postForm()`, `downloadFile()`

**API classes** (domain-specific, all in `shared-utils/src/api/`):
`CoursesApi`, `LecturesApi`, `EnrollmentsApi`, `StudentsApi`, `PaymentsApi`, `MediaApi`, `TenantsApi`, `ClassesApi`, `SectionsApi`, `InstitutesApi`, `AnalyticsApi`, `FeedbackApi`, `NotesApi`, `IssuesApi`, `TenantFeaturesApi`, `QuestionBankApi`

Each API class takes an `ApiClient` instance in its constructor and normalizes responses (handles Spring Data Page format, wrapped/unwrapped responses).

### Auth Flow (Frontend)
```
TokenManager (localStorage)  →  AuthService (login/logout/refresh)  →  AuthContext (React state)  →  useAuth() hook
```
- `TokenManager`: Raw get/set/clear for tokens in localStorage. SSR-safe.
- `AuthService`: Business logic — login, register, tenant selection, token refresh.
- `AuthContext`: React Context provider wrapping the app. Exposes `user`, `token`, `tenantId`, `isLoading`, `login()`, `logout()`, `selectTenant()`.
- `useAuth()`: Hook to consume AuthContext in components.

**Tenant selection flow**: After login, if user has multiple tenants, `needsTenantSelection` is true → show `TenantSelection` page → user picks tenant → `selectTenant()` updates all localStorage keys.

### State Management
- **React Query** (`@tanstack/react-query` v5) for server state — configured with `staleTime: 60s`, `refetchOnWindowFocus: false`
- **React Context** for auth state
- **localStorage** for persistence (tokens, tenant, font size preferences)
- No Redux, Zustand, or other global state library

### Route Protection
No Next.js middleware.ts files. Route protection is client-side via:
```typescript
// In page components:
const { user } = useRequireAuth({ allowedRoles: ['TENANT_ADMIN', 'CONTENT_MANAGER'] });
```
Redirects to `/login` if unauthenticated, `/unauthorized` if wrong role.

### Styling
- **Tailwind CSS** v3 with CSS variables (HSL color system)
- **shadcn/ui** + **Radix UI** primitives for components (in `src/components/ui/`)
- **Color palette**: Navy Blue primary (#1E3A5F), Tech Teal secondary (#0891B2), Orange accent (#F97316)
- **Font**: Manrope (weight 600) via `@next/font/google`
- **App-wide scaling**: `--app-scale` CSS variable on `<html>` (stored in localStorage)
- No CSS modules — Tailwind utility classes throughout

### Adding a New API Endpoint (Frontend)
1. Add method to the relevant API class in `shared-utils/src/api/`
2. Export any new types from the same file
3. Run `npm run build` in `shared-utils/`
4. Use in app via the API instance from `src/lib/api.ts`

## Async & Background Processing

### Thread Pool Configuration
Defined in `AsyncConfig.java` (content service has the most complex setup):
| Executor | Core | Max | Queue | Rejection Policy |
|---|---|---|---|---|
| `eventTaskExecutor` | 2 | 5 | 100 | Default (AbortPolicy) |
| `aiJobTaskExecutor` | 3 | 5 | 20 | CallerRunsPolicy |

### AI Job Queue Pattern
For long-running AI operations (course generation, image generation):
```
Client request → AIJobQueueService.enqueue(job) → stores in DB with PENDING status
                                                 → AIQueueProcessor polls for PENDING jobs
                                                 → AIJobWorker.process(job) runs async
                                                 → Updates job status: PROCESSING → COMPLETED/FAILED
Client polls → GET /api/content/ai-jobs/{id} → returns current status + result
```
- Jobs persist in DB (survive restarts)
- `CourseCopyWorker` follows the same pattern for course duplication
- Redis is available for distributed queue scenarios

## Infrastructure & Deployment

### Docker
- **Multi-stage builds**: Builder (`gradle:8.5-jdk21`) → Runtime (`eclipse-temurin:21-jre`)
- **Health checks**: `curl http://localhost:PORT/actuator/health`
- **Compose files**:
  - `docker-compose.yml` — PostgreSQL 16 (port 5433) + Redis 7 (port 6379)
  - `docker-compose.dev.yml` — Full stack (all services)
  - `docker-compose.db-only.yml` — Database only

### Azure Deployment
- **Hosting**: Azure Container Apps (autoscaling with min/max replicas)
- **Secrets**: Azure Key Vault (DB creds, JWT secret, API keys, storage keys)
- **Media storage**: Azure Blob Storage (images, videos, documents)
- **AI**: Azure OpenAI (GPT models for course generation)
- **Deploy scripts**: `azure/scripts/deploy-{service}.sh` — reads config from `azure/config/dev.env`, resolves version from `versions.json`, creates/updates container app

### Version Management
- Backend versions tracked in `versions.json` at project root
- Bump with `scripts/manage-versions.sh bump <service> <major|minor|patch>`
- Frontend package versions in their respective `package.json` files

### Environment Variables
- **Local dev**: `.env` file at project root, loaded via `dotenv-java` library
- **Frontend**: `.env.local` files in each app, `NEXT_PUBLIC_*` prefix for client-side vars
- **Production**: Azure Key Vault references in container app config
- **Key vars**: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `JWT_SECRET`, `AZURE_OPENAI_*`, `AZURE_STORAGE_*`

### Git Conventions
- **Commit style**: Conventional commits — `fix:`, `feat:`, `chore:`, `refactor:`
- **Branch naming**: `feat/description`, `fix/description`
- **Merge strategy**: Pull requests into `main`

## Shared Utils Package
- Published to GitHub npm registry: `@kunal-ak23/edudron-shared-utils`
- Built with **tsup** (`npm run build` in `frontend/packages/shared-utils/`)
- Current version: **1.0.29**
- All imports from shared-utils MUST use the npm package path, NOT relative paths
- CSS exports use subpath: `@kunal-ak23/edudron-shared-utils/tiptap/editor-styles.css`
- `tsconfig.json` uses `"moduleResolution": "bundler"` to support subpath exports (e.g., `@tiptap/react/menus`)
- After making changes to shared-utils, always run `npm run build` before testing

## TipTap Editor Setup

### Editor Components
| Component | Location | Purpose |
|-----------|----------|---------|
| `TipTapMarkdownEditor` | `admin-dashboard/src/components/` | Markdown editor (uses `tiptap-markdown`) |
| `RichTextEditor` | `admin-dashboard/src/components/` | HTML editor (no markdown conversion) |
| `TipTapContentViewer` | `student-portal/src/components/` | Read-only viewer for student portal |
| `MarkdownRenderer` | Both apps `/src/components/` | Read-only markdown rendering |

### TipTap Extensions (shared-utils)
All custom TipTap extensions live in `frontend/packages/shared-utils/src/tiptap/`:

1. **`ResizableImage.ts`** — Custom Image extension extending `@tiptap/extension-image`
   - Adds `alignment` attribute (`'left' | 'center' | 'right'`, default `'center'`)
   - Enables TipTap's built-in resize via `ResizableNodeView` (corner drag handles)
   - Uses `width: fit-content` on the wrapper div for alignment margins to work
   - `renderMarkdown`: images with width/alignment serialize as `<img>` HTML tags; plain images use `![](url)` syntax
   - Command: `setImageAlignment(alignment)` — updates alignment via `updateAttributes`

2. **`ImageBubbleMenu.tsx`** — Floating toolbar for image alignment
   - Uses `BubbleMenu` from `@tiptap/react/menus` (NOT from `@tiptap/react` directly)
   - TipTap v3.20 uses **Floating UI** (NOT Tippy.js) — use `options={{ placement: 'top' }}` not `tippyOptions`
   - Shows 3 alignment buttons + image width in px
   - Active alignment highlighted with blue

3. **`HighlightMark.ts`** — Custom mark for text highlighting (used in student portal notes)

4. **`editor-styles.css`** — Shared CSS for ProseMirror editors
   - Image selection outline, resize handle styling, alignment CSS

### Image Upload Pipeline
- Images uploaded to **Azure Blob Storage** via backend content service
- Upload endpoint: `POST /api/content/media/upload` (through gateway at port 8080)
- Media API in shared-utils: `MediaApi.uploadMedia(file, folder)`
- Folders defined in `MediaFolderConstants.java`: `COURSE_IMAGES`, `LECTURE_IMAGES`, `CONTENT_IMAGES`

### Key Gotchas
- **`BubbleMenu` import path**: Must use `@tiptap/react/menus`, not `@tiptap/react`
- **NodeView alignment**: Must set `width: fit-content` on the resize wrapper div, otherwise `margin-left/right: auto` has no visual effect on a full-width block
- **NodeView update**: The parent `ResizableNodeView.update()` may not re-render for custom attribute changes (like alignment). Must intercept `update` and apply styles manually
- **tiptap-markdown serialization**: Custom markdown serializers MUST be defined via `addStorage()` → `markdown.serialize(state, node)`. The `getMarkdownSpec()` function in `tiptap-markdown/src/util/extensions.js` reads from `extension.storage?.markdown`. A top-level `renderMarkdown` property does NOT work.
- **tiptap-markdown HTML attribute parsing**: When tiptap-markdown parses `<img>` HTML tags from markdown content, standard attributes (like `width`) survive because they're defined in the Image extension schema. Custom attributes (like `data-alignment`) also parse correctly IF the extension's `parseHTML` function is defined. However, inline `style` attributes may be sanitized. As a safeguard, the `alignment` attribute's `parseHTML` also checks inline styles for margin-based alignment inference.
- **CSS scoping for read-only viewers**: Selected node styles (e.g., blue outline on images) must be scoped to `[contenteditable="true"]` to avoid showing in read-only viewers like the student portal's `TipTapContentViewer`
- **Content sections in lectures**: Use `TipTapMarkdownEditor` (NOT `RichTextEditor`) because content is stored as markdown
- **Programmatic file input**: Browsers block setting file inputs via JS for security. Cannot automate file upload testing
- **MarkdownRenderer**: Both admin and student portal `MarkdownRenderer` components use `ResizableImage` (not plain `Image`) so they correctly parse alignment/width from `<img>` HTML tags in markdown content

## Page Routes
- Course edit: `/courses/[id]`
- Lecture/sub-lecture edit: `/courses/[id]/lectures/[lectureId]/edit?subLectureId=[id]`
- Student portal course: `localhost:3001/courses/[id]`

## Build & Test Commands
```bash
# Rebuild shared-utils after changes
cd frontend/packages/shared-utils && npm run build

# Admin dashboard dev server (port 3000)
cd frontend/apps/admin-dashboard && npm run dev

# Student portal dev server (port 3001)
cd frontend/apps/student-portal && npm run dev

# Backend — start all services
./scripts/edudron.sh start

# Backend — start individual services
cd gateway && ../gradlew bootRun
cd content && ../gradlew bootRun
cd core-api && ../gradlew bootRun

# Docker — database only
docker-compose -f docker-compose.db-only.yml up -d

# Docker — full dev stack
docker-compose -f docker-compose.dev.yml up -d

# Version management
./scripts/manage-versions.sh get content        # Check current version
./scripts/manage-versions.sh bump content patch  # Bump patch version

# Deploy to Azure
./azure/scripts/deploy-content.sh
./azure/scripts/deploy-core-api.sh
./azure/scripts/deploy-gateway.sh
```
