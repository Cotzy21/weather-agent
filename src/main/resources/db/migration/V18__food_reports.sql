-- Rapporter fra brukere om offentlig delte matvarer (feil verdier, spam, upassende innhold). En bruker kan
-- rapportere en vare én gang. Rapporter skjuler ikke varen av seg selv: en administrator ser dem og bestemmer
-- (slette varen eller avvise rapportene). Slettes varen, forsvinner rapportene med den.
create table food_reports (
    id          uuid primary key,
    food_id     uuid         not null references custom_foods (id) on delete cascade,
    reporter_id uuid         not null,
    reason      varchar(20)  not null,
    note        varchar(300),
    created_at  timestamptz  not null,
    unique (food_id, reporter_id)
);

create index idx_food_reports_reporter on food_reports (reporter_id);

-- Ikke åpent via Supabase Data API (se V16): all tilgang går gjennom backend.
alter table food_reports enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on food_reports from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on food_reports from authenticated;
    end if;
end
$$;
