use crate::models::Decision;
use serde::Serialize;
use std::sync::atomic::{AtomicU64, Ordering};

pub const LIMITS_US: [u64; 11] = [50, 100, 200, 400, 800, 1500, 3000, 5000, 10000, 20000, 50000];

pub struct Stats {
    pub requests: AtomicU64,
    pub errors: AtomicU64,
    pub invalid_requests: AtomicU64,
    pub auth_failures: AtomicU64,
    decisions: AtomicU64,
    allow: AtomicU64,
    deny: AtomicU64,
    step_up: AtomicU64,
    defer: AtomicU64,
    max_us: AtomicU64,
    sum_us: AtomicU64,
    buckets: [AtomicU64; 12],
}
impl Default for Stats {
    fn default() -> Self {
        Self {
            requests: AtomicU64::new(0), errors: AtomicU64::new(0),
            invalid_requests: AtomicU64::new(0), auth_failures: AtomicU64::new(0),
            decisions: AtomicU64::new(0), allow: AtomicU64::new(0), deny: AtomicU64::new(0),
            step_up: AtomicU64::new(0), defer: AtomicU64::new(0),
            max_us: AtomicU64::new(0), sum_us: AtomicU64::new(0),
            buckets: std::array::from_fn(|_| AtomicU64::new(0)),
        }
    }
}
#[derive(Serialize)]
pub struct Snapshot {
    pub requests: u64, pub errors: u64, pub decisions: u64,
    pub invalid_requests: u64, pub auth_failures: u64,
    pub allow: u64, pub deny: u64, pub step_up: u64, pub defer: u64,
    pub p50_us: u64, pub p95_us: u64, pub p99_us: u64, pub p999_us: u64, pub max_us: u64,
    #[serde(skip)] pub sum_us: u64,
    #[serde(skip)] pub buckets: [u64; 12],
}
impl Stats {
    pub fn record(&self, us: u64, decision: Decision) {
        let counter = match decision {
            Decision::Allow => &self.allow, Decision::Deny => &self.deny,
            Decision::StepUp => &self.step_up, Decision::Defer => &self.defer,
        };
        counter.fetch_add(1, Ordering::Relaxed);
        self.max_us.fetch_max(us, Ordering::Relaxed);
        self.sum_us.fetch_add(us, Ordering::Relaxed);
        let bucket = LIMITS_US.iter().position(|bound| us <= *bound).unwrap_or(11);
        self.buckets[bucket].fetch_add(1, Ordering::Relaxed);
        self.decisions.fetch_add(1, Ordering::Relaxed);
    }
    pub fn snapshot(&self) -> Snapshot {
        let buckets = std::array::from_fn(|i| self.buckets[i].load(Ordering::Relaxed));
        let max_us = self.max_us.load(Ordering::Relaxed);
        Snapshot {
            requests: self.requests.load(Ordering::Relaxed), errors: self.errors.load(Ordering::Relaxed),
            invalid_requests: self.invalid_requests.load(Ordering::Relaxed),
            auth_failures: self.auth_failures.load(Ordering::Relaxed),
            decisions: self.decisions.load(Ordering::Relaxed),
            allow: self.allow.load(Ordering::Relaxed), deny: self.deny.load(Ordering::Relaxed),
            step_up: self.step_up.load(Ordering::Relaxed), defer: self.defer.load(Ordering::Relaxed),
            p50_us: percentile(&buckets, max_us, 0.50), p95_us: percentile(&buckets, max_us, 0.95),
            p99_us: percentile(&buckets, max_us, 0.99), p999_us: percentile(&buckets, max_us, 0.999),
            max_us, sum_us: self.sum_us.load(Ordering::Relaxed), buckets,
        }
    }
}
fn percentile(buckets: &[u64; 12], max_us: u64, percentile: f64) -> u64 {
    let total: u64 = buckets.iter().sum();
    if total == 0 { return 0; }
    let target = ((total as f64) * percentile).ceil() as u64;
    let mut count = 0;
    for (i, value) in buckets.iter().enumerate() {
        count += value;
        if count >= target { return LIMITS_US.get(i).copied().unwrap_or(max_us).min(max_us); }
    }
    max_us
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn slow_requests_do_not_report_an_infinite_integer_percentile() {
        let stats = Stats::default();
        stats.record(120_000, Decision::Deny);
        assert_eq!(stats.snapshot().p99_us, 120_000);
        assert_eq!(stats.snapshot().deny, 1);
    }
}

