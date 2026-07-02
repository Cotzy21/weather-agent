-- Treningsplaner: AI-forslag (eller egne opplegg) brukeren har lagret i
-- profilen sin. Samme fleksible form som workouts (type + JSONB-innhold),
-- så en plan kan fylle økt-skjemaet direkte.
create table training_plans (
    id         uuid primary key,
    user_id    uuid not null,
    title      varchar(120) not null,
    type       varchar(40) not null,
    content    jsonb not null,
    rationale  text,
    created_at timestamptz not null
);

create index idx_training_plans_user on training_plans (user_id, created_at desc);
