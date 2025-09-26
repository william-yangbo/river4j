CREATE TABLE river_migration(
  id bigserial PRIMARY KEY,
  version bigint NOT NULL,
  name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT NOW(),
  CONSTRAINT version CHECK (version >= 1)
);

CREATE UNIQUE INDEX ON river_migration USING btree(version);