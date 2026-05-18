-- ChatAgent / Spring AI pgvector schema
-- Idempotent. Safe to run against an existing Railway database.

-- Extensions required by Spring AI's PgVectorStore
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- KB table: matches `spring.ai.vectorstore.pgvector` settings in application.yaml
--   table-name : kb_documents
--   dimensions : 1536  (OpenAI text-embedding-3-small / ada-002)
--   distance   : COSINE_DISTANCE
--   index-type : HNSW
CREATE TABLE IF NOT EXISTS public.kb_documents (
    id          UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT,
    metadata    JSONB,
    embedding   vector(1536)
);

-- HNSW cosine index; created once, no-op on repeat.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename  = 'kb_documents'
          AND indexname  = 'kb_documents_embedding_idx'
    ) THEN
        CREATE INDEX kb_documents_embedding_idx
            ON public.kb_documents
            USING hnsw (embedding vector_cosine_ops);
    END IF;
END $$;

-- =====================================================================
-- GitHub project documents: stores chunked & embedded content fetched
-- from configured GitHub repositories (READMEs, source files, etc.).
-- Separate from kb_documents so retrieval and re-sync stay independent.
-- =====================================================================
CREATE TABLE IF NOT EXISTS public.github_project_documents (
    id              BIGSERIAL PRIMARY KEY,
    repo_owner      VARCHAR(255) NOT NULL,
    repo_name       VARCHAR(255) NOT NULL,
    branch          VARCHAR(100) DEFAULT 'main',
    file_path       TEXT         NOT NULL,
    file_type       VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    chunk_index     INTEGER      NOT NULL DEFAULT 0,
    metadata        JSONB,
    embedding       vector(1536),
    last_synced_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (repo_owner, repo_name, file_path, chunk_index)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename  = 'github_project_documents'
          AND indexname  = 'github_project_documents_embedding_idx'
    ) THEN
        CREATE INDEX github_project_documents_embedding_idx
            ON public.github_project_documents
            USING hnsw (embedding vector_cosine_ops);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS github_project_documents_repo_idx
    ON public.github_project_documents (repo_owner, repo_name);

CREATE INDEX IF NOT EXISTS github_project_documents_file_type_idx
    ON public.github_project_documents (file_type);
