use base64::{engine::general_purpose::STANDARD, Engine};
use chrono::{Duration, Utc};
use ed25519_dalek::{Signer, SigningKey};
use std::sync::Arc;
use zt_policy_data_plane::{bundle, canonical, config::Config, evaluator,
    models::{Action, Bundle, Decision, EvalRequest, FastPolicy, Principal, Resource}, state::AppState};

fn config(key: Option<&SigningKey>) -> Config {
    let encoded = key.map(|key| STANDARD.encode(key.verifying_key().to_bytes()));
    Config::from_lookup(|name| Ok(if name == "ZT_POLICY_PUBLIC_KEY_B64" { encoded.clone() } else { None })).unwrap()
}
fn policy(name: &str, effect: &str, fast_path: bool) -> FastPolicy {
    FastPolicy { name: name.into(), version: 1, priority: 10, effect: effect.into(),
        principal_type: Some("AI_AGENT".into()), actions: vec!["payment.transfer".into()],
        resource_type: Some("bank_account".into()), condition: None, fast_path }
}
fn candidate(config: &Config, policies: Vec<FastPolicy>, time: chrono::DateTime<Utc>) -> Bundle {
    let mut bundle = Bundle { canonical_version: 2, version: String::new(),
        tenant_id: config.tenant_id.clone(), workspace_id: config.workspace_id.clone(),
        generated_at: time.to_rfc3339(), signature: None, key_id: Some("test-key".into()),
        bundle_hash: None, policies };
    rehash(&mut bundle);
    bundle
}
fn rehash(bundle: &mut Bundle) {
    bundle.version = canonical::revision(bundle);
    bundle.bundle_hash = Some(canonical::hash(&canonical::payload(bundle)));
}
fn sign(bundle: &mut Bundle, key: &SigningKey) {
    bundle.signature = Some(STANDARD.encode(key.sign(&canonical::payload(bundle)).to_bytes()));
}
fn request() -> EvalRequest {
    EvalRequest { principal: Principal { id: "agent-1".into(), kind: "AI_AGENT".into() },
        action: Action { name: "payment.transfer".into() },
        resource: Resource { kind: "bank_account".into(), id: "account-1".into() },
        context: Default::default() }
}

