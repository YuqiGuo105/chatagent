# Writer Service

Spring Boot 3.2.5 / Java 21 microservice that owns the **canonical content write path** for Blogs, Life Blogs and Projects, and emits domain events via the **Transactional Outbox pattern**.

Replaces direct Supabase writes from the Next.js frontend with a proper API surface that:

- Validates input
- Enforces optimistic concurrency
- Supports idempotent retries
- Guarantees that every successful write produces exactly one outbox event in the same transaction

---

## Architecture in one picture

```
┌─────────────┐    HTTP (Bearer)     ┌────────────────┐   JDBC    ┌──────────────────┐
│  Admin UI   ├─────────────────────▶│ writer-service ├──────────▶│  Supabase (PG)   │
└─────────────┘                      │  port 8081     │           │  schema: writer  │
                                     └───────┬────────┘           └────────┬─────────┘
                                             │ Outbox poller              │ pg_notify
                                             │ (every 5s)                 │ ('writer_content_events')
                                             ▼                            ▼
                                     ┌─────────────────┐         ┌──────────────────┐
                                     │ pg_notify call  │────────▶│ Supabase Realtime│
                                     └─────────────────┘         │  /  LISTEN       │
                                                                 └────────┬─────────┘
                                                                          ▼
                                                              Indexer → OpenSearch → Search API
                                                              (separate services, not in this repo)
```

**Service boundary:** Writer Service = content writes + outbox events + pg_notify broadcast. It does **not** call Kafka, OpenSearch, or Search API. A separate Indexer Worker subscribes to the notify channel.

---

## Storage: Supabase Postgres

All four tables live in a dedicated **`writer`** schema inside your existing Supabase Postgres project. This avoids any collision with the existing `public."Blogs"`, `public.life_blogs`, `public."Projects"` tables that the Next.js reads from.

| Table | Purpose |
|---|---|
| `writer.blogs` | Long-form blog posts (`id UUID`) |
| `writer.life_blogs` | Life updates / personal posts (`id BIGSERIAL`) |
| `writer.projects` | Portfolio projects (`id UUID`) |
| `writer.outbox_events` | One row per domain event awaiting publication |

Flyway runs `V1__init.sql` on startup and creates the `writer` schema automatically (`spring.flyway.create-schemas=true`).

### Why not host Kafka on Supabase?

Supabase does not host Kafka. The writer-service ships with a **`pg_notify` publisher** as the default — it broadcasts events through Postgres `NOTIFY`, which Supabase Realtime exposes to browser / Edge Function subscribers, and which any JDBC client can subscribe to via `LISTEN`. No external broker required.

If you later want a real message bus, three options:

1. **Stick with `pg_notify` + Supabase Realtime** — zero infra, perfect for low-throughput admin events. (current default)
2. **Railway Kafka** — Bitnami template. A future `KafkaContentEventPublisher` plugs in by setting `writer.publisher.type=kafka`.
3. **Upstash / Confluent Cloud** — managed Kafka SaaS.

Swap publishers via the `writer.publisher.type` property: `pg-notify` (default) | `noop`.

---

## API endpoints

All write endpoints require `Authorization: Bearer <WRITER_ADMIN_TOKEN>` (dev placeholder — see Security). All write endpoints accept an optional `Idempotency-Key` header.

| Method | Path | Headers | Description |
|---|---|---|---|
| `POST`   | `/api/admin/blogs`            | `Idempotency-Key?`     | Create blog |
| `GET`    | `/api/admin/blogs`            |                        | Paged list |
| `GET`    | `/api/admin/blogs/{id}`       |                        | Fetch by id |
| `PUT`    | `/api/admin/blogs/{id}`       | `X-Expected-Version`   | Update (optimistic lock) |
| `DELETE` | `/api/admin/blogs/{id}`       | `X-Expected-Version`   | Soft-delete (sets status=DELETED) |
| `POST`   | `/api/admin/life-blogs`       | `Idempotency-Key?`     | Create life blog |
| `GET`    | `/api/admin/life-blogs`       |                        | Paged list |
| `GET`    | `/api/admin/life-blogs/{id}`  |                        | Fetch by id |
| `PUT`    | `/api/admin/life-blogs/{id}`  | `X-Expected-Version`   | Update |
| `DELETE` | `/api/admin/life-blogs/{id}`  | `X-Expected-Version`   | Soft-delete |
| `POST`   | `/api/admin/projects`         | `Idempotency-Key?`     | Create project |
| `GET`    | `/api/admin/projects`         |                        | Paged list |
| `GET`    | `/api/admin/projects/{id}`    |                        | Fetch by id |
| `PUT`    | `/api/admin/projects/{id}`    | `X-Expected-Version`   | Update |
| `DELETE` | `/api/admin/projects/{id}`    | `X-Expected-Version`   | Soft-delete |
| `GET`    | `/actuator/health`            | (public)               | Liveness probe |

---

## Outbox pattern

```
@Transactional
create() {
   blogRepository.save(blog);              // INSERT writer.blogs ...
   outboxService.record(...);              // INSERT writer.outbox_events (status=NEW)
}                                           // commits as ONE transaction
```

