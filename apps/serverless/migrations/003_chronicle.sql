-- The Chronicle: session recaps and wiki pages every member of a campaign can read. bb chronicle:publish writes these
-- from campaigns/<campaign>/chronicle; the API only reads them. image_key is a session's title card, if it has one.

CREATE TABLE IF NOT EXISTS chronicle_entries (
  campaign_id uuid NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
  kind text NOT NULL CHECK (kind IN ('session', 'pc', 'npc', 'faction', 'location')),
  slug text NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  number integer,
  played_on date,
  image_key text,
  published_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (campaign_id, kind, slug)
);
