ALTER TABLE pull_requests
    ADD COLUMN additions INTEGER,
    ADD COLUMN deletions INTEGER,
    ADD COLUMN changed_files INTEGER,
    ADD COLUMN commits_count INTEGER,
    ALTER COLUMN author_login DROP NOT NULL;