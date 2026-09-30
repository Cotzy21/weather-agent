-- Kroppsvekt: én innveiing per bruker per dag (ny logging samme dag overskriver).
create table weigh_ins (
    id         uuid primary key,
    user_id    uuid not null,
    date       date not null,
    weight_kg  double precision not null,
    created_at timestamptz not null,
    unique (user_id, date)
);

create index idx_weigh_ins_user_date on weigh_ins (user_id, date desc);
