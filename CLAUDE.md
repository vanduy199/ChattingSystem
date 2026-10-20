# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Spring Boot 4.1 / Java 25, Maven (use the `./mvnw` wrapper), PostgreSQL + Flyway, Spring Security as an OAuth2 resource server (self-issued HS256 JWTs), Firebase Admin SDK, AWS SDK v2 S3 client (for Cloudflare R2), Lombok. Jackson is v3 (`tools.jackson.*`; annotations still `com.fasterxml.jackson.annotation`). WebSocket starter is on the classpath but no chat/WebSocket code exists yet. User-facing messages (validation messages, `ErrorCode` texts, `ApiResponse` messages) are written in Vietnamese.

## Commands

```bash
./mvnw spring-boot:run                     # run the app (http://localhost:8080/api/v1)
./mvnw clean package                       # build jar (runs tests)
./mvnw clean package -DskipTests           # build without tests
./mvnw test                                # all tests
./mvnw test -Dtest=ChattingSystemApplicationTests              # single class
./mvnw test -Dtest=ChattingSystemApplicationTests#contextLoads # single method
./mvnw test -Dtest='SlugUtilsTest,ReadingTimeCalculatorTest,HtmlSanitizerTest,ImageTypeTest' # unit tests, no DB needed
docker build -t chattingsystem .           # production image (Dockerfile used by Railway)
```

There is no linter/formatter configured.

## Configuration

- `application.yaml` imports `./.env` (see `.env.example`) via `spring.config.import`, so env vars must be present in `.env` or the shell before running **or testing** — the only test is a full `@SpringBootTest`, which needs a reachable DB and valid Firebase credentials.
- The datasource URL hardcodes `sslmode=require` (targets a hosted Postgres). `compose.yaml` defines a local Postgres, but Spring's docker-compose integration is disabled (`spring.docker.compose.enabled: false`), and the local container won't accept SSL without changing the URL.
- Firebase credentials are read from `FIREBASE_SERVICE_ACCOUNT` as the **raw service-account JSON string** (`FirebaseConfig` parses it from bytes).
- `server.port` is `${PORT:8080}` (Railway injects `PORT`).
- CORS origins: `CORS_ALLOWED_ORIGINS` (comma separated) → `cors.allowed-origins` (`CorsProperties`), used by `config/CorsConfig`.
- Image storage (Cloudflare R2 via S3 API): `STORAGE_ENDPOINT`, `STORAGE_REGION` (default `auto`), `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_PUBLIC_BASE_URL` → `StorageProperties` (prefix `storage`). The `S3Client` bean only exists when `STORAGE_ENDPOINT` is non-blank; otherwise uploads return 503 `STORAGE_NOT_CONFIGURED` but the app still starts. Multipart limit is 5 MB per file.
- Redis: `REDIS_URL` → `spring.data.redis.url` (default `redis://localhost:6379`), used by `shared.redis.RedisService`.
- Railway: `railway.toml` builds from the `Dockerfile` and health-checks `GET /api/v1/tags`. Set every var from `.env.example` on the service; `REDIS_URL` can reference a Railway Redis service (`${{Redis.REDIS_URL}}`).
- `JWT_SECRET_KEY` is used directly as the HMAC-SHA256 key, so it must be ≥ 32 bytes. Expirations are `Duration` strings (`15m`, `30d`). `refreshExpiration` is defined but refresh tokens aren't implemented yet (`HashToken` is a prepared helper for storing hashed tokens).
- Typed config lives in `shared/properties` as `@ConfigurationProperties` records, picked up by `@ConfigurationPropertiesScan` on the main class.

## Architecture

Package-by-feature under `com.ChattingSystem`: `auth`, `user`, `security`, `config`, and cross-cutting `shared` (note the package is spelled `shared.exeption` — keep using that name).

**Auth flow:** the client signs in with Firebase, then `POST /api/v1/auth/login` with `{ idToken }`. `AuthServiceImpl` verifies the token via `FirebaseAuth`, finds the user by `firebaseUid` (or creates one; rejects with `EMAIL_ALREADY_EXISTS` if the email belongs to another uid), and returns an app JWT from `JwtService` (`sub` = firebase uid, claims `userId`, `scope` = `"USER"` or `"USER ADMIN"` from `users.role`). The role is stamped at login, so a promoted user must log in again to get the admin scope. `GET /auth/me` returns `MeResponse` with the role read from the DB. All other endpoints authenticate with that JWT as a Bearer token via Spring's resource-server support configured in `security/config/JwtConfig` + `SecurityConfig` (stateless, CSRF off, `scope` claim → `SCOPE_` authorities).

