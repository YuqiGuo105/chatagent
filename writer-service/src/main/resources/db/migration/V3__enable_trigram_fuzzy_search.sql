-- V3__enable_trigram_fuzzy_search.sql
-- ============================================================
-- Enables pg_trgm (trigram similarity) and unaccent extensions
-- and adds GIN trigram indexes on title columns for fast fuzzy
-- search (typo tolerance, partial-word matching).
--
-- These are used by ContentSearchService.word_similarity() calls
-- for fuzzy search alongside the FTS (websearch_to_tsquery) and
-- ILIKE keyword matching.
-- ============================================================

-- ── 1. Extensions ────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ── 2. Custom text search config with unaccent (accent-insensitive) ──────
-- Drop and recreate so re-runs are idempotent
DROP TEXT SEARCH CONFIGURATION IF EXISTS writer.portfolio_search CASCADE;
CREATE TEXT SEARCH CONFIGURATION writer.portfolio_search (COPY = pg_catalog.english);
ALTER TEXT SEARCH CONFIGURATION writer.portfolio_search
    ALTER MAPPING FOR hword, hword_part, word
    WITH unaccent, english_stem;

-- ── 3. Trigram GIN indexes on title (for word_similarity WHERE clause) ───
-- These allow PostgreSQL to use an index scan for word_similarity(q, title) > 0.2

DROP INDEX IF EXISTS writer.writer_blogs_trgm_title_idx;
CREATE INDEX writer_blogs_trgm_title_idx
    ON writer.blogs USING GIN (title gin_trgm_ops);

DROP INDEX IF EXISTS writer.writer_life_blogs_trgm_title_idx;
CREATE INDEX writer_life_blogs_trgm_title_idx
    ON writer.life_blogs USING GIN (title gin_trgm_ops);

DROP INDEX IF EXISTS writer.writer_projects_trgm_title_idx;
CREATE INDEX writer_projects_trgm_title_idx
    ON writer.projects USING GIN (title gin_trgm_ops);

-- Trigram index on technology for projects (e.g. "sprng boot" → "Spring Boot")
DROP INDEX IF EXISTS writer.writer_projects_trgm_tech_idx;
CREATE INDEX writer_projects_trgm_tech_idx
    ON writer.projects USING GIN (technology gin_trgm_ops);
