# Credential Rotation Runbook

See [RC Security Baseline](rc-security-baseline.md) for identity/scope assignments. This procedure contains no real secret values.

1. Create one token for each of `Archive-Market`, `Archive-Logistics`, `ArchiveOS`, `ArchiveOS-Reader`, and `Archive-Nexus-Operator`.
2. Supply tokens through the platform secret mechanism or a local ignored `.env` file.
3. Update caller and receiver during a controlled grace period, then recreate only affected containers.
4. Verify valid calls succeed and retired tokens return `401`; verify wrong source/scope returns `403`.
5. Check logs and request captures for accidental credential exposure, then revoke the old values.

Never rotate by editing committed Compose files or `.env.example`.
