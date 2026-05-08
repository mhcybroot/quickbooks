create table import_batch (
    id bigserial primary key,
    batch_name varchar(255) not null,
    status varchar(64) not null,
    total_files integer not null,
    validated_files integer not null,
    runnable_files integer not null,
    completed_files integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

alter table import_run
    add column batch_id bigint references import_batch(id) on delete set null,
    add column batch_order integer,
    add column dependency_group varchar(255);

create index idx_import_run_batch_id on import_run(batch_id);
create index idx_import_run_entity_created_at on import_run(entity_type, created_at desc);
