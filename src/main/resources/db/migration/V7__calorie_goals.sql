-- Kalorimål-profil: én rad per bruker (user_id = primærnøkkel, upsert ved
-- lagring). Selve kcal-målet beregnes i backend fra disse feltene.
create table calorie_goals (
    user_id          uuid primary key,
    weight_kg        double precision not null,
    height_cm        double precision not null,
    age              integer not null,
    sex              varchar(1) not null,
    activity_level   varchar(20) not null,
    goal_kg_per_week double precision not null,
    updated_at       timestamptz not null
);
