-- Egne måltider satt sammen av favoritt-ingredienser (Kosthold-fanen).
-- Ingrediensene lagres som JSONB slik frontenden bruker dem:
--   [ { "key": "kjott:Kyllingfilet", "n": "Kyllingfilet", "e": "🍗",
--       "m": {"p":23,"k":0,"f":2}, "grams": 150 }, ... ]
create table custom_meals (
    id          uuid primary key,
    user_id     uuid not null,
    name        varchar(120) not null,
    ingredients jsonb not null,
    created_at  timestamptz not null
);

create index idx_custom_meals_user on custom_meals (user_id);