#[test]
fn fast_path_flag_cannot_be_changed_under_an_existing_signature() {
    let key = SigningKey::from_bytes(&[7; 32]);
    let config = config(Some(&key));
    let now = Utc::now();
    let mut bundle = candidate(&config, vec![policy("allow", "allow", false)], now);
    sign(&mut bundle, &key);
    assert!(bundle::validate(bundle.clone(), &config, now).is_ok());
    bundle.policies[0].fast_path = true;
    rehash(&mut bundle); // Even rewriting the unsigned hash/revision cannot repair the signature.
    let rejection = bundle::validate(bundle, &config, now).err().unwrap();
    assert_eq!(rejection.code, "SIGNATURE_INVALID");
}
#[test]
fn corrupted_hash_wrong_scope_and_future_timestamp_are_rejected() {
    let config = config(None);
    let now = Utc::now();
    let original = candidate(&config, vec![policy("allow", "allow", true)], now);
    let mut corrupt = original.clone();
    corrupt.bundle_hash = Some("bad".into());
    assert_eq!(bundle::validate(corrupt, &config, now).err().unwrap().code, "BUNDLE_HASH_MISMATCH");
    let mut wrong_scope = original.clone();
    wrong_scope.workspace_id = Some("88888888-8888-8888-8888-888888888801".into());
    rehash(&mut wrong_scope);
    assert_eq!(bundle::validate(wrong_scope, &config, now).err().unwrap().code, "BUNDLE_SCOPE_MISMATCH");
    let future = candidate(&config, original.policies, now + Duration::seconds(30));
    assert_eq!(bundle::validate(future, &config, now).err().unwrap().code, "BUNDLE_TIMESTAMP_IN_FUTURE");
}
#[test]
fn unknown_policy_blocks_allow_but_supported_deny_is_definitive() {
    let config = config(None);
    let policies = vec![policy("known-allow", "allow", true), policy("unknown-deny", "deny", false)];
    let validated = bundle::validate(candidate(&config, policies.clone(), Utc::now()), &config, Utc::now()).unwrap();
    assert_eq!(evaluator::evaluate(&validated, &request()).decision, Decision::Defer);
    let mut policies = policies;
    policies.push(policy("known-deny", "deny", true));
    let validated = bundle::validate(candidate(&config, policies, Utc::now()), &config, Utc::now()).unwrap();
    assert_eq!(evaluator::evaluate(&validated, &request()).decision, Decision::Deny);
}
#[test]
fn unsupported_condition_requires_authoritative_evaluation() {
    let config = config(None);
    let mut unknown = policy("condition-deny", "deny", true);
    unknown.condition = Some("context.amount > 100 and risk.score > 70".into());
    let validated = bundle::validate(candidate(&config,
        vec![policy("allow", "allow", true), unknown], Utc::now()), &config, Utc::now()).unwrap();
    assert_eq!(evaluator::evaluate(&validated, &request()).decision, Decision::Defer);
}
#[tokio::test]
async fn rejected_update_keeps_last_good_snapshot_and_cannot_enable_fail_open() {
    let state = AppState::new(config(None)).unwrap();
    let now = Utc::now();
    let good = candidate(&state.0.config, vec![policy("allow", "allow", true)], now);
    let first = state.accept(good.clone(), "control-plane").unwrap();
    let duplicate = state.accept(good, "control-plane").unwrap();
    assert!(Arc::ptr_eq(&first, &duplicate));
    let older = candidate(&state.0.config, vec![policy("deny", "deny", true)], now - Duration::seconds(1));
    assert_eq!(state.accept(older, "control-plane").err().unwrap().code, "BUNDLE_ROLLBACK_REJECTED");
    assert!(Arc::ptr_eq(&first, &state.snapshot().unwrap()));
    state.failure("SIGNATURE_INVALID", "Rejected update", true);
    state.failure("CONTROL_PLANE_UNAVAILABLE", "Network failure", false);
    assert!(!state.readiness(None).availability_failure);
    assert!(state.readiness(Some(&first)).ready);
}
#[test]
fn java_and_rust_share_utf8_canonical_vector() {
    let bundle = Bundle { canonical_version: 2, version: "v".into(), tenant_id: "t".into(),
        workspace_id: None, generated_at: "time".into(), signature: None, key_id: None, bundle_hash: None,
        policies: vec![FastPolicy { name: "정책|x".into(), version: 1, priority: 7, effect: "allow".into(),
            principal_type: Some("P".into()), actions: vec!["a".into()], resource_type: Some("R".into()),
            condition: None, fast_path: true }] };
    let expected = "zt-policy-bundle-v2\n1:2\n1:t\n-\n1:v\n4:time\n-\n1:1\n8:정책|x\n1:1\n1:7\n5:allow\n1:P\n1:1\n1:a\n1:R\n-\n4:true\n";
    assert_eq!(canonical::payload(&bundle), expected.as_bytes());
}

#[tokio::test]
async fn evidence_verifies_over_the_returned_payload_and_binds_the_http_body() {
    use ed25519_dalek::Signature;
    use zt_policy_data_plane::evidence;
    let key = SigningKey::from_bytes(&[9; 32]);
    let mut config = config(None);
    config.evidence_key = Some(key.clone());
    let state = AppState::new(config).unwrap();
    let raw = br#"{"principal":{"id":"a","type":"AI_AGENT"}}"#;
    let signed = evidence::sign(&state, raw, Decision::Deny, Some("p".into()), "v",
        None, Some("spiffe://unverified/claim".into()), false).unwrap();
    assert_eq!(signed.request_hash, canonical::hash(raw));
    let signature: [u8; 64] = STANDARD.decode(&signed.signature).unwrap().try_into().unwrap();
    assert!(key.verifying_key().verify_strict(signed.signed_payload.as_bytes(), &Signature::from_bytes(&signature)).is_ok());
    let payload: serde_json::Value = serde_json::from_str(&signed.signed_payload).unwrap();
    assert_eq!(payload["identity_authenticated"], false);
    assert_eq!(payload["decision"], "DENY");
    assert_eq!(payload["request_hash"], signed.request_hash);
    assert_ne!(signed.request_hash, canonical::hash(br#"{ "principal":{"id":"a","type":"AI_AGENT"}}"#));
}
