-- Lumina Store D1 schema. Run once:
--   wrangler d1 execute lumina-store --remote --file=store/schema.sql
CREATE TABLE IF NOT EXISTS assets (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  type        TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  author      TEXT NOT NULL DEFAULT '',
  version     TEXT NOT NULL DEFAULT '1.0',
  price       TEXT NOT NULL DEFAULT 'Free',
  tags        TEXT NOT NULL DEFAULT '[]',
  previews    TEXT NOT NULL DEFAULT '[]',
  video       TEXT NOT NULL DEFAULT '',
  license     TEXT NOT NULL DEFAULT '',
  size        INTEGER NOT NULL DEFAULT 0,
  downloads   INTEGER NOT NULL DEFAULT 0,
  object_key  TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'published',
  created     INTEGER NOT NULL,
  updated     INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS assets_status_updated ON assets(status, updated DESC);
