# Enterprise SSO

4.75 supports enterprise SSO integration patterns for Keycloak, Okta, Microsoft Entra ID and Ping Identity.

## Preflight requirements

Verify:

1. issuer and audience
2. signing keys/JWKS availability
3. required claims
4. group-to-role mapping
5. tenant/organization mapping
6. controlled test authentication
7. failure behavior and token expiry

Provider-specific configuration examples remain under `docs/enterprise/sso/`.

SSO authenticates an identity; it does not replace capability, policy, approval or execution governance.
