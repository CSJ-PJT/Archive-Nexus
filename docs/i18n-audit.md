# i18n Audit

## Scope

Archive-Nexus frontend now uses a lightweight React i18n context without adding a new runtime dependency. The existing globe language selector is connected to the shared provider and updates all primary user-facing UI labels immediately.

## Supported locales

| Locale | Display name | Notes |
| --- | --- | --- |
| `ko` | 한국어 | Default and fallback locale |
| `en` | English | Operations-console wording |
| `ja` | 日本語 | Short technical UI labels |
| `zh-CN` | 简体中文 | Simplified Chinese |

## Processed screens and components

- Global shell, sidebar, topbar, simulator controls
- Dashboard metrics and overview cards
- Factory control and active factory cards
- Production, Inventory, Quality, Maintenance, Logistics, RPA data panels
- Settings, platform contract, persistence, ArchiveOS status, interaction table
- Manufacturing AI query, result, evidence, recommendation, history table
- Task Operations create/list/detail/log panels
- API timeout and HTTP status error messages shown by the frontend

## Translation files

- `frontend/src/i18n/index.tsx`
- `frontend/src/i18n/types.ts`
- `frontend/src/i18n/ko.ts`
- `frontend/src/i18n/en.ts`
- `frontend/src/i18n/ja.ts`
- `frontend/src/i18n/zh-CN.ts`

All locale files currently contain the same 201 keys. Missing keys fall back to `ko` and finally to the key name.

## Locale storage

- Primary localStorage key: `archive.locale`
- Legacy compatibility key: `archive-nexus-language`
- Unsupported or unknown locale values fall back to `ko`.

## Deliberately not translated

The following remain as original values because they are system identifiers or domain data, not UI copy:

- API paths such as `/api/outbox/summary`
- `eventType` values such as `LOGISTICS_DISPATCHED`
- enum/status source values such as `APPROVAL_REQUIRED`, `SETTLEMENT_READY`
- repository and product names such as `ArchiveOS`, `Archive-Nexus`, `Archive-Logistics`, `Archive-Ledger`
- IDs, correlation IDs, workflow IDs, factory IDs, task IDs
- backend-provided domain payload text such as alert messages, recommendations, evidence text, manifest summaries, and task logs
- internal compatibility values such as `LOGITICS`, `logitics`, and `ARCHIVE_INTEGRATIONS_LOGITICS_*`

Status and enum values may be shown with localized labels in badges while preserving the raw value as the underlying data and tooltip.

## Remaining hardcoded text

- Test fixtures contain expected Korean strings for the default `ko` locale.
- Backend-provided strings are displayed as data. They are not translated in the frontend to avoid altering API contracts or synthetic domain payload meaning.
- Proper nouns and operation names such as `Manufacturing AI`, `RPA`, `PostgreSQL`, `Platform Contract`, `Batch Snapshot`, `Workflow`, `Evidence`, and `Recommendation` remain in English where they are product/system terminology.

## Verification notes

- Translation key parity was checked across `ko`, `en`, `ja`, and `zh-CN`.
- Frontend test covers locale switch persistence through `archive.locale`.
- Manual UI checklist is available in `docs/i18n-manual-checklist.md`.

## Future improvements

- Add screenshot automation for the four language states if a browser capture pipeline is available.
- Add a CI script that fails on locale key mismatch.
- Introduce backend error code mapping if future APIs return structured `errorCode` fields.

