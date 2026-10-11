-- A durable receipt makes a retry after a lost HTTP response safe.
create table training_plan_batches (
    user_id uuid not null,
    client_id uuid not null,
    request jsonb not null,
    response jsonb not null,
    created_at timestamptz not null default now(),
    primary key (user_id, client_id)
);
alter table training_plan_batches enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on training_plan_batches from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on training_plan_batches from authenticated;
    end if;
end
$$;
