-- P0 recovery: simulator_state JSON columns are PostgreSQL TEXT, never LOB/OID references.
-- This migration intentionally retains all large objects. Their deletion is a separate, gated operation.
DO $$
DECLARE
    column_name text;
    column_value text;
    large_object_oid oid;
    json_columns constant text[] := ARRAY[
        'factories_json',
        'sensor_metrics_json',
        'production_orders_json',
        'lots_json',
        'quality_inspections_json',
        'inventory_items_json',
        'inventory_transactions_json',
        'logistics_shipments_json',
        'maintenance_events_json',
        'alerts_json',
        'rpa_tasks_json',
        'batch_snapshots_json',
        'archiveos_interactions_json'
    ];
BEGIN
    -- A clean database, or one without the runtime row, has nothing to recover.
    IF NOT EXISTS (
        SELECT 1 FROM simulator_state WHERE id = 'archive-nexus-runtime'
    ) THEN
        RETURN;
    END IF;

    FOREACH column_name IN ARRAY json_columns LOOP
        EXECUTE format(
            'SELECT %1$I FROM simulator_state WHERE id = ''archive-nexus-runtime''',
            column_name
        ) INTO column_value;

        -- Only a numeric-only value is a legacy OID candidate. Existing JSON text remains untouched.
        IF column_value ~ '^[0-9]+$' THEN
            BEGIN
                large_object_oid := column_value::oid;
            EXCEPTION WHEN OTHERS THEN
                RAISE EXCEPTION 'Invalid legacy large object OID in simulator_state.%: %',
                    column_name, column_value;
            END;

            IF NOT EXISTS (
                SELECT 1 FROM pg_largeobject_metadata WHERE oid = large_object_oid
            ) THEN
                RAISE EXCEPTION 'Missing legacy large object % referenced by simulator_state.%',
                    large_object_oid, column_name;
            END IF;

            EXECUTE format(
                'UPDATE simulator_state SET %1$I = convert_from(lo_get(%1$I::oid), ''UTF8'') WHERE id = ''archive-nexus-runtime''',
                column_name
            );
        END IF;

        -- Validate every resulting payload without logging its content.
        EXECUTE format(
            'SELECT %1$I::jsonb FROM simulator_state WHERE id = ''archive-nexus-runtime''',
            column_name
        );
    END LOOP;

    UPDATE simulator_state
    SET running = false
    WHERE id = 'archive-nexus-runtime';

    IF to_regclass('simulator_control_state') IS NOT NULL THEN
        UPDATE simulator_control_state
        SET running = false
        WHERE id = 'archive-nexus-runtime';
    END IF;
END $$;
