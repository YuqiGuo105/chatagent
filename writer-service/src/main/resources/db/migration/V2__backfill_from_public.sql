-- V2__backfill_from_public.sql
-- ============================================================
-- One-time backfill: copies existing public-schema content into
-- the writer.* tables so it appears in GET /api/search results.
--
-- • Safe to run multiple times — uses ON CONFLICT (idempotency_key) DO NOTHING
-- • Legacy rows land with status = PUBLISHED, visibility = PUBLIC
--   (they were already public on the site)
-- • URL is set to the canonical frontend routes used by the Portfolio pages
--   (e.g. /blog-single/{id}, /life-blog/{id})
-- • Slugs are generated from title + ID suffix to guarantee uniqueness
-- ============================================================

-- ── 0. Helper function: title → URL slug ─────────────────────────────────
CREATE OR REPLACE FUNCTION writer.slugify(input TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT TRIM(BOTH '-' FROM
        regexp_replace(
            lower(COALESCE(input, '')),
            '[^a-z0-9]+', '-', 'g'
        )
    );
$$;

-- ── 1. GIN indexes for fast websearch_to_tsquery FTS ─────────────────────
-- These dramatically speed up ContentSearchService on large datasets.

CREATE INDEX IF NOT EXISTS writer_blogs_fts_idx
    ON writer.blogs
    USING GIN (
        to_tsvector('english',
            COALESCE(title, '') || ' ' ||
            COALESCE(description, '') || ' ' ||
            COALESCE(tags, ''))
    );

CREATE INDEX IF NOT EXISTS writer_life_blogs_fts_idx
    ON writer.life_blogs
    USING GIN (
        to_tsvector('english',
            COALESCE(title, '') || ' ' ||
            COALESCE(description, '') || ' ' ||
            COALESCE(tags, ''))
    );

CREATE INDEX IF NOT EXISTS writer_projects_fts_idx
    ON writer.projects
    USING GIN (
        to_tsvector('english',
            COALESCE(title, '') || ' ' ||
            COALESCE(description, '') || ' ' ||
            COALESCE(tags, ''))
    );

-- ── 2. Backfill public."Blogs" → writer.blogs ────────────────────────────
-- Legacy schema: id (UUID), title, description, content, tags, category,
--               image_url, date
-- No slug, url, status, visibility, published_at in legacy.
-- URL pattern used by Portfolio frontend: /blog-single/{id}
INSERT INTO writer.blogs (
    id,
    title,
    slug,
    description,
    content,
    tags,
    category,
    image_url,
    url,
    date,
    status,
    visibility,
    published_at,
    created_at,
    updated_at,
    version,
    idempotency_key
)
SELECT
    b.id,
    COALESCE(NULLIF(TRIM(b.title), ''), 'untitled'),
    -- slug = slugify(title) + '-' + first 8 chars of UUID for guaranteed uniqueness
    writer.slugify(COALESCE(NULLIF(TRIM(b.title), ''), 'untitled'))
        || '-' || SUBSTR(b.id::TEXT, 1, 8),
    b.description,
    b.content,
    b.tags,
    b.category,
    b.image_url,
    '/blog-single/' || b.id::TEXT,  -- existing Portfolio route
    b.date,
    'PUBLISHED',
    'PUBLIC',
    NULL,  -- no published_at in legacy schema
    NOW(),
    NOW(),
    0,
    'legacy-blog-' || b.id::TEXT
FROM public."Blogs" b
ON CONFLICT (idempotency_key) DO NOTHING;

-- ── 3. Backfill public.life_blogs → writer.life_blogs ────────────────────
-- Legacy schema: id (serial), title, image_url, category, published_at,
--               description, require_login, created_at, updated_at, tags, content
-- URL pattern used by Portfolio frontend: /life-blog/{id}
INSERT INTO writer.life_blogs (
    title,
    slug,
    description,
    content,
    tags,
    category,
    image_url,
    url,
    require_login,
    status,
    visibility,
    published_at,
    created_at,
    updated_at,
    version,
    idempotency_key
)
SELECT
    COALESCE(NULLIF(TRIM(lb.title), ''), 'untitled'),
    writer.slugify(COALESCE(NULLIF(TRIM(lb.title), ''), 'untitled'))
        || '-lb-' || lb.id::TEXT,
    lb.description,
    lb.content,
    lb.tags,
    lb.category,
    lb.image_url,
    '/life-blog/' || lb.id::TEXT,  -- existing Portfolio route
    COALESCE(lb.require_login, FALSE),
    'PUBLISHED',
    -- honour the require_login gate as visibility
    CASE WHEN COALESCE(lb.require_login, FALSE) THEN 'LOGIN_REQUIRED' ELSE 'PUBLIC' END,
    lb.published_at,
    COALESCE(lb.created_at, NOW()),
    COALESCE(lb.updated_at, NOW()),
    0,
    'legacy-lb-' || lb.id::TEXT
FROM public.life_blogs lb
ON CONFLICT (idempotency_key) DO NOTHING;

-- ── 4. Backfill public."Projects" → writer.projects ──────────────────────
-- Legacy schema: id (UUID), title, content, published_at, updated_at,
--               image_url, "URL", category, year, technology, num
-- No slug, description, tags, status, visibility in legacy.
-- Projects on Portfolio use /work-single (no per-item deep-link), so
-- url is set to the top-level works page.
INSERT INTO writer.projects (
    id,
    title,
    slug,
    description,
    content,
    tags,
    category,
    image_url,
    url,
    technology,
    year,
    num,
    status,
    visibility,
    published_at,
    created_at,
    updated_at,
    version,
    idempotency_key
)
SELECT
    p.id,
    COALESCE(NULLIF(TRIM(p.title), ''), 'untitled'),
    writer.slugify(COALESCE(NULLIF(TRIM(p.title), ''), 'untitled'))
        || '-' || SUBSTR(p.id::TEXT, 1, 8),
    NULL,           -- no description in legacy
    p.content,
    NULL,           -- no tags in legacy
    p.category,
    p.image_url,
    COALESCE(p."URL", '/works'),  -- fallback to works listing
    p.technology,
    p.year,
    p.num,
    'PUBLISHED',
    'PUBLIC',
    p.published_at,
    COALESCE(p.updated_at, NOW()),
    COALESCE(p.updated_at, NOW()),
    0,
    'legacy-project-' || p.id::TEXT
FROM public."Projects" p
ON CONFLICT (idempotency_key) DO NOTHING;
