# i18n Manual Checklist

Use this checklist when the frontend is running on `http://localhost:15173`.

## Common checks

1. Open the app and confirm the default language is Korean.
2. Use the top-right globe selector.
3. Switch to each language:
   - 한국어
   - English
   - 日本語
   - 简体中文
4. Confirm the selected language remains after browser refresh.
5. Confirm `localStorage.archive.locale` stores `ko`, `en`, `ja`, or `zh-CN`.
6. Confirm unsupported values fall back to Korean.

## Screens to inspect

| Screen | Expected result |
| --- | --- |
| Overview | Sidebar, dashboard cards, metric labels, empty states switch language |
| Tasks | Create task form, task list, detail labels, action buttons switch language |
| Manufacturing AI | Form labels, submit/loading text, result headings, history table switch language |
| Factories | Factory control form and active factory labels switch language |
| Production | Panel title and table headers switch language |
| Inventory | Panel titles and table headers switch language |
| Quality | Panel title and table headers switch language |
| Maintenance | Panel title and table headers switch language |
| Logistics | Panel title and table headers switch language |
| RPA | Empty state and action buttons switch language |
| Settings | Platform contract, persistence, ArchiveOS status, interaction table labels switch language |

## Non-translated values to confirm

- API URLs are not shown as translated text.
- Event and enum source values remain unchanged in payload data.
- Product names remain `ArchiveOS`, `Archive-Nexus`, `Archive-Logistics`, and `Archive-Ledger`.
- Factory IDs, correlation IDs, workflow IDs, task IDs, and trace IDs remain original.

## Optional screenshots

If screenshot capture is available, save:

- `docs/screenshots/i18n-ko.png`
- `docs/screenshots/i18n-en.png`
- `docs/screenshots/i18n-ja.png`
- `docs/screenshots/i18n-zh-CN.png`

