-- Egne matvarer: brukeren legger inn varer som mangler i Matvaretabellen
-- (f.eks. en bestemt proteinshake), med næring per 100 g og valgfri porsjon.
-- is_public = delt, så alle brukere kan søke den opp. barcode gjør at en
-- strekkodeskanner senere kan slå varen opp direkte.
create table custom_foods (
    id             uuid primary key,
    owner_id       uuid not null,
    name           varchar(120) not null,
    brand          varchar(80),
    barcode        varchar(32),
    kcal_per_100g  double precision not null,
    protein_g      double precision not null,
    fat_g          double precision not null,
    carb_g         double precision not null,
    portion_name   varchar(40),
    portion_grams  double precision,
    is_public      boolean not null default false,
    created_at     timestamptz not null
);

create index idx_custom_foods_owner on custom_foods (owner_id);
create index idx_custom_foods_public_name on custom_foods (is_public, lower(name));
create index idx_custom_foods_barcode on custom_foods (barcode);

-- Dagboka refererer egne varer som "egen:<uuid>" (41 tegn) - for langt for
-- den opprinnelige kolonnen, som bare var dimensjonert for Matvaretabellens id-er.
alter table meal_entries alter column food_id type varchar(60);
