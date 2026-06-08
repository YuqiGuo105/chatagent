# Deploy writer-service to Render — Step by Step

Everything is already in place in this folder. Follow these steps top-to-bottom.

---

## 0. What you have right now

| Item | Where | Status |
|---|---|---|
| Supabase project | `iyvhmpdfrnznxgyvvkvx` (us-west-1) | already exists |
| `.env` (local dev) | `writer-service/.env` | created, gitignored |
| Admin token | inside `writer-service/.env` | already generated for you |
| Multi-stage Dockerfile | `writer-service/Dockerfile` | ready |
| Render blueprint | `writer-service/render.yaml` | ready |
| CI workflow | `.github/workflows/writer-service-ci.yml` | ready |
| GitHub repo | `github.com/YuqiGuo105/Portfolio` | already connected |

**The only secret you still need:** your Supabase database password.

---

## 1. Get your Supabase database password (2 min)

1. Go to <https://supabase.com/dashboard/project/iyvhmpdfrnznxgyvvkvx/settings/database>
2. Look for **Connection string → URI** or scroll to **Database password**.
3. If you don't remember the password, click **Reset database password** and copy the new one.
4. Open `writer-service/.env` in this workspace and replace `PASTE_YOUR_SUPABASE_DB_PASSWORD_HERE` with the real password.
5. **Do not commit `.env`** — it's already in `.gitignore`.

> While you're in that Supabase settings page, also click **Connection string → JDBC** and verify the host. The current `.env` assumes `aws-0-us-west-1.pooler.supabase.com`. If yours is different (e.g. a different AWS region), fix the host portion of `WRITER_DB_URL`.

---

## 2. Smoke test locally (3 min)

```bash
cd writer-service
set -a; source .env; set +a
mvn spring-boot:run
```

You should see (about 10–20s in):

```
HikariPool-1 - Start completed.
Successfully validated 1 migration ...     ← Flyway picked up V1__init.sql
o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8081
Started WriterServiceApplication in X seconds
```

Verify the schema was created (in another terminal):

```bash
# Hit health
curl -s http://localhost:8081/actuator/health

# Create a blog
curl -s -X POST http://localhost:8081/api/admin/blogs \
  -H "Authorization: Bearer $WRITER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: smoke-1" \
  -d '{"title":"Hello","slug":"hello-world","description":"first post"}'

# List blogs
curl -s -H "Authorization: Bearer $WRITER_ADMIN_TOKEN" \
  http://localhost:8081/api/admin/blogs | jq
```

Then check Supabase: dashboard → **Table Editor** → schema dropdown → `writer` → you should see `blogs`, `life_blogs`, `projects`, `outbox_events`.

Kill the server with `Ctrl+C`.

> **If Flyway fails with `permission denied for database postgres`** — the Supabase database user has only enough rights to create schemas it owns. You should be fine because Flyway runs as the user you provided and `create-schemas: true` will create the `writer` schema owned by that user. If you do hit this, run the contents of `src/main/resources/db/migration/V1__init.sql` once in Supabase's SQL Editor manually, then change `spring.flyway.enabled` to `false` in `application.yaml` for the next boot.

---

## 3. Sign up for Render (5 min)

1. Go to <https://render.com> → **Get Started**.
2. Choose **Sign up with GitHub** (recommended — auto-links your repos).
3. Authorize Render to read your `YuqiGuo105/Portfolio` repo (you can scope it to that repo only).
4. Confirm your email if prompted.

---

## 4. Commit and push everything

```bash
cd /Users/yuqiguo/Documents/GitHub/unified-workspace/Portfolio
git status            # should show writer-service/ + .github/workflows/writer-service-ci.yml as new
git add writer-service .github/workflows/writer-service-ci.yml
git commit -m "feat: add writer-service (Spring Boot 3, Supabase, pg_notify outbox)"
git push origin main
```

Sanity check: confirm `writer-service/.env` is **not** in `git status` (it must stay local).

---

## 5. Create the Render service from the blueprint (3 min)

1. Render dashboard → **New + → Blueprint**.
2. Pick the `YuqiGuo105/Portfolio` repo.
3. Render auto-detects `writer-service/render.yaml`. Click **Apply**.
4. Render shows: "1 service will be created: `writer-service` (web, docker, free)". Click **Apply** again.
5. The service starts building. First build = ~5–8 min (Maven dependency download).

> Render's free tier builds inside Docker, so it doesn't matter that you don't have a JAR pre-built locally — the multi-stage Dockerfile builds it for you.

---

## 6. Set the secret env vars in Render (2 min)

