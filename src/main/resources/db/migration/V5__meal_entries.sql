-- Kostholdsdagbok: én rad per logget matvare. Kalorier/makroer er et
-- øyeblikksbilde fra Matvaretabellen ved logging; vitaminer/mineraler
-- beregnes ved lesing via food_id mot den cachede tabellen.
create table meal_entries (
    id         uuid primary key,
    user_id    uuid not null,
    date       date not null,
    meal       varchar(20) not null,
    food_id    varchar(20) not null,
    food_name  varchar(160) not null,
    grams      double precision not null,
    kcal       double precision not null,
    protein_g  double precision not null,
    fat_g      double precision not null,
    carb_g     double precision not null,
    created_at timestamptz not null
);

create index idx_meal_entries_user_date on meal_entries (user_id, date);
