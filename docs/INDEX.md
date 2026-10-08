# Documentation Index — Zero Trust Security Platform 4.75.0 R2

This documentation describes the **4.75.0 Revision 2** codebase. It is intentionally focused on the current platform rather than preserving historical release-note documents inside the source tree.

## Start here

1. [Architecture](architecture/4.75_ARCHITECTURE.md)
2. [Security and governance model](security/4.75_SECURITY_MODEL.md)
3. [Backend and API](api/4.75_BACKEND_API.md)
4. [Developer guide](development/4.75_DEVELOPER_GUIDE.md)
5. [SDK guide](development/4.75_SDK.md)
6. [Observability](operations/4.75_OBSERVABILITY.md)
7. [Operations](operations/4.75_OPERATIONS.md)
8. [Kubernetes](platform/4.75_KUBERNETES.md)
9. [Federation](platform/4.75_FEDERATION.md)
10. [Simulation](platform/4.75_SIMULATION.md)
11. [Compliance evidence](compliance/enterprise-compliance-mapping.md)
12. [3.x → 4.x migration](migration/3.X_TO_4.0_MIGRATION.md)

## Current implementation boundaries

- The single Spring Boot backend is apps/authorization-api; it includes the integrated SDK governance lifecycle.
- The canonical SDK contracts live under `sdk/contracts/`.
- Python, TypeScript, Java and Go SDKs consume the same contract model.
- Kubernetes policy integration lives under `k8s/operator/` and the SDK Kubernetes contract.
- Existing 3.x/4.0 application modules remain in the repository and are not removed by the 4.75 R2 documentation cleanup.
- Documentation does not claim that every legacy 3.x feature has been rewritten as a 4.75 service.

## Evidence and certification wording

Documentation describes implementation and evidence coverage only. It does not constitute ISO, SOC 2, ISMS-P, or other legal certification.
