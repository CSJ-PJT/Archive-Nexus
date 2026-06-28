create table if not exists factories (id varchar(40) primary key, name varchar(120) not null, kind varchar(60) not null, scenario text not null);
create table if not exists production_lines (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), name varchar(120) not null, product varchar(120) not null);
create table if not exists machines (id varchar(40) primary key, line_id varchar(40) not null references production_lines(id), name varchar(120) not null, vibration_threshold numeric(8,3) not null, temperature_threshold numeric(8,3) not null, current_threshold numeric(8,3) not null);
create table if not exists sensor_metrics (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), machine_id varchar(40) not null references machines(id), tick bigint not null, measured_at timestamptz not null, vibration numeric(8,3) not null, temperature_celsius numeric(8,3) not null, current_ampere numeric(8,3) not null);
create table if not exists production_orders (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), product varchar(120) not null, target_quantity integer not null, produced_quantity integer not null, status varchar(40) not null);
create table if not exists work_orders (id varchar(40) primary key, production_order_id varchar(40) not null references production_orders(id), line_id varchar(40) not null references production_lines(id), status varchar(40) not null);
create table if not exists lots (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), product varchar(120) not null, quantity integer not null, shipment_hold boolean not null);
create table if not exists quality_inspections (id varchar(40) primary key, lot_id varchar(40) not null references lots(id), factory_id varchar(40) not null references factories(id), defect_rate numeric(8,4) not null, result varchar(40) not null);
create table if not exists defect_events (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), lot_id varchar(40) references lots(id), severity varchar(40) not null, reason text not null);
create table if not exists inventory_items (id varchar(40) primary key, name varchar(120) not null, type varchar(40) not null, quantity integer not null, safety_stock integer not null);
create table if not exists inventory_transactions (id varchar(40) primary key, item_id varchar(40) not null references inventory_items(id), factory_id varchar(40) not null references factories(id), type varchar(40) not null, quantity integer not null, occurred_at timestamptz not null);
create table if not exists warehouses (id varchar(40) primary key, name varchar(120) not null, location varchar(120) not null);
create table if not exists logistics_shipments (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), destination varchar(120) not null, status varchar(40) not null, priority integer not null);
create table if not exists maintenance_events (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), machine_id varchar(40) not null references machines(id), severity varchar(40) not null, cause text not null, status varchar(40) not null);
create table if not exists factory_alerts (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), severity varchar(40) not null, category varchar(60) not null, message text not null, occurred_at timestamptz not null);
create table if not exists rpa_tasks (id varchar(40) primary key, factory_id varchar(40) not null references factories(id), status varchar(60) not null, trigger text not null, recommendation text not null, approval_required boolean not null, created_at timestamptz not null);
create table if not exists rpa_decisions (id varchar(40) primary key, rpa_task_id varchar(40) not null references rpa_tasks(id), decision varchar(40) not null, actor varchar(120) not null, decided_at timestamptz not null);
create table if not exists rpa_execution_logs (id varchar(40) primary key, rpa_task_id varchar(40) not null references rpa_tasks(id), message text not null, logged_at timestamptz not null);
create table if not exists simulator_runs (id varchar(40) primary key, running boolean not null, tick bigint not null, started_at timestamptz not null, stopped_at timestamptz);
create table if not exists batch_snapshots (id bigserial primary key, tick bigint not null, factory_count integer not null, production_order_count integer not null, total_produced_quantity integer not null, average_defect_rate numeric(8,4) not null, alert_count integer not null, pending_approval_count integer not null, created_at timestamptz not null);
create table if not exists archiveos_interactions (id varchar(40) primary key, type varchar(80) not null, factory_id varchar(40), payload text not null, occurred_at timestamptz not null);
create table if not exists nexus_tasks (id varchar(48) primary key,title varchar(180) not null,task_type varchar(40) not null,factory_id varchar(40),question text,requested_by varchar(120) not null,status varchar(20) not null,attempt_count integer not null default 0,max_attempts integer not null default 3,result_summary text,error_message text,created_at timestamptz not null,started_at timestamptz,completed_at timestamptz,updated_at timestamptz not null);
create table if not exists nexus_task_logs (id bigserial primary key,task_id varchar(48) not null references nexus_tasks(id) on delete cascade,level varchar(16) not null,message text not null,created_at timestamptz not null);

create table if not exists simulator_state (
    id varchar(80) primary key,
    running boolean not null,
    tick bigint not null,
    last_parallel_worker_count integer not null,
    factories_json text not null,
    sensor_metrics_json text not null,
    production_orders_json text not null,
    lots_json text not null,
    quality_inspections_json text not null,
    inventory_items_json text not null,
    inventory_transactions_json text not null,
    logistics_shipments_json text not null,
    maintenance_events_json text not null,
    alerts_json text not null,
    rpa_tasks_json text not null,
    batch_snapshots_json text not null,
    archiveos_interactions_json text not null,
    saved_at timestamptz not null
);

create table if not exists ai_query_history (
    query_id varchar(80) primary key,
    original_question text not null,
    requested_by varchar(120) not null,
    selected_factory_id varchar(80),
    routed_intents_json text not null,
    invoked_agents_json text not null,
    agent_results_json text not null,
    final_answer text not null,
    evidence_json text not null,
    recommended_actions_json text not null,
    confidence numeric(6,5) not null,
    execution_status varchar(40) not null,
    execution_time_ms bigint not null,
    error_message text,
    rpa_task_id varchar(80),
    created_at timestamptz not null
);
