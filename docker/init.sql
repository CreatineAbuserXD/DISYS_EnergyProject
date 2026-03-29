CREATE TABLE IF NOT EXISTS usage_bucket (
    id                  BIGSERIAL PRIMARY KEY,
    bucket_hour         TIMESTAMP NOT NULL UNIQUE,
    community_produced  DOUBLE PRECISION NOT NULL DEFAULT 0,
    community_used      DOUBLE PRECISION NOT NULL DEFAULT 0,
    grid_used           DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS percentage_record (
    id                  BIGSERIAL PRIMARY KEY,
    bucket_hour         TIMESTAMP NOT NULL UNIQUE,
    community_depleted  DOUBLE PRECISION NOT NULL DEFAULT 0,
    grid_portion        DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
