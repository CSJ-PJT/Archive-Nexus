create table if not exists nexus_outbox_event (
  id bigserial primary key,
  event_id varchar(80) not null unique,
  idempotency_key varchar(160) not null unique,
  event_type varchar(80) not null,
  aggregate_type varchar(80) not null,
  aggregate_id varchar(120) not null,
  source varchar(80) not null default 'Archive-Nexus',
  schema_version integer not null default 1,
  payload jsonb not null,
  status varchar(40) not null default 'PENDING',
  retry_count integer not null default 0,
  last_error text,
  occurred_at timestamptz not null,
  created_at timestamptz not null,
  published_at timestamptz
);

create index if not exists nexus_outbox_event_status_idx on nexus_outbox_event(status, created_at);
create index if not exists nexus_outbox_event_type_idx on nexus_outbox_event(event_type, occurred_at desc);
create index if not exists nexus_outbox_event_aggregate_idx on nexus_outbox_event(aggregate_type, aggregate_id);