- `SecurityConfig` matchers are relative to the servlet context path `/api/v1`. Public endpoints must be added to its `permitAll()` list (currently `POST /auth/login`, `GET /posts`, `/posts/**`, `/tags`, `POST /waitlist`, `/error`); keep that list in sync when renaming controller routes. `/admin/**` requires `SCOPE_ADMIN`.
- Security-layer 401/403 are written as `ErrorResponse` JSON by `security/handler` (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`).
- Roles: `user.entity.Role` (`USER`/`ADMIN`, column `users.role`). Admins are promoted manually: `UPDATE users SET role = 'ADMIN' WHERE email = '...';`

**Features:**
- `post` — blog posts + tags. Public `PostController` (`/posts`, `/posts/slugs`, `/posts/{slug}`) and `TagController` (`/tags`) expose PUBLISHED posts only; `AdminPostController` (`/admin/posts`) does CRUD for all statuses. Slugs come from `shared.util.SlugUtils` (strips Vietnamese diacritics, `đ→d`); a blank slug is generated from the title on create (auto-suffixed `-2`, `-3`… if taken) and kept unchanged on update; an explicit taken slug → 409 `POST_SLUG_EXISTS`. Post bodies are HTML (`contentHtml`, column `content_html` since V3) from the frontend's Tiptap editor: `post.util.HtmlSanitizer` (jsoup) cleans every create/update against the contract's allow-list (h1→h2, a/img protocol rules incl. relative `/` and `#` hrefs, `language-*` classes only, `rel="noopener noreferrer"` on `target=_blank`) and the sanitized HTML is what's stored; a body with no text and no image afterwards → 400 `VALIDATION_ERROR` on `contentHtml` (`AppException.fieldError`). `readingMinutes` = `ceil(words/200)` of the plain text, min 1 (`post.util.ReadingTimeCalculator`). Tags are upserted by slug. `publishedAt` is set on first publish only. List queries fetch-join the author and batch-load tags (`@BatchSize`) — don't add collection fetch joins to paged queries.
- `waitlist` — `POST /waitlist` (public, idempotent: duplicate email is a silent success) and `GET /admin/waitlist`.
- `storage` — `POST /admin/uploads/images` (multipart `file`); type is detected from magic bytes (`storage.util.ImageType`: jpeg/png/webp/avif/gif), ≤ 5 MB, stored at `blog/{yyyy}/{MM}/{uuid}.{ext}`, URL = `STORAGE_PUBLIC_BASE_URL + "/" + key`.
- The API contract shared with the Next.js frontend defines the paths/field names above — don't rename them unilaterally.

**Conventions:**
- Constructor injection written out by hand (not `@RequiredArgsConstructor`); services are an interface in `service/` with the implementation in `service/impl/`.
- DTOs are Java records under `dto/request` and `dto/response`, validated with Jakarta Validation (`@Valid`).
- Success responses wrap data in `shared.response.ApiResponse` (`ApiResponse.success(message, data)`); paginated data uses `PageResponse.of(page)`.
- Errors: throw `AppException(ErrorCode.X)` (or `AppException.fieldError(field, msg)` for a service-level 400 with `errors[]`); add new cases to the `ErrorCode` enum (HTTP status + default message). `GlobalExceptionHandler` turns these and validation exceptions into `ErrorResponse` (`statusCode`, `message`, `path`, `timestamp`, `errors[]`).
- Entities extend `shared.entity.BaseEntity` (identity `id`, JPA-audited `createdAt`/`updatedAt` as `Instant`). Entities have a protected no-arg constructor, no setters, a static `create(...)` factory and intent-named mutators (see `User`).

**Database:** schema is owned by Flyway (`src/main/resources/db/migration/V{n}__*.sql`); Hibernate runs with `ddl-auto: validate`, so every entity change needs a matching new migration. Never edit an applied migration. `open-in-view` is off — do lazy loading inside `@Transactional` service methods.