Either both rows commit or neither does — so the outbox can never drift from the content store. A scheduled poller (every 5s) reads `status=NEW` rows, hands them to the `ContentEventPublisher`, and on success flips them to `PUBLISHED`. Failures retry up to 5 times before moving to `FAILED`.

### Default publisher: Postgres `pg_notify`

`PgNotifyContentEventPublisher` issues `SELECT pg_notify('writer_content_events', <json>)` on the same Supabase Postgres connection. The notification payload is intentionally **small** (no blog body) because `pg_notify` is capped at  8 KB:

```json
{
  "outbox_id":     "f3e2...",
  "event_type":    "content.created",
  "aggregate_type":"BLOG",
  "aggregate_id":  "a1b2...",
  "event_version": 1
}
```

Consumers receive the pointer, then `SELECT * FROM writer.outbox_events WHERE id = ?` for the full payload.

**Subscribing from Supabase Realtime (Node example):**

```js
const { createClient } = require('@supabase/supabase-js');
const supabase = createClient(URL, ANON_KEY);
supabase
  .channel('writer_content_events')
  .on('broadcast', { event: '*' }, (msg) => console.log(msg.payload))
  .subscribe();
```

**Subscribing from a JDBC client (any worker):**

```sql
LISTEN writer_content_events;
```

Then poll `PGConnection.getNotifications()` from the Postgres JDBC driver.

---

## Local run (against Supabase)

1. **Get your Supabase JDBC URL.** In the Supabase dashboard → Project Settings → Database → Connection string → **JDBC**. Use the **Session pooler** entry (port `5432`). It looks like:
   ```
   jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require
   ```
   The username is `postgres.<your-project-ref>`.

2. **Create your `.env`** (do not commit it):
   ```bash
   cd Portfolio/writer-service
   cp .env.example .env
   # edit .env with your real Supabase values
   ```

3. **Run the service** (Flyway will auto-create the `writer` schema + tables on first boot):
   ```bash
   set -a; source .env; set +a
   mvn spring-boot:run
   ```

4. **Smoke test:**
   ```bash
   curl -s -X POST http://localhost:8081/api/admin/blogs \
     -H "Authorization: Bearer $WRITER_ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     -H "Idempotency-Key: smoke-1" \
     -d '{"title":"Hello","slug":"hello-world","description":"first post"}'
   ```

### Docker

```bash
cp .env.example .env   # fill in Supabase values
mvn -DskipTests package
docker compose --env-file .env up --build
```

(The compose file no longer ships a local Postgres — everything talks to Supabase.)

---

## Required environment variables

| Var | Required | Description |
|---|---|---|
| `WRITER_DB_URL`      | yes | Supabase JDBC URL, with `?sslmode=require` |
| `WRITER_DB_USERNAME` | yes | `postgres.<project-ref>` |
| `WRITER_DB_PASSWORD` | yes | Supabase database password |
| `WRITER_ADMIN_TOKEN` | yes | Bearer token required for `/api/admin/**` |
| `WRITER_PUBLISHER_TYPE` | no | `pg-notify` (default) or `noop` |
| `WRITER_PUBLISHER_CHANNEL` | no | Notify channel name (default `writer_content_events`) |
| `OUTBOX_POLL_INTERVAL_MS` | no | Poller cadence in ms (default `5000`) |
| `PORT`               | no  | Server port (default `8081`) |

---

## Security

The current `AdminTokenFilter` is a **dev placeholder** — a single shared bearer token. In production you should:

1. Issue your admins a **Supabase Auth** session (the Next.js admin UI signs in with `supabase-js`).
2. Replace `AdminTokenFilter` with a JWT validator that:
   - Verifies the JWT against the Supabase project's JWKS (`https://<project>.supabase.co/auth/v1/.well-known/jwks.json`)
   - Extracts the `email` claim
   - Allows only emails in a configurable admin allow-list (`writer.admin.allowed-emails`)

Per project requirements:
- **Editing requires login (Supabase Auth).**
- **Only the admin allow-list can edit.**

A follow-up ticket will implement `SupabaseJwtAdminFilter`.

---

## Testing

```bash
mvn test
```

- `BlogServiceTest` — `@SpringBootTest` + Testcontainers Postgres, verifies content + outbox rows are committed together, version conflicts, idempotency.
- `BlogControllerTest` — standalone MockMvc, verifies auth (401/403), validation (400), version conflicts (409).

Docker is required for the integration tests.

---

## Future steps (not in scope)

1. (Optional) Implement `KafkaContentEventPublisher` (Railway Kafka) and switch via `writer.publisher.type=kafka` — only needed if `pg_notify` throughput is insufficient.
2. Deploy an **Indexer Worker** that subscribes to `writer_content_events` (via Supabase Realtime or `LISTEN`) and writes to OpenSearch.
3. Deploy a **Search API** (`GET /api/search`) backed by OpenSearch.
4. Update `pages/api/search.js` and `app/api/search/route.ts` to proxy to Search API.
5. Replace `AdminTokenFilter` with **Supabase Auth JWT** validation + admin email allow-list.
6. One-time **backfill** of existing `public."Blogs"`, `public.life_blogs`, `public."Projects"` into `writer.*`.
7. Add `portfolio_content_documents` PGVector table for RAG.
