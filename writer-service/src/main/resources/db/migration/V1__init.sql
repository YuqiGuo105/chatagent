-- All writer-service tables live in a dedicated `writer` schema so they do not
-- collide with the existing Supabase tables in `public` (e.g. public.life_blogs,
-- public."Blogs", public."Projects") that the Next.js frontend reads from.
CREATE SCHEMA IF NOT EXISTS writer;

-- blogs
CREATE TABLE IF NOT EXISTS writer.blogs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500) NOT NULL,
    slug            VARCHAR(500) NOT NULL UNIQUE,
    description     TEXT,
    content         TEXT,
    tags            TEXT,
    category        VARCHAR(255),
    image_url       TEXT,
    url             TEXT,
    date            VARCHAR(50),           -- preserved from Supabase "Blogs".date
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    visibility      VARCHAR(50) NOT NULL DEFAULT 'PUBLIC',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      TEXT,
    updated_by      TEXT,
    version         BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255) UNIQUE
);

-- life_blogs
CREATE TABLE IF NOT EXISTS writer.life_blogs (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(500) NOT NULL UNIQUE,
    description     TEXT,
    content         TEXT,
    tags            VARCHAR(255),
    category        VARCHAR(255),
    image_url       VARCHAR(255),
    url             TEXT,
    require_login   BOOLEAN NOT NULL DEFAULT false,
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    visibility      VARCHAR(50) NOT NULL DEFAULT 'PUBLIC',
    published_at    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      TEXT,
    updated_by      TEXT,
    version         BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255) UNIQUE
);

-- projects
CREATE TABLE IF NOT EXISTS writer.projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500) NOT NULL,
    slug            VARCHAR(500) NOT NULL UNIQUE,
    description     TEXT,
    content         TEXT,
    tags            TEXT,
    category        VARCHAR(255),
    image_url       TEXT,
    url             TEXT,
    technology      TEXT,
    year            VARCHAR(10),
    num             INTEGER,
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    visibility      VARCHAR(50) NOT NULL DEFAULT 'PUBLIC',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      TEXT,
    updated_by      TEXT,
    version         BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(255) UNIQUE
);

-- outbox_events
CREATE TABLE IF NOT EXISTS writer.outbox_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type   VARCHAR(50) NOT NULL,   -- BLOG | LIFE_BLOG | PROJECT
    aggregate_id     TEXT NOT NULL,
    event_type       VARCHAR(100) NOT NULL,  -- content.created | content.updated | content.deleted
    event_version    INTEGER NOT NULL DEFAULT 1,
    payload          JSONB NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'NEW',  -- NEW | PUBLISHED | FAILED
    retry_count      INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ,
    last_error       TEXT,
    idempotency_key  VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS outbox_events_status_idx ON writer.outbox_events (status, created_at)
    WHERE status = 'NEW';
