-- Gjør treningsøkter fleksible: en type (styrke, løping, buldring, fristil ...) og
-- et JSONB-innhold som varierer med typen (sett/dropsett/supersett, eller distanse/tid).
drop table if exists exercise_sets;

alter table workouts
    add column type    varchar(40)  not null default 'STYRKE',
    add column content jsonb        not null default '{}'::jsonb,
    add column notes   text;
