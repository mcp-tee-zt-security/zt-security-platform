use crate::{config::FailMode, evaluator, evidence, identity, metrics::LIMITS_US,
    models::{Decision, EvalRequest, EvalResponse}, state::AppState};
use axum::{body::Bytes, extract::{DefaultBodyLimit, Request, State},
    http::{header, HeaderMap, Method, StatusCode}, middleware::{self, Next},
    response::{IntoResponse, Response}, routing::{get, post}, Json, Router};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{sync::atomic::Ordering, time::Instant};
use tower_http::cors::CorsLayer;

pub fn router(state: AppState) -> Router {
    let cors = match &state.0.config.cors_origins {
        None => CorsLayer::permissive(),
        Some(origins) => CorsLayer::new().allow_origin(origins.clone())
            .allow_methods([Method::GET, Method::POST])
            .allow_headers([header::CONTENT_TYPE,
                header::HeaderName::from_static("x-api-key"),
                header::HeaderName::from_static("x-tenant-id"),
                header::HeaderName::from_static("x-workspace-id")]),
    };
    Router::new().route("/health", get(health)).route("/ready", get(ready))
        .route("/metrics", get(metrics)).route("/v1/fast/evaluate", post(evaluate))
        .route("/v1/fast/policy-bundle", get(bundle)).route("/v1/fast/stats", get(stats))
        .route("/v1/fast/identity", get(workload_identity)).route("/v1/fast/attestation", get(attestation))
        .layer(DefaultBodyLimit::max(state.0.config.max_request_bytes))
        .layer(middleware::from_fn_with_state(state.clone(), authorize))
        .layer(cors).with_state(state)
}

