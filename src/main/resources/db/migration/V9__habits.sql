-- Habit tracker: vaner (koffein, søvn, lesing …) og én logg-rad per vane
-- per dag. unit tom = ja/nei-vane; satt = mengde-vane (kopper/timer/min).
create table habits (
    id         uuid primary key,
    user_id    uuid not null,
    name       varchar(40) not null,
    emoji      varchar(16) not null default '',
    unit       varchar(20) not null default '',
    created_at timestamptz not null
);

create index idx_habits_user on habits (user_id);

create table habit_logs (
    id       uuid primary key,
    habit_id uuid not null references habits (id) on delete cascade,
    date     date not null,
    value    double precision not null,
    unique (habit_id, date)
);

create index idx_habit_logs_habit_date on habit_logs (habit_id, date desc);
