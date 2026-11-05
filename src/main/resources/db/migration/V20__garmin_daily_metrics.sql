create table garmin_daily_metrics (
    user_id uuid not null,
    day date not null,
    metrics jsonb not null,
    primary key (user_id, day)
);
alter table garmin_daily_metrics enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on garmin_daily_metrics from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on garmin_daily_metrics from authenticated;
    end if;
end
$$;
