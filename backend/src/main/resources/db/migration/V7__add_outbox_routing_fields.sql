alter table nexus_outbox_event
  add column if not exists target_service varchar(50),
  add column if not exists target_url varchar(500),
  add column if not exists routing_status varchar(50),
  add column if not exists last_publish_target varchar(50),
  add column if not exists last_publish_attempt_at timestamptz,
  add column if not exists publish_skipped_reason text;

update nexus_outbox_event
set target_service = case
    when event_type in (
      'LOGISTICS_DISPATCHED',
      'URGENT_DELIVERY_REQUESTED',
      'SHIPMENT_HOLD_RELEASED',
      'MATERIAL_TRANSFER_REQUESTED',
      'QUALITY_REPLACEMENT_SHIPMENT'
    ) then 'LOGITICS'
    when event_type = 'SHIPMENT_HOLD_CREATED' then 'NONE'
    when event_type in (
      'PRODUCTION_COMPLETED',
      'MATERIAL_CONSUMED',
      'MAINTENANCE_COMPLETED',
      'QUALITY_DEFECT_DETECTED',
      'EMERGENCY_PURCHASE_REQUESTED',
      'QUALITY_CLAIM_CHARGED',
      'CORPORATE_CARD_USED',
      'VENDOR_PAYMENT_REQUESTED'
    ) then 'LEDGER'
    else 'UNKNOWN'
  end,
  routing_status = case
    when event_type = 'SHIPMENT_HOLD_CREATED' then 'ROUTE_SKIPPED'
    else 'ROUTED'
  end
where target_service is null;

create index if not exists nexus_outbox_event_target_idx
  on nexus_outbox_event(target_service, status, created_at);

create index if not exists nexus_outbox_event_routing_status_idx
  on nexus_outbox_event(routing_status, created_at);
