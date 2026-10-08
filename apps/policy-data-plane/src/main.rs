use std::sync::atomic::Ordering;
use tokio::sync::watch;
use zt_policy_data_plane::{config::Config, refresh, routes, state::AppState};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt().with_env_filter(
        tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into())
    ).init();
    let config = Config::from_env()?;
    let address = config.bind_addr;
    let state = AppState::new(config)?;
    let listener = tokio::net::TcpListener::bind(address).await?;
    refresh::load_cache(&state).await;
    let (stop, receiver) = watch::channel(false);
    let worker = tokio::spawn(refresh::run(state.clone(), receiver));
    tracing::info!(%address, signed_bundles_required = !state.0.config.allow_unsigned,
        "Policy data plane listening");
    let shutdown_state = state.clone();
    let shutdown_sender = stop.clone();
    let result = axum::serve(listener, routes::router(state.clone()))
        .with_graceful_shutdown(async move {
            shutdown_signal().await;
            shutdown_state.0.stopping.store(true, Ordering::Release);
            let _ = shutdown_sender.send(true);
        }).await;
    state.0.stopping.store(true, Ordering::Release);
    let _ = stop.send(true);
    let _ = worker.await;
    result?;
    Ok(())
}

async fn shutdown_signal() {
    let ctrl_c = async {
        if let Err(error) = tokio::signal::ctrl_c().await {
            tracing::error!(%error, "Unable to install Ctrl-C handler");
        }
    };
    #[cfg(unix)]
    {
        match tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate()) {
            Ok(mut terminate) => { tokio::select! { _ = ctrl_c => {}, _ = terminate.recv() => {} } }
            Err(error) => {
                tracing::error!(%error, "Unable to install SIGTERM handler");
                ctrl_c.await;
            }
        }
    }
    #[cfg(not(unix))]
    ctrl_c.await;
}
