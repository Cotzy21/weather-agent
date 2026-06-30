create table saved_routes (
    id          uuid primary key,
    user_id     uuid not null,
    name        varchar(120) not null,
    distance_km double precision not null,
    ascent_m    double precision not null,
    geometry    jsonb not null,
    created_at  timestamptz not null
);

create index idx_saved_routes_user on saved_routes (user_id);
