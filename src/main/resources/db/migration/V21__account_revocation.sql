-- Account write locks and minimal revocation records. NULL means active.
-- It contains no email, health data or token. Do not cascade this row from auth.users.
create table revoked_accounts (
    user_id uuid primary key,
    revoked_at timestamptz,
    auth_deleted_at timestamptz,
    auth_delete_attempted_at timestamptz
);
alter table revoked_accounts enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on revoked_accounts from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on revoked_accounts from authenticated;
    end if;
end
$$;

-- Serialize writes with deletion on the same owner row. A transaction that started
-- before deletion cannot recreate records after the deletion commits. Row locking
-- also causes stale REPEATABLE READ writers to fail rather than bypass the check.
create function guard_account_write() returns trigger language plpgsql as $$
declare
    owner uuid;
    closed_at timestamptz;
begin
    if tg_table_name = 'habit_logs' then
        select user_id into owner from habits where id = new.habit_id;
    else
        owner := (to_jsonb(new) ->> tg_argv[0])::uuid;
        if tg_op = 'UPDATE' and (to_jsonb(old) ->> tg_argv[0])::uuid is distinct from owner then
            raise exception 'Changing record ownership is not allowed' using errcode = '23514';
        end if;
    end if;
    if owner is null then
        raise exception 'Missing account owner' using errcode = '23514';
    end if;
    insert into revoked_accounts(user_id) values (owner) on conflict (user_id) do nothing;
    select revoked_at into closed_at from revoked_accounts where user_id = owner for update;
    if closed_at is not null then
        raise exception 'Account is closed' using errcode = '23514';
    end if;
    return new;
end
$$;

do $$
declare
    item text;
    owner_column text;
begin
    foreach item in array array['workouts','garmin_daily_metrics','training_plans',
        'training_plan_batches','training_memory','training_profile','user_settings',
        'exercise_name_map','habits','weigh_ins','nutrition_favorites','meal_entries',
        'diet_preferences','calorie_goals','food_reports','custom_foods','custom_meals','saved_routes']
    loop
        owner_column := case item when 'food_reports' then 'reporter_id'
                                  when 'custom_foods' then 'owner_id' else 'user_id' end;
        execute format('create trigger guard_account_write before insert or update on %I
            for each row execute function guard_account_write(%L)', item, owner_column);
    end loop;
    create trigger guard_account_write before insert or update on habit_logs
        for each row execute function guard_account_write();
end
$$;
