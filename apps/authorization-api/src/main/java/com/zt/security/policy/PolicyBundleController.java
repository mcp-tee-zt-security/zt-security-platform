package com.zt.security.policy;

import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/** Conservative policy projection. This endpoint does not replace full authorization. */
@RestController
@RequestMapping("/v1/policy-bundles")
public class PolicyBundleController {
    private final PolicyRepository policies;
    private final TenantSession tenantSession;
    private final WorkspaceSession workspaceSession;
    private static final Comparator<FastPolicy> ORDER = Comparator.comparingInt(FastPolicy::priority)
        .thenComparing(FastPolicy::name, (a, b) -> Arrays.compareUnsigned(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)))
        .thenComparingInt(FastPolicy::version);

    PolicyBundleController(PolicyRepository policies, TenantSession tenantSession, WorkspaceSession workspaceSession) {
        this.policies = policies;
        this.tenantSession = tenantSession;
        this.workspaceSession = workspaceSession;
    }

    @GetMapping("/fast")
    @Transactional(readOnly = true)
    public Bundle fast(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace) {
        tenantSession.set(tenant);
        workspaceSession.set(workspace);
        // Do not use the tenant-only activePolicies cache for a workspace-scoped projection.
        List<FastPolicy> out = new ArrayList<>();
        for (Policy policy : policies.findByTenantIdAndStatusOrderByPriorityAsc(tenant, "ACTIVE")) {
            if (policy.getWorkspaceId() != null && !policy.getWorkspaceId().equals(workspace)) continue;
            try {
                out.add(project(policy, PolicyDsl.parse(policy.getPolicyText())));
            } catch (PolicyDsl.PolicyParseException ex) {
                // Retain an unresolved policy rather than accidentally allowing past it.
                out.add(new FastPolicy(policy.getName(), policy.getVersion(), policy.getPriority(),
                    policy.getEffect(), null, List.of(), null, null, false));
            }
        }
        out.sort(ORDER);
        String keyId = env("ZT_POLICY_SIGNING_KEY_ID", "dev-ed25519");
        String tenantId = tenant.toString();
        String workspaceId = workspace == null ? null : workspace.toString();
        List<FastPolicy> immutable = List.copyOf(out);
        String version = revision(tenantId, workspaceId, immutable);
        Bundle unsigned = new Bundle(2, version, tenantId, workspaceId, Instant.now().toString(),
            null, keyId, null, immutable);
        return new Bundle(2, version, tenantId, workspaceId, unsigned.generatedAt(),
            sign(unsigned), keyId, hash(canonical(unsigned)), immutable);
    }

    static FastPolicy project(Policy policy, PolicyDsl dsl) {
        String principal = selector(dsl, "principal.type");
        String resource = selector(dsl, "resource.type");
        List<PolicyDsl.Rule> actionRules = dsl.rules().stream().filter(r -> "action".equals(r.left())).toList();
        List<String> actions = List.of();
        if (actionRules.size() == 1) {
            PolicyDsl.Rule rule = actionRules.get(0);
            if ("==".equals(rule.op()) && !rule.right().isBlank()) actions = List.of(rule.right());
            if ("in".equals(rule.op())) actions = actionList(rule.right());
        }
        // Duplicate or unsupported clauses never become an eligible fast policy.
        boolean eligible = dsl.condition() == null && dsl.rules().size() == 3
            && principal != null && resource != null && !actions.isEmpty();
        String condition = dsl.condition() == null ? null : dsl.condition().expression().toString();
        return new FastPolicy(policy.getName(), policy.getVersion(), policy.getPriority(),
            policy.getEffect(), principal, actions, resource, condition, eligible);
    }

    private static String selector(PolicyDsl dsl, String left) {
        List<PolicyDsl.Rule> rules = dsl.rules().stream().filter(r -> left.equals(r.left())).toList();
        if (rules.size() != 1 || !"==".equals(rules.get(0).op()) || rules.get(0).right().isBlank()) return null;
        return rules.get(0).right();
    }

    private static List<String> actionList(String right) {
        if (!right.startsWith("[") || !right.endsWith("]")) return List.of();
        String body = right.substring(1, right.length() - 1);
        if (body.isBlank()) return List.of();
        List<String> values = Arrays.stream(body.split(",", -1)).map(String::trim).toList();
        // DSL Rule currently renders lists without quoting. Limit this projection to unambiguous tokens.
        if (values.stream().anyMatch(v -> !v.matches("[A-Za-z0-9_.:-]+"))) return List.of();
        return values;
    }

    public record Bundle(int canonicalVersion, String version, String tenantId, String workspaceId,
        String generatedAt, String signature, String keyId, String bundleHash, List<FastPolicy> policies) {}
    public record FastPolicy(String name, int version, int priority, String effect,
        String principalType, List<String> actions, String resourceType, String condition, boolean fastPath) {}

    static byte[] canonical(Bundle bundle) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("zt-policy-bundle-v2\n".getBytes(StandardCharsets.UTF_8));
        field(out, Integer.toString(bundle.canonicalVersion()));
        field(out, bundle.tenantId());
        field(out, bundle.workspaceId());
        field(out, bundle.version());
        field(out, bundle.generatedAt());
        field(out, bundle.keyId());
        policyFields(out, bundle.policies());
        return out.toByteArray();
    }

    static String revision(String tenant, String workspace, List<FastPolicy> policies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("zt-policy-set-v2\n".getBytes(StandardCharsets.UTF_8));
        field(out, tenant);
        field(out, workspace);
        policyFields(out, policies);
        return "sha256:" + hash(out.toByteArray());
    }

    private static void policyFields(ByteArrayOutputStream out, List<FastPolicy> policies) {
        List<FastPolicy> sorted = new ArrayList<>(policies);
        sorted.sort(ORDER);
        field(out, Integer.toString(sorted.size()));
        for (FastPolicy policy : sorted) {
            field(out, policy.name());
            field(out, Integer.toString(policy.version()));
            field(out, Integer.toString(policy.priority()));
            field(out, policy.effect());
            field(out, policy.principalType());
            field(out, Integer.toString(policy.actions().size()));
            for (String action : policy.actions()) field(out, action);
            field(out, policy.resourceType());
            field(out, policy.condition());
            field(out, Boolean.toString(policy.fastPath()));
        }
    }

    private static void field(ByteArrayOutputStream out, String value) {
        if (value == null) {
            out.writeBytes("-\n".getBytes(StandardCharsets.UTF_8));
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeBytes((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
            out.writeBytes(bytes);
            out.write('\n');
        }
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash policy bundle", ex);
        }
    }

    private static String sign(Bundle bundle) {
        String encoded = System.getenv("ZT_POLICY_SIGNING_PRIVATE_KEY_B64");
        if (encoded == null || encoded.isBlank()) return null;
        try {
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(canonical(bundle));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to sign policy bundle", ex);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
