# Evidence Integrity Runbook

Any unexplained signing or evidence error is treated as a security incident.

- Verify evidence key ID and key availability.
- Verify KMS/HSM or external signer health.
- Preserve failed request IDs and hashes.
- Do not disable evidence signing to restore traffic.
- If evidence integrity cannot be guaranteed, use the configured fail-closed posture.
