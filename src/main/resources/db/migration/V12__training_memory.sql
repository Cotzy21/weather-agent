-- Treningsminne: det brukeren har sagt om seg selv (én rad per bruker, upsert).
-- liked/disliked er komma-separerte øvelsesnavn; notes er fritekst appen skal
-- huske (utstyr, skader, mål). Vanene (frekvens, dager, øvelser) regnes ut fra
-- øktene ved hvert kall og lagres ikke her.
create table training_memory (
    user_id    uuid primary key,
    liked      varchar(1500) not null default '',
    disliked   varchar(1500) not null default '',
    notes      varchar(500)  not null default '',
    updated_at timestamptz   not null
);
