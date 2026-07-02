-- Kosthold-favoritter: matvarene en bruker har merket som favoritt.
-- food_key = "familie:navn" (f.eks. "gronnsaker:Brokkoli") og er unik per bruker,
-- så samme matvare ikke kan lagres dobbelt.
create table nutrition_favorites (
    id         uuid primary key,
    user_id    uuid not null,
    food_key   varchar(120) not null,
    name       varchar(120) not null,
    emoji      varchar(16)  not null default '',
    tag        varchar(40)  not null default '',
    created_at timestamptz  not null,
    unique (user_id, food_key)
);

create index idx_nutrition_favorites_user on nutrition_favorites (user_id);
