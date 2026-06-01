-- ============================================================
-- Portfolio MCP read-only views
-- Run in Supabase SQL editor with the OWNER of the source tables.
-- These views expose ONLY non-sensitive columns intended for
-- LLM-driven natural-language queries.
-- ============================================================

-- 1. Projects view
CREATE OR REPLACE VIEW public.v_portfolio_projects AS
SELECT
    id,
    title,
    content        AS description,
    category,
    year,
    technology     AS tech_stack,
    image_url,
    "URL"          AS url,
    published_at,
    updated_at
FROM public."Projects"
WHERE title IS NOT NULL;

COMMENT ON VIEW public.v_portfolio_projects IS
  'Public projects in the portfolio. Columns: id, title, description, category, year, tech_stack, image_url, url, published_at, updated_at.';

-- 2. Tech blogs view
CREATE OR REPLACE VIEW public.v_portfolio_tech_blogs AS
SELECT
    id,
    title,
    description    AS summary,
    category,
    tags,
    image_url,
    date           AS published_date
FROM public."Blogs"
WHERE title IS NOT NULL;

COMMENT ON VIEW public.v_portfolio_tech_blogs IS
  'Technical / portfolio blog posts. Columns: id, title, summary, category, tags, image_url, published_date.';

-- 3. Life blogs view
CREATE OR REPLACE VIEW public.v_portfolio_life_blogs AS
SELECT
    id,
    title,
    description    AS summary,
    category,
    tags,
    image_url,
    published_at,
    created_at
FROM public.life_blogs
WHERE require_login = false;  -- only public posts

COMMENT ON VIEW public.v_portfolio_life_blogs IS
  'Personal / life blog posts (public only). Columns: id, title, summary, category, tags, image_url, published_at, created_at.';

-- 4. Work / education experience view
CREATE OR REPLACE VIEW public.v_portfolio_experience AS
SELECT
    id,
    name           AS organization,
    subname        AS role,
    date           AS period,
    text           AS summary
FROM public.experience;

COMMENT ON VIEW public.v_portfolio_experience IS
  'Work and education experience. Columns: id, organization, role, period, summary.';

-- ============================================================
-- Read-only role for the MCP server
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portfolio_reader') THEN
        CREATE ROLE portfolio_reader LOGIN PASSWORD 'CHANGE_ME_IN_SUPABASE';
    END IF;
END$$;

GRANT USAGE ON SCHEMA public TO portfolio_reader;

GRANT SELECT ON
    public.v_portfolio_projects,
    public.v_portfolio_tech_blogs,
    public.v_portfolio_life_blogs,
    public.v_portfolio_experience
  TO portfolio_reader;

-- Ensure the role CANNOT read anything else
REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM portfolio_reader;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM portfolio_reader;
-- (then re-grant only the views above, already done)
GRANT SELECT ON
    public.v_portfolio_projects,
    public.v_portfolio_tech_blogs,
    public.v_portfolio_life_blogs,
    public.v_portfolio_experience
  TO portfolio_reader;
