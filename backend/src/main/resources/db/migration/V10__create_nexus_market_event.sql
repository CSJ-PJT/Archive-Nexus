create table if not exists nexus_market_event (
  id bigserial primary key,
  event_id varchar(120) not null unique,
  idempotency_key varchar(180) not null unique,
  source varchar(120) not null default 'Archive-Market',
  event_type varchar(80) not null,
  processing_status varchar(40) not null default 'RECEIVED',
  schema_version integer not null default 1,
  occurred_at timestamptz not null,
  received_at timestamptz not null,
  simulation_run_id varchar(120),
  settlement_cycle_id varchar(120),
  correlation_id varchar(120),
  causation_id varchar(120),
  hop_count integer not null,
  max_hop integer not null,
  payload_json text not null,
  reason text,
  outbox_event_count integer not null default 0,
  outbox_event_ids text not null default '[]'
);

create index if not exists nexus_market_event_status_idx on nexus_market_event(processing_status, received_at);
create index if not exists nexus_market_event_type_idx on nexus_market_event(event_type, occurred_at);
create index if not exists nexus_market_event_source_idx on nexus_market_event(source, received_at);
