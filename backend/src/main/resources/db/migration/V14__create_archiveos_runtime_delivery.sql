create table if not exists archiveos_runtime_delivery (
    id bigserial primary key,
    event_id varchar(120) not null unique,
    idempotency_key varchar(180) not null,
    correlation_id varchar(160),
    causation_id varchar(160),
    order_id varchar(160),
    simulation_run_id varchar(160),
    entity_id varchar(160) not null,
    event_type varchar(100) not null,
    payload_json text not null,
    status varchar(40) not null default 'PENDING',
    retry_count integer not null default 0,
    next_retry_at timestamp,
    publishing_started_at timestamp,
    last_error_code varchar(80),
    last_error_message text,
    created_at timestamp not null,
    updated_at timestamp not null,
    published_at timestamp
);

create index if not exists archiveos_runtime_delivery_status_idx
    on archiveos_runtime_delivery(status, next_retry_at, created_at);
create index if not exists archiveos_runtime_delivery_correlation_idx
    on archiveos_runtime_delivery(correlation_id, created_at);
