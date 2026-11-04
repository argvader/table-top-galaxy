-- The table's story point pool (CRB p.27-30): {"player": n, "gm": n}. NULL until the GM starts a session. Its own
-- column, not part of data, so the GM saving the campaign never overwrites a point a player has just spent.

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS story jsonb;
