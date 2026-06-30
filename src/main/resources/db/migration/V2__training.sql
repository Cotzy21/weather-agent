create table workouts (
    id         uuid primary key,
    user_id    uuid not null,
    date       date not null,
    title      varchar(120) not null,
    created_at timestamptz not null
);

create index idx_workouts_user on workouts (user_id, date desc);

create table exercise_sets (
    id         uuid primary key,
    workout_id uuid not null references workouts (id) on delete cascade,
    exercise   varchar(120) not null,
    reps       integer not null,
    weight_kg  double precision not null,
    position   integer not null
);

create index idx_exercise_sets_workout on exercise_sets (workout_id);
create index idx_exercise_sets_exercise on exercise_sets (lower(exercise));
