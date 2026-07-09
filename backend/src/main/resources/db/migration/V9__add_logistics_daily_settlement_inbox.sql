create table if not exists nexus_logistics_daily_settlement (
  id bigserial primary key,
  settlement_id varchar(100) not null unique,
  idempotency_key varchar(180) not null unique,
  source varchar(80) not null default 'Archive-Logistics',
  schema_version integer not null default 1,
  settlement_date date not null,
  factory_id varchar(80) not null,
  processing_status varchar(40) not null default 'RECEIVED',
  currency varchar(12) not null default 'KRW',
  total_shipments integer not null default 0,
  delayed_shipments integer not null default 0,
  held_shipments integer not null default 0,
  total_quantity integer not null default 0,
  total_logistics_cost numeric(18,2) not null default 0,
  manufacturing_impact_cost numeric(18,2) not null default 0,
  on_time_rate numeric(8,4) not null default 0,
  evidence_json text not null,
  payload_json text not null,
  occurred_at timestamptz not null,
  received_at timestamptz not null,
  processed_at timestamptz,
  last_duplicate_received_at timestamptz,
  duplicate_count integer not null default 0
);

create index if not exists nexus_logistics_daily_settlement_date_idx
  on nexus_logistics_daily_settlement(settlement_date desc, factory_id);

create index if not exists nexus_logistics_daily_settlement_status_idx
  on nexus_logistics_daily_settlement(processing_status, received_at desc);

create index if not exists nexus_logistics_daily_settlement_factory_idx
  on nexus_logistics_daily_settlement(factory_id, settlement_date desc);
