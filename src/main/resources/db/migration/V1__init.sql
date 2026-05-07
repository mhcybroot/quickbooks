create table qbo_connection (
    id bigserial primary key,
    realm_id varchar(128) not null,
    access_token text not null,
    refresh_token text not null,
    token_type varchar(64),
    expires_at timestamp with time zone not null,
    refresh_expires_at timestamp with time zone,
    connected_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uq_qbo_connection_realm_id on qbo_connection(realm_id);

create table csv_mapping_profile (
    id bigserial primary key,
    name varchar(255) not null,
    entity_type varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table csv_mapping_profile_entries (
    mapping_profile_id bigint not null references csv_mapping_profile(id) on delete cascade,
    source_header varchar(255) not null,
    target_field varchar(255) not null,
    primary key (mapping_profile_id, target_field)
);

create table import_run (
    id bigserial primary key,
    entity_type varchar(64) not null,
    status varchar(64) not null,
    source_file_name varchar(255) not null,
    mapping_profile_name varchar(255),
    total_rows integer not null,
    valid_rows integer not null,
    invalid_rows integer not null,
    duplicate_rows integer not null,
    imported_rows integer not null,
    export_csv text,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone
);

create table import_row_result (
    id bigserial primary key,
    import_run_id bigint not null references import_run(id) on delete cascade,
    row_number integer not null,
    source_identifier varchar(255),
    status varchar(64) not null,
    message text,
    raw_data text,
    normalized_data text,
    created_entity_id varchar(255)
);
