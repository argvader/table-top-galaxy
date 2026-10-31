-- Characters and campaigns. Each statement ends with a semicolon at the end of a line: bb db:migrate sends them to the
-- Data API one at a time, because it refuses multi-statement calls.
-- ids of people are Cognito subs, so they are text. The library stays on DynamoDB for now.

CREATE TABLE IF NOT EXISTS campaigns (
  id uuid PRIMARY KEY,
  gm_id text NOT NULL,
  name text NOT NULL,
  data jsonb NOT NULL DEFAULT '{}',
  manager jsonb,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS campaign_members (
  campaign_id uuid NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
  user_id text NOT NULL,
  role text NOT NULL CHECK (role IN ('gm', 'player')),
  display_name text NOT NULL,
  PRIMARY KEY (campaign_id, user_id)
);

CREATE INDEX IF NOT EXISTS campaign_members_user_idx ON campaign_members (user_id);

CREATE TABLE IF NOT EXISTS characters (
  id uuid PRIMARY KEY,
  owner_id text NOT NULL,
  campaign_id uuid REFERENCES campaigns (id) ON DELETE SET NULL,
  name text NOT NULL,
  theme text,
  portrait_key text,
  data jsonb NOT NULL DEFAULT '{}',
  version integer NOT NULL DEFAULT 1,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS characters_owner_idx ON characters (owner_id);

CREATE INDEX IF NOT EXISTS characters_campaign_idx ON characters (campaign_id);

CREATE TABLE IF NOT EXISTS invites (
  code text PRIMARY KEY,
  campaign_id uuid NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
  created_by text NOT NULL,
  expires_at timestamptz NOT NULL
);
