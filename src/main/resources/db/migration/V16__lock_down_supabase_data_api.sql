-- SIKKERHET: Supabase eksponerer alle tabeller i skjemaet «public» via et åpent REST-API
-- (https://<prosjekt>.supabase.co/rest/v1/<tabell>). Anon-nøkkelen ligger i frontend-koden, og tabeller
-- laget via migrasjoner (som våre) har radnivå-sikkerhet (RLS) AV og full tilgang for rollene anon og
-- authenticated. Uten dette kan hvem som helst lese og endre ALLE brukeres økter, vekt, måltider osv.
-- direkte mot Supabase og forbi backend.
--
-- Backend kobler til som databaseeieren (rollen postgres), som omgår RLS, så den påvirkes ikke. All tilgang
-- går gjennom Spring-API-et, så vi slår på RLS UTEN policyer (= nekt alt via Data API) og trekker tilbake
-- rettighetene til API-rollene. Lokale Postgres-oppsett uten Supabase-roller hopper over rettighetsdelen.

-- MERK: flyway_schema_history hoppes over i RLS-løkka. Flyway holder selv en lås på den tabellen mens denne migreringen
-- kjører, så «alter table ... enable row level security» ville vente på seg selv til databasen avbryter den
-- (statement timeout, SQL State 57014) og hele oppstarten feiler. Tabellen er likevel utilgjengelig via Data API-et:
-- «revoke all on all tables» under fjerner rettighetene til anon/authenticated for alle tabeller, også denne.
do $$
declare
    t record;
begin
    for t in select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history' loop
        execute format('alter table public.%I enable row level security', t.tablename);
    end loop;

    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on all tables in schema public from anon;
        revoke all on all sequences in schema public from anon;
        alter default privileges in schema public revoke all on tables from anon;
        alter default privileges in schema public revoke all on sequences from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on all tables in schema public from authenticated;
        revoke all on all sequences in schema public from authenticated;
        alter default privileges in schema public revoke all on tables from authenticated;
        alter default privileges in schema public revoke all on sequences from authenticated;
    end if;
end
$$;
