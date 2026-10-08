package com.zt.security.policy;

import com.zt.security.common.TenantSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.MessageDigest;

/**
 * Control-plane policy distribution endpoint for the high-performance data plane.
 * Only policies that are safe for the conservative Rust fast path are marked fastPath=true.
 * Policies containing conditions are deliberately deferred to the full Spring evaluator.
 */
@RestController
@RequestMapping("/v1/policy-bundles")
public class PolicyBundleController {
    private final PolicyService policies;
    private final TenantSession tenantSession;

    PolicyBundleController(PolicyService policies, TenantSession tenantSession) {
        this.policies = policies;
        this.tenantSession = tenantSession;
    }

    @GetMapping("/fast")
    @Transactional(readOnly = true)
    public Bundle fast(@RequestHeader("X-Tenant-Id") UUID tenant) {
        tenantSession.set(tenant);
        List<FastPolicy> out = new ArrayList<>();
        for (Policy p : policies.active(tenant)) {
            PolicyDsl d;
            try {
                d = PolicyDsl.parse(p.getPolicyText());
            }
            catch (RuntimeException ex) {
                continue;
            }
            boolean supported = d.condition() == null && d.rules().stream().allMatch(r ->
                    Set.of("==", "in").contains(r.op()));
            String principal = null, resource = null;
            List<String> actions = new ArrayList<>();
            for (PolicyDsl.Rule r : d.rules()) {
                if ("principal.type".equals(r.left()) && "==".equals(r.op())) principal = strip(r.right());
                if ("resource.type".equals(r.left()) && "==".equals(r.op())) resource = strip(r.right());
                if ("action".equals(r.left())) {
                    if ("==".equals(r.op())) actions.add(strip(r.right()));
                    else if ("in".equals(r.op())) actions.addAll(list(r.right()));
                }
            }
            boolean usable = supported && principal != null && resource != null && !actions.isEmpty();
            out.add(new FastPolicy(p.getName(), p.getVersion(), p.getPriority(), p.getEffect(), principal,
                    actions, resource, usable));
        }
        out.sort(Comparator.comparingInt(FastPolicy::priority));
        String generatedAt = Instant.now().toString();
        Bundle unsigned = new Bundle("3.2.0", generatedAt, null, env("ZT_POLICY_SIGNING_KEY_ID",
        "dev-ed25519"), null, out);
        String hash = hash(unsigned);
        Bundle hashed = new Bundle(unsigned.version(), unsigned.generatedAt(),
        null, unsigned.keyId(), hash, unsigned.policies());
        return new Bundle(hashed.version(), hashed.generatedAt(), sign(hashed),
        hashed.keyId(), hashed.bundleHash(), hashed.policies());
    }

    private static String strip(String s) {
        return s == null ? "" : s.trim().replaceAll("^\\\"|\\\"$", "");
    }
    private static List<String> list(String s) {
        String x = s == null ? "" : s.trim();
        if (x.startsWith("[") && x.endsWith("]")) x = x.substring(1, x.length()-1);
        if (x.isBlank()) return List.of();
        return Arrays.stream(x.split(",")).map(PolicyBundleController::strip).filter(v -> !v.isBlank()).toList();
    }

    public record Bundle(String version, String generatedAt, String signature,
    String keyId, String bundleHash, List<FastPolicy> policies) {
    }

    private static String hash(Bundle b) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(b).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to hash policy bundle", e);
        }
    }

    private static String sign(Bundle b) {
        String encoded = System.getenv("ZT_POLICY_SIGNING_PRIVATE_KEY_B64");
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(canonical(b).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to sign policy bundle", e);
        }
    }

    private static String canonical(Bundle b) {
        List<FastPolicy> sorted = new ArrayList<>(b.policies());
        sorted.sort(Comparator.comparingInt(FastPolicy::priority).thenComparing(FastPolicy::name).thenComparingInt(FastPolicy::version));
        StringBuilder out = new StringBuilder("version=").append(b.version()).append("\ngenerated_at=").
        append(b.generatedAt()).append("\n");
        for (FastPolicy p : sorted) {
            out.append(p.name()).append('|').append(p.version()).append('|').append(p.priority()).append('|')
               .append(p.effect()).append('|').append(p.principalType() == null ? "" : p.principalType()).append('|')
               .append(String.join(",", p.actions())).append('|').append(p.resourceType() ==
               null ? "" : p.resourceType()).append('|')
               .append("").append('|').append("\n");
        }
        return out.toString();
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
    public record FastPolicy(String name, int version, int priority, String effect,
                              String principalType, List<String> actions, String resourceType,
                              boolean fastPath) {
                              }
}