fn error(status: StatusCode, code: &str, message: &str) -> Response {
    (status, Json(json!({"error": code, "message": message}))).into_response()
}
async fn authorize(State(state): State<AppState>, request: Request, next: Next) -> Response {
    let path = request.uri().path();
    if path == "/v1/fast/evaluate" && request.method() == Method::POST {
        state.0.stats.requests.fetch_add(1, Ordering::Relaxed);
    }
    if path == "/health" || path == "/ready" { return next.run(request).await; }
    let headers = request.headers();
    if state.0.config.require_api_key {
        let expected = state.0.config.incoming_api_key.as_deref().unwrap_or("");
        let supplied = headers.get("x-api-key").map(|v| v.as_bytes()).unwrap_or_default();
        let a = Sha256::digest(expected.as_bytes());
        let b = Sha256::digest(supplied);
        let difference = a.iter().zip(b.iter()).fold(0u8, |difference, (a, b)| difference | (a ^ b));
        if difference != 0 {
            state.0.stats.auth_failures.fetch_add(1, Ordering::Relaxed);
            return error(StatusCode::UNAUTHORIZED, "INVALID_API_KEY", "A valid data-plane API key is required");
        }
    }
    for (name, expected) in [("x-tenant-id", Some(state.0.config.tenant_id.as_str())),
        ("x-workspace-id", state.0.config.workspace_id.as_deref())] {
        if let Some(value) = headers.get(name) {
            if value.to_str().ok() != expected {
                state.0.stats.auth_failures.fetch_add(1, Ordering::Relaxed);
                return error(StatusCode::FORBIDDEN, "SCOPE_MISMATCH", "Request scope does not match this data plane");
            }
        }
    }
    next.run(request).await
}
async fn health(State(state): State<AppState>) -> Json<serde_json::Value> {
    Json(json!({"status":"UP", "engine":"rust-fast-path",
        "bundle_available": state.snapshot().is_some(), "canonical_version": 2}))
}
async fn ready(State(state): State<AppState>) -> Response {
    let snapshot = state.snapshot();
    let readiness = state.readiness(snapshot.as_deref());
    let status = if readiness.ready { StatusCode::OK } else { StatusCode::SERVICE_UNAVAILABLE };
    (status, Json(readiness)).into_response()
}
async fn bundle(State(state): State<AppState>) -> Response {
    match state.snapshot() {
        Some(snapshot) => Json(&snapshot.bundle).into_response(),
        None => error(StatusCode::SERVICE_UNAVAILABLE, "BUNDLE_UNAVAILABLE", "No validated bundle is available"),
    }
}
async fn stats(State(state): State<AppState>) -> Json<serde_json::Value> {
    let snapshot = state.snapshot();
    let mut value = serde_json::to_value(state.0.stats.snapshot()).expect("numeric stats serialize");
    let object = value.as_object_mut().expect("stats is an object");
    object.insert("bundle_version".into(), json!(snapshot.as_ref().map(|s| &s.bundle.version)));
    object.insert("bundle_age_seconds".into(), json!(snapshot.as_ref().map(|s| s.age_seconds())));
    object.insert("bundle_signature_valid".into(), json!(snapshot.as_ref().is_some_and(|s| s.signature_verified())));
    object.insert("signature_state".into(), json!(snapshot.as_ref().map(|s| s.signature_state)));
    object.insert("fail_mode".into(), json!(state.0.config.fail_mode.label()));
    object.insert("refresh".into(), json!(state.0.refresh.read().clone()));
    object.insert("readiness".into(), json!(state.readiness(snapshot.as_deref())));
    Json(value)
}
async fn workload_identity(headers: HeaderMap) -> Json<identity::IdentityReport> {
    Json(identity::report(&headers))
}
async fn attestation(State(state): State<AppState>) -> Json<crate::models::AttestationState> {
    Json(state.0.attestation.clone())
}
async fn evaluate(State(state): State<AppState>, headers: HeaderMap, body: Bytes) -> Response {
    let start = Instant::now();
    let content_type = headers.get(header::CONTENT_TYPE).and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(';').next()).unwrap_or("").trim();
    if !content_type.eq_ignore_ascii_case("application/json") {
        state.0.stats.invalid_requests.fetch_add(1, Ordering::Relaxed);
        return error(StatusCode::UNSUPPORTED_MEDIA_TYPE, "JSON_REQUIRED", "Content-Type must be application/json");
    }
    let request: EvalRequest = match serde_json::from_slice::<EvalRequest>(&body) {
        Ok(request) if request.valid() => request,
        _ => {
            state.0.stats.invalid_requests.fetch_add(1, Ordering::Relaxed);
            return error(StatusCode::BAD_REQUEST, "INVALID_EVALUATION_REQUEST", "Principal, action and resource must be valid");
        }
    };
    let snapshot = state.snapshot();
    let readiness = state.readiness(snapshot.as_deref());
    let (decision, reason, reason_code, policy, degraded) = if readiness.ready {
        let evaluation = evaluator::evaluate(snapshot.as_deref().expect("ready snapshot"), &request);
        (evaluation.decision, evaluation.reason, evaluation.reason_code, evaluation.policy, false)
    } else if readiness.availability_failure && state.0.config.fail_mode == FailMode::Open {
        (Decision::Allow, "Allowed in configured degraded availability mode", "FAIL_OPEN_AVAILABILITY", None, true)
    } else {
        (Decision::Deny, readiness.reason, readiness.reason_code, None, true)
    };
    let version = snapshot.as_ref().map(|s| s.bundle.version.clone()).unwrap_or_else(|| "unavailable".into());
    let hash = snapshot.as_ref().and_then(|s| s.bundle.bundle_hash.clone());
    let reported_identity = identity::reported_identity(&headers);
    let evidence = evidence::sign(&state, &body, decision, policy.clone(), &version,
        hash.clone(), reported_identity.clone(), degraded);
    let latency_us = start.elapsed().as_micros();
    state.0.stats.record(latency_us.min(u64::MAX as u128) as u64, decision);
    Json(EvalResponse { decision, reason: reason.into(), reason_code, policy,
        engine: "rust-fast-path", evaluation_scope: "POLICY_ONLY", bundle_version: version,
        latency_us, bundle_age_seconds: snapshot.as_ref().map(|s| s.age_seconds()),
        bundle_hash: hash, workload_identity: reported_identity, identity_authenticated: false,
        degraded, evidence }).into_response()
}
async fn metrics(State(state): State<AppState>) -> Response {
    use std::fmt::Write;
    let stats = state.0.stats.snapshot();
    let snapshot = state.snapshot();
    let ready = state.readiness(snapshot.as_deref()).ready;
    let refresh = state.0.refresh.read().clone();
    let mut text = String::new();
    for (name, value) in [
        ("zt_dp_requests_total", stats.requests), ("zt_dp_errors_total", stats.errors),
        ("zt_dp_invalid_requests_total", stats.invalid_requests), ("zt_dp_auth_failures_total", stats.auth_failures),
        ("zt_dp_allow_total", stats.allow), ("zt_dp_deny_total", stats.deny),
        ("zt_dp_step_up_total", stats.step_up), ("zt_dp_defer_total", stats.defer),
        ("zt_dp_refresh_attempts_total", refresh.attempts), ("zt_dp_refresh_successes_total", refresh.successes),
        ("zt_dp_refresh_failures_total", refresh.failures), ("zt_dp_refresh_rejections_total", refresh.rejections),
    ] { let _ = writeln!(text, "# TYPE {name} counter\n{name} {value}"); }
    for (name, value) in [
        ("zt_dp_ready", ready as u64), ("zt_dp_bundle_available", snapshot.is_some() as u64),
        ("zt_dp_bundle_signature_valid", snapshot.as_ref().is_some_and(|s| s.signature_verified()) as u64),
        ("zt_dp_bundle_age_seconds", snapshot.as_ref().map(|s| s.age_seconds()).unwrap_or(0)),
        ("zt_dp_p99_us", stats.p99_us), ("zt_dp_max_us", stats.max_us),
        ("zt_dp_refresh_consecutive_failures", refresh.consecutive_failures),
    ] { let _ = writeln!(text, "# TYPE {name} gauge\n{name} {value}"); }
    text.push_str("# TYPE zt_dp_evaluation_duration_seconds histogram\n");
    let mut cumulative = 0u64;
    for (i, count) in stats.buckets.iter().enumerate() {
        cumulative += count;
        let bound = LIMITS_US.get(i).map(|v| (*v as f64 / 1_000_000.0).to_string()).unwrap_or("+Inf".into());
        let _ = writeln!(text, "zt_dp_evaluation_duration_seconds_bucket{{le=\"{bound}\"}} {cumulative}");
    }
    let _ = writeln!(text, "zt_dp_evaluation_duration_seconds_sum {}\nzt_dp_evaluation_duration_seconds_count {}",
        stats.sum_us as f64 / 1_000_000.0, cumulative);
    ([(header::CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8")], text).into_response()
}
