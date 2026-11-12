-- Legacy rows do not have reliable provenance (FIT could be merged into manual rows).
-- Keep them visible in the log, but do not infer permission to send them to an AI.
alter table workouts add column source varchar(16) not null default 'UNKNOWN';
update workouts set source = 'GARMIN'
where notes ilike '%importert fra Garmin%'
   or content ? 'garminActivityId' or content ? 'garminFitClientId';
alter table workouts alter column source set default 'MANUAL';
alter table workouts add constraint workouts_source_check check (source in ('MANUAL', 'GARMIN', 'UNKNOWN'));
