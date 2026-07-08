# ArchiveOS Workflow Integration Token

Archive Nexus creates ArchiveOS workflows, synchronizes approval state, and sends action result callbacks through ArchiveOS CUD APIs.

The backend sends the shared local environment value as:

```http
X-ArchiveOS-Integration-Token: ${ARCHIVEOS_INTEGRATION_TOKEN}
```

Configure the same token in both ArchiveOS and Archive Nexus local `.env` files. Do not commit the value.

If the token is missing or mismatched, Nexus keeps the task in a retryable state while manufacturing data and dashboards remain readable.
