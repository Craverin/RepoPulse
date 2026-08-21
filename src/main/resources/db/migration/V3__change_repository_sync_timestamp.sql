ALTER TABLE repositories
    ALTER COLUMN last_synced_at DROP NOT NULL,
    ALTER COLUMN last_synced_at DROP DEFAULT;

ALTER TABLE repositories RENAME COLUMN last_synced_at TO summary_synced_at;
ALTER TABLE repositories ADD COLUMN size_synced_at TIMESTAMPTZ;