The blueprint marked these `sync: false` (Render won't read them from yaml). Set them now:

1. Render dashboard → **writer-service** → **Environment** tab.
2. Click **Add Environment Variable** and add **three** vars:

| Key | Value |
|---|---|
| `WRITER_DB_URL`      | `jdbc:postgresql://aws-0-us-west-1.pooler.supabase.com:5432/postgres?sslmode=require` |
| `WRITER_DB_USERNAME` | `postgres.iyvhmpdfrnznxgyvvkvx` |
| `WRITER_DB_PASSWORD` | (your Supabase DB password) |

3. The blueprint auto-generated `WRITER_ADMIN_TOKEN` for you — **copy it now** (you'll need it to call the API). It's in Environment tab too.
4. Click **Save Changes**. Render redeploys.

---

## 7. Verify the deploy

Render will give you a URL like `https://writer-service.onrender.com`.

```bash
# 1. Health (no auth)
curl -s https://writer-service.onrender.com/actuator/health

# 2. Create a blog (use the WRITER_ADMIN_TOKEN from Render dashboard)
ADMIN_TOKEN=<paste from render dashboard>
curl -s -X POST https://writer-service.onrender.com/api/admin/blogs \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: prod-smoke-1" \
  -d '{"title":"Hello from Render","slug":"hello-render","description":"first prod post"}'
```

Expected: `{"id":"…", "title":"Hello from Render", … "status":"DRAFT", "version":0}` with HTTP 201.

Then in Supabase Table Editor → `writer.blogs` you should see the row, and `writer.outbox_events` should have a `content.created` event flipping from `NEW → PUBLISHED` within 5 seconds (the poller interval).

---

## 8. Lock down the deploy (optional but recommended)

- **Custom domain**: Render → writer-service → Settings → Custom Domain.
- **Disable autoDeploy** if you'd rather use the deploy-hook from GH Actions:
  1. Render → writer-service → Settings → toggle **Auto-Deploy** off.
  2. Render → Settings → **Deploy Hook** → copy URL.
  3. GitHub → repo → Settings → Secrets → add `RENDER_DEPLOY_HOOK_URL` = (paste).
  4. The GH Action will POST to it after tests pass.

---

## 9. Subscribe to events (next milestone)

The writer-service is already broadcasting `pg_notify('writer_content_events', …)` for every successful write. You have two ways to subscribe:

### Option A: Supabase Realtime from your Next.js admin UI
```js
import { createClient } from '@supabase/supabase-js';
const supabase = createClient(NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_ANON_KEY);

supabase
  .channel('writer_content_events')
  .on('broadcast', { event: '*' }, msg => console.log('content changed:', msg.payload))
  .subscribe();
```

> Note: Supabase Realtime needs to be told to forward this channel. Dashboard → Database → Replication → enable Realtime for the `writer` schema. (Or use `realtime.broadcast_changes`.)

### Option B: A standalone Indexer Worker
A separate Spring Boot or Node service that opens a JDBC `LISTEN writer_content_events;` connection, fetches the full row from `writer.outbox_events` on each notification, and writes to OpenSearch / Algolia / wherever search lives. That's the next ticket.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `FATAL: Tenant or user not found` | `WRITER_DB_USERNAME` must be `postgres.<project-ref>` (with the dot), not just `postgres`. |
| `connection refused` or `unknown host` | Wrong region in `WRITER_DB_URL` — check the JDBC URL on the Supabase dashboard. |
| `password authentication failed` | Reset the Supabase DB password and update Render env var. |
| Render build fails on Maven | First build is slow (~5 min). If it actually fails, check the build log; usually a typo in `pom.xml`. |
| `404 Not Found` on POST `/api/admin/blogs` from a browser | Render free tier spins down after 15min — first request takes 30s to wake up, then works. |
| `401` even with the right token | Make sure header is `Authorization: Bearer <token>`, not just `<token>`. |
| Outbox events stuck in `NEW` | The poller fires every 5s. If you see retry_count growing, check the writer-service logs — `pg_notify` may be failing if you don't have INSERT permission. |

---

## Files added by this milestone

```
writer-service/
├── .env                  ← LOCAL ONLY (gitignored, has your secrets)
├── .env.example          ← committed template
├── .gitignore
├── Dockerfile            ← multi-stage build
├── docker-compose.yml    ← optional, for local non-Supabase dev
├── pom.xml
├── README.md
├── render.yaml           ← Render Blueprint
├── DEPLOY.md             ← this file
└── src/...
.github/workflows/
└── writer-service-ci.yml ← runs tests on every push touching writer-service/
```
