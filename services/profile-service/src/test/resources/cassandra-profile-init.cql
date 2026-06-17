CREATE KEYSPACE IF NOT EXISTS member_api
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS member_api.profiles (
    member_id uuid,
    profile_id uuid,
    name text,
    avatar_url text,
    is_kids boolean,
    language text,
    pin_hash text,
    PRIMARY KEY (member_id, profile_id)
);
