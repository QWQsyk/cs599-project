CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120),
  role_code VARCHAR(40) NOT NULL DEFAULT 'USER',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS roles (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS legal_sessions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(200) NOT NULL,
  case_type VARCHAR(80),
  status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_legal_sessions_user_updated ON legal_sessions(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES legal_sessions(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  metadata TEXT NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created ON chat_messages(session_id, created_at);

CREATE TABLE IF NOT EXISTS case_facts (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES legal_sessions(id) ON DELETE CASCADE,
  case_type VARCHAR(80),
  sub_type VARCHAR(120),
  claim_goals_json TEXT NOT NULL DEFAULT '[]',
  completeness_score NUMERIC(5,2) NOT NULL DEFAULT 0,
  conclusion_level VARCHAR(40),
  facts_json TEXT NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS case_analyses (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES legal_sessions(id) ON DELETE CASCADE,
  case_type VARCHAR(80),
  facts_json TEXT NOT NULL DEFAULT '{}',
  missing_questions_json TEXT NOT NULL DEFAULT '[]',
  issues_json TEXT NOT NULL DEFAULT '[]',
  evidence_assessments_json TEXT NOT NULL DEFAULT '[]',
  evidence_json TEXT NOT NULL DEFAULT '[]',
  liability_json TEXT NOT NULL DEFAULT '[]',
  action_path_json TEXT NOT NULL DEFAULT '[]',
  risks_json TEXT NOT NULL DEFAULT '[]',
  citations_json TEXT NOT NULL DEFAULT '[]',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_case_analyses_session_updated ON case_analyses(session_id, updated_at DESC);
ALTER TABLE case_analyses ADD COLUMN IF NOT EXISTS issues_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE case_analyses ADD COLUMN IF NOT EXISTS evidence_assessments_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE case_analyses ADD COLUMN IF NOT EXISTS sub_type VARCHAR(120);
ALTER TABLE case_analyses ADD COLUMN IF NOT EXISTS claim_goals_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE case_analyses ADD COLUMN IF NOT EXISTS completeness_score NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE case_analyses ADD COLUMN IF NOT EXISTS conclusion_level VARCHAR(40);

CREATE TABLE IF NOT EXISTS legal_articles (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(240) NOT NULL,
  article_no VARCHAR(80),
  content TEXT NOT NULL,
  source_url VARCHAR(500),
  effective BOOLEAN NOT NULL DEFAULT TRUE,
  embedding vector(1536)
);
CREATE INDEX IF NOT EXISTS idx_legal_articles_effective ON legal_articles(effective);

CREATE TABLE IF NOT EXISTS legal_cases (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(240) NOT NULL,
  case_type VARCHAR(80),
  summary TEXT NOT NULL,
  source_url VARCHAR(500),
  embedding vector(1536)
);
CREATE INDEX IF NOT EXISTS idx_legal_cases_type ON legal_cases(case_type);

CREATE TABLE IF NOT EXISTS legal_documents (
  id BIGSERIAL PRIMARY KEY,
  doc_type VARCHAR(40) NOT NULL,
  source_id BIGINT,
  chunk_no INT NOT NULL DEFAULT 0,
  content TEXT NOT NULL,
  metadata TEXT NOT NULL DEFAULT '{}',
  embedding vector(1536),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS analysis_reports (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES legal_sessions(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(240) NOT NULL,
  content_md TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_files (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  session_id BIGINT REFERENCES legal_sessions(id) ON DELETE SET NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(120),
  storage_path VARCHAR(500),
  parse_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  parsed_json TEXT NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS prompt_templates (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS model_call_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT,
  session_id BIGINT,
  provider VARCHAR(40),
  prompt_code VARCHAR(80),
  input_tokens INT DEFAULT 0,
  output_tokens INT DEFAULT 0,
  latency_ms INT DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS privacy_mask_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT,
  session_id BIGINT,
  mask_type VARCHAR(80),
  original_hash VARCHAR(128),
  masked_value VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
