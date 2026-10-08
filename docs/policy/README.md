# Policy DSL reference

This directory contains reference material, not runtime policy storage.

- `grammar/Policy.g4`: ANTLR grammar reference. The backend currently parses policies with `PolicyDsl.java`; it does not load this file.
- `examples/banking.policy`: policy DSL examples for high-value transfers and data export protection. The `---` line separates the examples; submit each policy separately to the API.

Policies authored in the dashboard's Policy Studio are validated and saved through `/v1/policies` APIs in the database's `policies` table. Dashboard templates are defined in `dashboard/web/src/pages/PolicyStudio.tsx` and are not loaded from these examples.
