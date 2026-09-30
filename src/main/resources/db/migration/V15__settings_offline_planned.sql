-- Server-lagrede innstillinger per bruker (dashboard-oppsett, treningsprogram med startdato og lengde).
create table user_settings (
    user_id    uuid         not null,
    key        varchar(40)  not null,
    value      jsonb        not null,
    updated_at timestamptz  not null,
    primary key (user_id, key)
);

-- client_id: klient-generert id så en økt som sendes på nytt (offline-kø) ikke dupliseres.
-- planned_id: planen økten ble startet fra, så uke-oversikten kobler på id i stedet for tittel.
alter table workouts add column client_id uuid;
alter table workouts add column planned_id uuid;
create unique index uq_workouts_client on workouts (user_id, client_id) where client_id is not null;
