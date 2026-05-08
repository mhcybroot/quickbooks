create table invoice_import_preference (
    id bigint primary key,
    grouping_enabled boolean not null,
    updated_at timestamp with time zone not null
);
