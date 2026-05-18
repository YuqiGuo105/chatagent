-- Initialize pgvector extension for ChatAgent
CREATE EXTENSION IF NOT EXISTS vector;

-- Create chat_embeddings table for storing vector embeddings
CREATE TABLE IF NOT EXISTS chat_embeddings (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for efficient vector search
CREATE INDEX IF NOT EXISTS ON chat_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Create sequence for IDs if needed
CREATE SEQUENCE IF NOT EXISTS hibernate_sequence START WITH 1 INCREMENT BY 1;
