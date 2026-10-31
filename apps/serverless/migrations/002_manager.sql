-- The campaign's prep: the arc, NPCs and ledger, with their GM secrets. It's kept here rather than in the web app's
-- bundle because every player downloads the bundle. Like manager, this column is GM-only.

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS manager_content jsonb;
