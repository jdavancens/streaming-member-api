CREATE KEYSPACE IF NOT EXISTS member_api
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS member_api.members (
    id uuid PRIMARY KEY,
    email text,
    password_hash text,
    full_name text,
    country text,
    status text,
    created_at timestamp
);
