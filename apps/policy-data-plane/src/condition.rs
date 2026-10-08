use serde_json::Value;
use std::{cmp::Ordering, collections::HashMap};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Outcome { Match, NoMatch, Unsupported }
#[derive(Clone, Copy, Debug)]
pub enum Operator { Eq, Ne, Gt, Ge, Lt, Le }
#[derive(Clone, Debug)]
pub enum Scalar { Number(f64), Text(String), Boolean(bool) }
#[derive(Clone, Debug)]
pub enum Condition { Always, Unsupported, Compare { key: String, op: Operator, rhs: Scalar } }

pub fn compile(text: Option<&str>) -> Condition {
    let Some(text) = text.map(str::trim) else { return Condition::Always; };
    if text.is_empty() { return Condition::Unsupported; }
    let mut quoted = false;
    let mut escaped = false;
    let mut found = None;
    let mut chars = text.char_indices().peekable();
    while let Some((index, character)) = chars.next() {
        if quoted {
            if escaped { escaped = false; }
            else if character == '\\' { escaped = true; }
            else if character == '"' { quoted = false; }
            continue;
        }
        if character == '"' { quoted = true; continue; }
        let pair = chars.peek().map(|(_, next)| *next == '=').unwrap_or(false);
        let operator = match (character, pair) {
            ('=', true) => Some(Operator::Eq), ('!', true) => Some(Operator::Ne),
            ('>', true) => Some(Operator::Ge), ('<', true) => Some(Operator::Le),
            ('>', false) => Some(Operator::Gt), ('<', false) => Some(Operator::Lt),
            _ => None,
        };
        if let Some(op) = operator {
            if found.is_some() { return Condition::Unsupported; }
            if pair { chars.next(); }
            found = Some((index, if pair { 2 } else { 1 }, op));
        }
    }
    let Some((index, length, op)) = found else { return Condition::Unsupported; };
    let lhs = text[..index].trim();
    // These namespaces resolve outside context in the authoritative evaluator.
    if lhs.starts_with("principal.") || lhs.starts_with("resource.") {
        return Condition::Unsupported;
    }
    if lhs.is_empty() || !lhs.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'.') {
        return Condition::Unsupported;
    }
    let rhs = match serde_json::from_str::<Value>(text[index + length..].trim()) {
        Ok(Value::Number(value)) => match value.as_f64() {
            Some(value) if precise_number(value) => Scalar::Number(value),
            _ => return Condition::Unsupported,
        },
        Ok(Value::String(value)) => Scalar::Text(value),
        Ok(Value::Bool(value)) => Scalar::Boolean(value),
        _ => return Condition::Unsupported,
    };
    if !matches!(rhs, Scalar::Number(_)) && !matches!(op, Operator::Eq | Operator::Ne) {
        return Condition::Unsupported;
    }
    let key = lhs.strip_prefix("context.").unwrap_or(lhs).to_owned();
    if key.is_empty() || key.split('.').any(str::is_empty) { return Condition::Unsupported; }
    Condition::Compare { key, op, rhs }
}

fn precise_number(value: f64) -> bool { value.is_finite() && value.abs() <= 9_007_199_254_740_991.0 }

fn lookup<'a>(context: &'a HashMap<String, Value>, key: &str) -> Option<&'a Value> {
    if let Some(value) = context.get(key) { return Some(value); }
    let mut parts = key.split('.');
    let mut current = context.get(parts.next()?)?;
    for part in parts { current = current.as_object()?.get(part)?; }
    Some(current)
}

impl Condition {
    pub fn evaluate(&self, context: &HashMap<String, Value>) -> Outcome {
        let Self::Compare { key, op, rhs } = self else {
            return if matches!(self, Self::Always) { Outcome::Match } else { Outcome::Unsupported };
        };
        let Some(actual) = lookup(context, key).filter(|v| !v.is_null()) else { return Outcome::NoMatch; };
        let ordering = match (rhs, actual) {
            (Scalar::Number(expected), Value::Number(number)) => match number.as_f64() {
                Some(actual) if precise_number(actual) => actual.total_cmp(expected),
                _ => return Outcome::Unsupported,
            },
            (Scalar::Text(expected), Value::String(actual)) => actual.cmp(expected),
            (Scalar::Boolean(expected), Value::Bool(actual)) => actual.cmp(expected),
            _ => return Outcome::Unsupported,
        };
        let matched = match op {
            Operator::Eq => ordering == Ordering::Equal, Operator::Ne => ordering != Ordering::Equal,
            Operator::Gt => ordering == Ordering::Greater, Operator::Ge => ordering != Ordering::Less,
            Operator::Lt => ordering == Ordering::Less, Operator::Le => ordering != Ordering::Greater,
        };
        if matched { Outcome::Match } else { Outcome::NoMatch }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn context_prefix_matches_native_context_and_nested_aliases() {
        let context = HashMap::from([
            ("amount".into(), serde_json::json!(150)),
            ("risk".into(), serde_json::json!({"score": 70})),
        ]);
        assert_eq!(compile(Some("context.amount > 100")).evaluate(&context), Outcome::Match);
        assert_eq!(compile(Some("risk.score >= 70")).evaluate(&context), Outcome::Match);
    }
    #[test]
    fn complex_and_type_mismatched_conditions_are_not_false_matches() {
        let context = HashMap::from([("amount".into(), serde_json::json!("150"))]);
        assert_eq!(compile(Some("context.amount > 100 and risk.score >= 70")).evaluate(&context), Outcome::Unsupported);
        assert_eq!(compile(Some("context.amount > 100")).evaluate(&context), Outcome::Unsupported);
    }
    #[test]
    fn operators_inside_quoted_strings_do_not_confuse_the_parser() {
        let context = HashMap::from([("note".into(), serde_json::json!("a > b"))]);
        assert_eq!(compile(Some("context.note == \"a > b\"")).evaluate(&context), Outcome::Match);
    }
}

