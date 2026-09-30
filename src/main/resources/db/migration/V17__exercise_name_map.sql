-- Hva en språkmodell har svart om et øvelsesnavn fra en import (f.eks. Garmins «Barbell Bench Press» -> appens
-- «bench-press»), per bruker, så samme navn aldri må spørres om to ganger. exercise_id er NULL når modellen
-- mente at ingen av appens øvelser er den samme (så vi heller ikke spør om det igjen).
create table exercise_name_map (
    user_id     uuid         not null,
    name_key    varchar(120) not null,
    exercise_id varchar(60),
    created_at  timestamptz  not null,
    primary key (user_id, name_key)
);

-- Ikke åpent via Supabase Data API (se V16): all tilgang går gjennom backend.
alter table exercise_name_map enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on exercise_name_map from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on exercise_name_map from authenticated;
    end if;
end
$$;
