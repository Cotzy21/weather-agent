create table health_storage_consent (
    user_id uuid primary key,
    notice_version integer not null,
    granted_at timestamptz,
    withdrawn_at timestamptz,
    last_withdrawn_at timestamptz
);

alter table health_storage_consent enable row level security;
create trigger guard_account_write before insert or update on health_storage_consent
    for each row execute function guard_account_write('user_id');
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on health_storage_consent from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on health_storage_consent from authenticated;
    end if;
end
$$;

-- This trigger sorts after guard_account_write, which holds the account lock from V21.
-- A write that got past HTTP authorization before withdrawal still checks under that lock.
create function require_health_storage_consent() returns trigger language plpgsql as $$
declare
    owner uuid;
begin
    if tg_table_name = 'user_settings' then
        if new.key not in ('program', 'streak') then return new; end if;
    end if;
    if tg_table_name = 'habit_logs' then
        select user_id into owner from habits where id = new.habit_id;
    else
        owner := (to_jsonb(new) ->> tg_argv[0])::uuid;
    end if;
    if not exists (
        select 1 from health_storage_consent
        where user_id = owner and notice_version = 1 and granted_at is not null and withdrawn_at is null
    ) then
        raise exception 'Health storage consent required' using errcode = '23514';
    end if;
    return new;
end
$$;

do $$
declare
    target text;
begin
    foreach target in array array['workouts', 'garmin_daily_metrics', 'training_plans', 'training_plan_batches',
        'training_memory', 'training_profile', 'user_settings', 'exercise_name_map', 'habits', 'habit_logs',
        'weigh_ins', 'nutrition_favorites', 'meal_entries', 'diet_preferences', 'calorie_goals', 'custom_foods', 'custom_meals']
    loop
        execute format('create trigger require_health_storage_consent before insert or update on %I
            for each row execute function require_health_storage_consent(%L)',
            target, case when target = 'custom_foods' then 'owner_id' else 'user_id' end);
    end loop;
end
$$;
