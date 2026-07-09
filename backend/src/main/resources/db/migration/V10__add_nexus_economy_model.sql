create table if not exists nexus_revenue_event (
  id bigserial primary key,
  event_id varchar(100) not null unique,
  idempotency_key varchar(180) not null unique,
  simulation_run_id varchar(100),
  settlement_cycle_id varchar(100),
  correlation_id varchar(120),
  causation_id varchar(120),
  hop_count integer not null default 0,
  max_hop integer not null default 8,
  revenue_type varchar(80) not null,
  revenue_amount numeric(18,2) not null,
  currency varchar(12) not null default 'KRW',
  reason text,
  created_at timestamptz not null
);

create table if not exists nexus_cost_event (
  id bigserial primary key,
  event_id varchar(100) not null unique,
  idempotency_key varchar(180) not null unique,
  simulation_run_id varchar(100),
  settlement_cycle_id varchar(100),
  correlation_id varchar(120),
  causation_id varchar(120),
  hop_count integer not null default 0,
  max_hop integer not null default 8,
  source_service varchar(80) not null,
  cost_type varchar(80) not null,
  cost_amount numeric(18,2) not null,
  currency varchar(12) not null default 'KRW',
  reason text,
  created_at timestamptz not null
);

create table if not exists nexus_profit_snapshot (
  id bigserial primary key,
  snapshot_id varchar(120) not null unique,
  settlement_date date not null,
  revenue_amount numeric(18,2) not null,
  cost_amount numeric(18,2) not null,
  profit_amount numeric(18,2) not null,
  cash_balance numeric(18,2) not null,
  bankruptcy_risk varchar(40) not null,
  created_at timestamptz not null
);

create index if not exists nexus_revenue_event_type_created_idx
  on nexus_revenue_event(revenue_type, created_at desc);

create index if not exists nexus_cost_event_type_created_idx
  on nexus_cost_event(cost_type, created_at desc);

create index if not exists nexus_cost_event_source_created_idx
  on nexus_cost_event(source_service, created_at desc);

create index if not exists nexus_profit_snapshot_date_idx
  on nexus_profit_snapshot(settlement_date desc, created_at desc);
