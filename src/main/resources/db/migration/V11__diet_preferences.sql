-- Kostholdspreferanser: én rad per bruker (upsert). allergies er komma-
-- separerte allergen-koder (GLUTEN,MELK …), dislikes komma-separerte ord.
-- Enkle tekstkolonner med vilje: små lister, alltid lest/skrevet samlet.
create table diet_preferences (
    user_id    uuid primary key,
    diet       varchar(20)   not null default 'ALT',
    allergies  varchar(300)  not null default '',
    dislikes   varchar(1000) not null default '',
    updated_at timestamptz   not null
);
