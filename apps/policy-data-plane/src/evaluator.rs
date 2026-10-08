use crate::{
    bundle::{Effect, ValidatedBundle},
    condition::Outcome,
    models::{Decision, EvalRequest},
};

pub struct Evaluation {
    pub decision: Decision,
    pub reason: &'static str,
    pub reason_code: &'static str,
    pub policy: Option<String>,
}
fn answer(decision: Decision, code: &'static str, reason: &'static str, policy: Option<String>) -> Evaluation {
    Evaluation { decision, reason, reason_code: code, policy }
}

pub fn evaluate(bundle: &ValidatedBundle, request: &EvalRequest) -> Evaluation {
    let mut deny = None;
    let mut step_up = None;
    let mut allow = None;
    let mut deferred = None;
    for compiled in &bundle.policies {
        let policy = &compiled.policy;
        if policy.principal_type.as_deref().is_some_and(|value| value != request.principal.kind)
            || policy.resource_type.as_deref().is_some_and(|value| value != request.resource.kind)
            || (!policy.actions.is_empty() && !policy.actions.iter().any(|a| a == &request.action.name)) {
            continue;
        }
        if !policy.fast_path {
            if deferred.is_none() { deferred = Some(policy.name.clone()); }
            continue;
        }
        match compiled.condition.evaluate(&request.context) {
            Outcome::NoMatch => continue,
            Outcome::Unsupported => {
                if deferred.is_none() { deferred = Some(policy.name.clone()); }
                continue;
            }
            Outcome::Match => {}
        }
        match compiled.effect {
            Effect::Deny if deny.is_none() => deny = Some(policy.name.clone()),
            Effect::StepUp if step_up.is_none() => step_up = Some(policy.name.clone()),
            Effect::Allow if allow.is_none() => allow = Some(policy.name.clone()),
            _ => {}
        }
    }
    if deny.is_some() {
        return answer(Decision::Deny, "POLICY_DENY", "Denied by a supported fast-path policy", deny);
    }
    // Unknown applicable policies could deny; never let an allow/step-up bypass them.
    if deferred.is_some() {
        return answer(Decision::Defer, "AUTHORITATIVE_EVALUATION_REQUIRED",
            "An applicable policy requires the authoritative control-plane evaluator", deferred);
    }
    if step_up.is_some() {
        return answer(Decision::StepUp, "POLICY_STEP_UP", "Additional approval required by fast-path policy", step_up);
    }
    if allow.is_some() {
        return answer(Decision::Allow, "POLICY_ALLOW", "Allowed by a supported fast-path policy", allow);
    }
    answer(Decision::Defer, "NO_FAST_POLICY_MATCH", "No supported fast-path policy matched; use the control plane", None)
}

