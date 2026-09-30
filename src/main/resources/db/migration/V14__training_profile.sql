-- Treningsprofil fra onboarding: erfaring, mål, utstyr, skader osv. (én rad per bruker, upsert).
-- Finnes raden ikke, er brukeren ikke onboardet ennå. Brukes til å tilpasse AI-forslagene
-- og til å velge modellnivå (nybegynnere får den sterkeste modellen).
create table training_profile (
    user_id             uuid primary key,
    experience_level    varchar(20)  not null,
    training_months     integer      not null,
    sessions_per_week   integer      not null,
    goal                varchar(20)  not null,
    equipment           varchar(20)  not null,
    days_per_week       integer      not null,
    session_minutes     integer      not null,
    technique           varchar(10)  not null,
    explanation_style   varchar(10)  not null,
    injuries            varchar(300) not null default '',
    background          varchar(200) not null default '',
    strengths           varchar(200) not null default '',
    weaknesses          varchar(200) not null default '',
    updated_at          timestamptz  not null
);
