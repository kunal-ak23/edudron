-- ============================================================================
-- Liquibase Filename Migration for Core API Bundle
-- ============================================================================
--
-- PURPOSE: Update databasechangelog entries to match renamed master changelog
--          filenames after bundling identity + student + payment into core-api.
--
-- WHEN TO RUN: BEFORE deploying core-api for the first time on this database.
--              Run AFTER the old services are scaled down, BEFORE core-api starts.
--
-- WHAT IT DOES: Liquibase tracks changesets by (id + author + filename).
--               We renamed db.changelog-master.yaml → identity-master.yaml etc.
--               Only INLINE changesets in the master file are affected.
--               Child changelogs (included via 'include' directive) keep their
--               own filenames and are NOT affected.
--
-- SAFE TO RE-RUN: Yes — the WHERE clause is idempotent.
--
-- ROLLBACK: The renamed files are also used by standalone services now,
--           so no rollback is needed. But if needed:
--           UPDATE databasechangelog SET filename = 'db/changelog/db.changelog-master.yaml'
--           WHERE filename = 'db/changelog/identity-master.yaml' AND ...
-- ============================================================================

BEGIN;

-- Fix identity inline changesets (schemas-0000-create, common-0001-init)
-- These are the only changesets defined inline in the master file
UPDATE public.databasechangelog
SET filename = 'db/changelog/identity-master.yaml'
WHERE filename = 'db/changelog/db.changelog-master.yaml'
  AND (id LIKE 'schemas-%' OR id LIKE 'common-%');

-- Verify: should show 0 rows with old master filename
-- (student and payment masters only have 'include' directives, no inline changesets)
SELECT count(*) AS remaining_old_entries
FROM public.databasechangelog
WHERE filename = 'db/changelog/db.changelog-master.yaml';

-- Show what was updated
SELECT id, author, filename, dateexecuted
FROM public.databasechangelog
WHERE filename = 'db/changelog/identity-master.yaml'
ORDER BY orderexecuted;

COMMIT;
