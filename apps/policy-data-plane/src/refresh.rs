use crate::{models::Bundle, state::AppState};
use std::{io, path::Path, time::{Duration, SystemTime, UNIX_EPOCH}};
use tokio::{io::{AsyncReadExt, AsyncWriteExt}, sync::watch};

struct FetchError { code: &'static str, message: &'static str, rejected: bool }

async fn fetch(state: &AppState) -> Result<Bundle, FetchError> {
    let error = |code, message, rejected| FetchError { code, message, rejected };
    let url = format!("{}/v1/policy-bundles/fast", state.0.config.control_plane.trim_end_matches('/'));
    let mut response = state.0.client.get(url).send().await
        .map_err(|_| error("CONTROL_PLANE_UNAVAILABLE", "Control-plane request failed", false))?;
    if !response.status().is_success() {
        let status = response.status();
        let rejected = status.is_client_error()
            && status != reqwest::StatusCode::REQUEST_TIMEOUT
            && status != reqwest::StatusCode::TOO_MANY_REQUESTS;
        return Err(error("CONTROL_PLANE_HTTP_ERROR", "Control plane returned an unsuccessful HTTP status", rejected));
    }
    let limit = state.0.config.max_bundle_bytes;
    if response.content_length().is_some_and(|size| size > limit as u64) {
        return Err(error("BUNDLE_TOO_LARGE", "Bundle exceeds the configured size limit", true));
    }
    let mut body = Vec::new();
    while let Some(chunk) = response.chunk().await
        .map_err(|_| error("CONTROL_PLANE_BODY_ERROR", "Control-plane response body was interrupted", false))? {
        if chunk.len() > limit.saturating_sub(body.len()) {
            return Err(error("BUNDLE_TOO_LARGE", "Bundle exceeds the configured size limit", true));
        }
        body.extend_from_slice(&chunk);
    }
    serde_json::from_slice(&body)
        .map_err(|_| error("BUNDLE_INVALID_JSON", "Control plane returned an invalid bundle document", true))
}

pub async fn refresh_once(state: &AppState) -> bool {
    {
        let mut status = state.0.refresh.write();
        status.attempts += 1;
        status.last_attempt_at = Some(chrono::Utc::now().to_rfc3339());
    }
    match fetch(state).await {
        Ok(bundle) => match state.accept(bundle, "control-plane") {
            Ok(accepted) => {
                if let Some(path) = &state.0.config.cache_path {
                    let result = persist(path, &accepted.bundle).await;
                    state.0.refresh.write().cache_error = result.err().map(|_| "Unable to persist accepted bundle cache".into());
                }
                true
            }
            Err(error) => {
                state.failure(error.code, error.message, true);
                tracing::warn!(code = error.code, "Rejected policy bundle; retaining last accepted snapshot");
                false
            }
        },
        Err(error) => {
            state.failure(error.code, error.message, error.rejected);
            tracing::warn!(code = error.code, "Policy refresh failed; retaining last accepted snapshot");
            false
        }
    }
}

pub async fn run(state: AppState, mut stop: watch::Receiver<bool>) {
    // Four bounded attempts per cycle; no sleep after the final failed attempt.
    loop {
        for attempt in 0..4 {
            if *stop.borrow() { return; }
            let success = tokio::select! {
                changed = stop.changed() => { let _ = changed; return; }
                success = refresh_once(&state) => success,
            };
            if success { break; }
            if attempt < 3 {
                tokio::select! {
                    changed = stop.changed() => { let _ = changed; return; }
                    _ = tokio::time::sleep(Duration::from_secs(1 << attempt)) => {}
                }
            }
        }
        tokio::select! {
            changed = stop.changed() => { let _ = changed; return; }
            _ = tokio::time::sleep(state.0.config.refresh_interval) => {}
        }
    }
}

pub async fn load_cache(state: &AppState) {
    let Some(path) = &state.0.config.cache_path else { return; };
    let file = match tokio::fs::File::open(path).await {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return,
        Err(_) => {
            state.0.refresh.write().cache_error = Some("Unable to read bundle cache".into());
            return;
        }
    };
    let mut body = Vec::new();
    let result = file.take(state.0.config.max_bundle_bytes as u64 + 1).read_to_end(&mut body).await;
    if result.is_err() {
        state.0.refresh.write().cache_error = Some("Unable to read bundle cache".into());
        return;
    }
    if body.len() > state.0.config.max_bundle_bytes {
        state.failure("CACHE_TOO_LARGE", "Cached bundle exceeds the size limit", true);
        return;
    }
    match serde_json::from_slice::<Bundle>(&body) {
        Ok(bundle) => if let Err(error) = state.accept(bundle, "local-cache") {
            state.failure(error.code, error.message, true);
        },
        Err(_) => state.failure("CACHE_INVALID_JSON", "Cached bundle is not a bundle document", true),
    }
}

async fn persist(path: &Path, bundle: &Bundle) -> io::Result<()> {
    if let Some(parent) = path.parent().filter(|p| !p.as_os_str().is_empty()) {
        tokio::fs::create_dir_all(parent).await?;
    }
    let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos();
    let temporary = path.with_extension(format!("tmp-{}-{nonce}", std::process::id()));
    let bytes = serde_json::to_vec(bundle).map_err(io::Error::other)?;
    let result = async {
        let mut file = tokio::fs::OpenOptions::new().write(true).create_new(true).open(&temporary).await?;
        file.write_all(&bytes).await?;
        file.sync_all().await?;
        drop(file);
        // Same-directory rename: readers see a complete old or new document.
        tokio::fs::rename(&temporary, path).await
    }.await;
    if result.is_err() { let _ = tokio::fs::remove_file(&temporary).await; }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{canonical, config::Config};

    #[tokio::test]
    async fn cached_bundle_is_revalidated_instead_of_becoming_implicitly_trusted() {
        let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        let path = std::env::temp_dir().join(format!("zt-cache-{}-{nonce}.json", std::process::id()));
        let mut config = Config::from_lookup(|_| Ok(None)).unwrap();
        config.cache_path = Some(path.clone());
        let mut bundle = Bundle { canonical_version: 2, version: String::new(),
            tenant_id: config.tenant_id.clone(), workspace_id: None,
            generated_at: chrono::Utc::now().to_rfc3339(), signature: None,
            key_id: None, bundle_hash: None, policies: Vec::new() };
        bundle.version = canonical::revision(&bundle);
        bundle.bundle_hash = Some(canonical::hash(&canonical::payload(&bundle)));
        persist(&path, &bundle).await.unwrap();
        let state = AppState::new(config.clone()).unwrap();
        load_cache(&state).await;
        assert_eq!(state.snapshot().unwrap().bundle.version, bundle.version);
        bundle.bundle_hash = Some("corrupted".into());
        tokio::fs::write(&path, serde_json::to_vec(&bundle).unwrap()).await.unwrap();
        let fresh = AppState::new(config).unwrap();
        load_cache(&fresh).await;
        assert!(fresh.snapshot().is_none());
        assert!(!fresh.readiness(None).availability_failure);
        tokio::fs::remove_file(&path).await.unwrap();
    }
}
