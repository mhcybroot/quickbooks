create table payment_mapping_profile (
    id bigserial primary key,
    name varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table payment_mapping_profile_entries (
    mapping_profile_id bigint not null references payment_mapping_profile(id) on delete cascade,
    source_header varchar(255) not null,
    target_field varchar(255) not null,
    primary key (mapping_profile_id, target_field)
);
