use axum::{extract::State, http::{StatusCode, HeaderMap}, routing::{get,post}, Json, Router};
use base64::{engine::general_purpose::STANDARD as B64, Engine};
use ed25519_dalek::{Signature, Signer, SigningKey, Verifier, VerifyingKey};
use sha2::{Digest, Sha256};
use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, env, sync::{Arc, atomic::{AtomicU64, Ordering}}, time::{Duration, Instant}};
use tracing::{info, warn};
use tower_http::cors::CorsLayer;

#[derive(Clone)] struct AppState {
    bundle: Arc<RwLock<Bundle>>,
    control_plane: String,
    client: reqwest::Client,
    stats: Arc<Stats>,
    max_bundle_age: Duration,
    fail_mode: String,
    verify_key: Option<VerifyingKey>,
    bundle_pin: Option<String>,
    evidence_key: Option<SigningKey>,
    evidence_key_id: String,
    attestation: AttestationState,
}
#[derive(Default, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Bundle {
    version: String,
    generated_at: String,
    #[serde(default)] signature: Option<String>,
    #[serde(default)] key_id: Option<String>,
    #[serde(default)] bundle_hash: Option<String>,
    #[serde(default)] policies: Vec<FastPolicy>,
}
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FastPolicy { name:String, version:i32, priority:i32, effect:String,
    principal_type:Option<String>, actions:Vec<String>, resource_type:Option<String>,
    condition:Option<String>, fast_path:bool }
#[derive(Deserialize)] struct EvalRequest { principal: Principal, action: Action,
    resource: Resource, #[serde(default)] context: HashMap<String,serde_json::Value> }
#[derive(Deserialize)] struct Principal { id:String, #[serde(rename="type")] kind:String }
#[derive(Deserialize)] struct Action { name:String }
#[derive(Deserialize)] struct Resource { #[serde(rename="type")] kind:String, id:String }
#[derive(Serialize)] struct EvalResponse { decision:String, reason:String,
    policy:Option<String>, engine:String, bundle_version:String, latency_us:u128,
    bundle_age_seconds:u64, bundle_hash:Option<String>, workload_identity:Option<String>,
    evidence:Option<Evidence> }
#[derive(Clone, Serialize)] struct Evidence { decision:String, policy:Option<String>,
    bundle_version:String, bundle_hash:Option<String>, workload_identity:Option<String>,
    timestamp:String, payload_hash:String, signature:String, key_id:String }
#[derive(Clone, Serialize)] struct AttestationState { provider:String,
    mode:String, status:String, document_hash:Option<String>, expected_pcr3:Option<String>,
    expected_pcr8:Option<String>, nonce:Option<String>, key_id:Option<String>,
    verified_at:Option<String>, reason:String }
#[derive(Serialize)] struct StatsResponse { requests:u64, errors:u64, decisions:u64,
    allow:u64, deny:u64, step_up:u64, defer:u64, p50_us:u64, p95_us:u64, p99_us:u64,
    p999_us:u64, max_us:u64, bundle_version:String, bundle_age_seconds:u64,
    bundle_signature_valid:bool, bundle_hash:Option<String>, bundle_pin:Option<String>,
    fail_mode:String, attestation_status:String, attestation_provider:String }
struct Stats { requests:AtomicU64, errors:AtomicU64, decisions:AtomicU64,
    allow:AtomicU64, deny:AtomicU64, step_up:AtomicU64, defer:AtomicU64, max_us:AtomicU64,
    buckets:[AtomicU64; 12] }
impl Default for Stats { fn default()->Self{Self{requests:AtomicU64::new(0),
            errors:AtomicU64::new(0),decisions:AtomicU64::new(0),allow:AtomicU64::new(0),
            deny:AtomicU64::new(0),step_up:AtomicU64::new(0),defer:AtomicU64::new(0),
            max_us:AtomicU64::new(0),buckets:std::array::from_fn(|_|AtomicU64::new(0))}
    }}
impl Stats { fn record(&self, us:u64, decision:&str){ self.decisions.fetch_add(1,
        Ordering::Relaxed);
        match decision {"ALLOW"=>{self.allow.fetch_add(1,Ordering::Relaxed);
            },"DENY"=>{self.deny.fetch_add(1,Ordering::Relaxed);
            },"STEP_UP"=>{self.step_up.fetch_add(1,
            Ordering::Relaxed);
            },_=>{self.defer.fetch_add(1,Ordering::Relaxed);
            }};
    self.max_us.fetch_max(us,Ordering::Relaxed);
    let idx=match us {0..=49=>0,
        50..=99=>1,100..=199=>2,200..=399=>3,400..=799=>4,800..=1499=>5,1500..=2999=>6,
        3000..=4999=>7,5000..=9999=>8,10000..=19999=>9,20000..=49999=>10,_=>11}
    ;
    self.buckets[idx].fetch_add(1,Ordering::Relaxed);
    }
 fn percentile(&self, p:f64)->u64{ let total=self.decisions.load(Ordering::Relaxed);
     if total==0{return 0};
     let target=((total as f64)*p).ceil() as u64;
     let mut seen=0;
     let bounds=[49,99,199,399,799,1499,2999,4999,9999,19999,49999,u64::MAX];
     for (i,b) in bounds.iter().enumerate(){seen+=self.buckets[i].load(Ordering::Relaxed);
         if seen>=target{return *b;
         }} 0 }
}

#[tokio::main]
async fn main(){
 tracing_subscriber::fmt().with_env_filter(env::var("RUST_LOG").unwrap_or_else(|_|"info".into())).init();
 let control_plane=env::var("ZT_CONTROL_PLANE_URL").unwrap_or_else(|_|"http://localhost:8080".into());
 let stats=Arc::new(Stats::default());
 let verify_key=env::var("ZT_POLICY_PUBLIC_KEY_B64").ok().and_then(|x|B64.decode(x).ok()).and_then(|b|{
     if b.len()!=32{return None};
     let mut a=[0u8;32];
     a.copy_from_slice(&b);
     VerifyingKey::from_bytes(&a).ok()});
 let evidence_key=env::var("ZT_EVIDENCE_SIGNING_PRIVATE_KEY_B64").ok().and_then(|x|B64.decode(x).ok()).and_then(|b|{
     if b.len()!=32{return None};
     let mut a=[0u8;32];
     a.copy_from_slice(&b);
     Some(SigningKey::from_bytes(&a))}
 );
 let evidence_key_id=env::var("ZT_EVIDENCE_SIGNING_KEY_ID").unwrap_or_else(|_|"zt-dp-evidence-ed25519".into());
 let attestation = load_attestation();
 let state=AppState{bundle:Arc::new(RwLock::new(Bundle::default())),control_plane,
client:reqwest::Client::builder().pool_max_idle_per_host(256).connect_timeout(Duration::from_secs(1)).timeout(Duration::from_secs(3)).build().unwrap(),
stats,max_bundle_age:Duration::from_secs(env::var("ZT_BUNDLE_MAX_AGE_SECONDS").ok().and_then(|x|x.parse().ok()).unwrap_or(60)),
     fail_mode:env::var("ZT_FAIL_MODE").unwrap_or_else(|_|"FAIL_CLOSED".into()),
     verify_key,bundle_pin:env::var("ZT_POLICY_BUNDLE_PIN").ok().filter(|x|!x.trim().is_empty()),
     evidence_key, evidence_key_id, attestation};
 refresh(&state).await;
let poll=state.clone();
tokio::spawn(async move { loop { tokio::time::sleep(Duration::from_secs(env::var("ZT_POLICY_REFRESH_SECONDS").ok().and_then(|x|x.parse().ok()).unwrap_or(5))).await;
         refresh(&poll).await;
         }});
 let app=Router::new().route("/health",get(health)).route("/ready",get(ready)).route("/metrics",
 get(metrics)).route("/v1/fast/evaluate",post(evaluate)).route("/v1/fast/policy-bundle",
 get(bundle)).route("/v1/fast/stats",get(stats_endpoint)).route("/v1/fast/identity",
get(identity_endpoint)).route("/v1/fast/attestation",get(attestation_endpoint)).layer(CorsLayer::permissive()).with_state(state);
 let addr=env::var("ZT_BIND_ADDR").unwrap_or_else(|_|"0.0.0.0:8091".into());
 let listener=tokio::net::TcpListener::bind(&addr).await.unwrap();
 info!(%addr,
 "policy data plane listening");
 axum::serve(listener,app).await.unwrap();
}
fn bundle_age(b:&Bundle)->u64{ if b.generated_at.is_empty(){return u64::MAX}
    ;
    match chrono::DateTime::parse_from_rfc3339(&b.generated_at){Ok(ts)=>{
            let now=chrono::Utc::now();
            now.signed_duration_since(ts.with_timezone(&chrono::Utc)).num_seconds().max(0)
as u64}
        ,Err(_)=>u64::MAX}}
fn signature_valid(s:&AppState,b:&Bundle)->bool{ let Some(key)=&s.verify_key else{
        return b.signature.is_none() || b.signature.as_deref()==Some("")};
        let Some(sig)=&b.signature else{
        return false};
        let bytes=match B64.decode(sig){Ok(x)=>x,Err(_)=>return false}
    ;
    if bytes.len()!=64{return false};
    let mut a=[0u8;64];
    a.copy_from_slice(&bytes);
    let payload=canonical_payload(b);
    key.verify(payload.as_bytes(),&Signature::from_bytes(&a)).is_ok() }
fn canonical_payload(b:&Bundle)->String{ let mut policies=b.policies.clone();
    policies.sort_by(|a,c|a.priority.cmp(&c.priority).then(a.name.cmp(&c.name)).then(a.version.cmp(&c.version)));
    let mut out=format!("version={}\ngenerated_at={}\n",b.version,b.generated_at);
    for p in policies{out.push_str(&format!("{}|{}|{}|{}|{}|{}|{}|{}\n",p.name,
        p.version,p.priority,p.effect,p.principal_type.unwrap_or_default(),p.actions.join(","),
        p.resource_type.unwrap_or_default(),p.condition.unwrap_or_default()));
    } out}
async fn health(State(s):State<AppState>)->Json<serde_json::Value>{let b=s.bundle.read().clone();
    Json(serde_json::json!({"status":"UP","engine":"rust-fast-path","bundleVersion":b.version,
        "bundleHash":b.bundle_hash,"policies":b.policies.len(),"bundleAgeSeconds":bundle_age(&b),
        "signatureValid":signature_valid(&s,&b),"bundlePin":s.bundle_pin,"failMode":s.fail_mode,
        "atomicRollout":true}))}
async fn ready(State(s):State<AppState>)->(StatusCode,Json<serde_json::Value>){
    let b=s.bundle.read().clone();
    let age=bundle_age(&b);
    let sig=signature_valid(&s,
    &b);
    let pin_ok=s.bundle_pin.as_ref().map(|p|p==&b.version).unwrap_or(true);
let att_ok=attestation_required_ok(&s.attestation);
let ok=!b.policies.is_empty()&&age<=s.max_bundle_age.as_secs()&&sig&&pin_ok&&att_ok;
    let code=if ok{StatusCode::OK}else{StatusCode::SERVICE_UNAVAILABLE};
    (code,
    Json(serde_json::json!({"ready":ok,"bundleAgeSeconds":age,"maxBundleAgeSeconds":s.max_bundle_age.as_secs(),
        "signatureValid":sig,"policyCount":b.policies.len(),"bundleHash":b.bundle_hash,
        "bundlePin":s.bundle_pin,"pinMatch":pin_ok,"attestation":&s.attestation}
    )))}
async fn bundle(State(s):State<AppState>)->Json<Bundle>{Json(s.bundle.read().clone())}
async fn stats_endpoint(State(s):State<AppState>)->Json<StatsResponse>{
    let b=s.bundle.read().clone();
    Json(StatsResponse{requests:s.stats.requests.load(Ordering::Relaxed),
        errors:s.stats.errors.load(Ordering::Relaxed),decisions:s.stats.decisions.load(Ordering::Relaxed),
        allow:s.stats.allow.load(Ordering::Relaxed),deny:s.stats.deny.load(Ordering::Relaxed),
        step_up:s.stats.step_up.load(Ordering::Relaxed),defer:s.stats.defer.load(Ordering::Relaxed),
        p50_us:s.stats.percentile(0.50),p95_us:s.stats.percentile(0.95),p99_us:s.stats.percentile(0.99),
        p999_us:s.stats.percentile(0.999),max_us:s.stats.max_us.load(Ordering::Relaxed),
        bundle_version:b.version.clone(),bundle_age_seconds:bundle_age(&b),bundle_signature_valid:signature_valid(&s,
        &b),bundle_hash:b.bundle_hash.clone(),bundle_pin:s.bundle_pin.clone(),
        fail_mode:s.fail_mode.clone(),attestation_status:s.attestation.status.clone(),
        attestation_provider:s.attestation.provider.clone()})}
async fn metrics(State(s):State<AppState>)->String{let b=s.bundle.read().clone();
    format!("# TYPE zt_dp_requests_total counter\nzt_dp_requests_total {}\n# TYPE zt_dp_decisions_total counter\nzt_dp_decisions_total {}\n# TYPE zt_dp_allow_total counter\nzt_dp_allow_total {}\nzt_dp_deny_total {}\nzt_dp_step_up_total {}\nzt_dp_defer_total {}\nzt_dp_p99_us {}\nzt_dp_bundle_age_seconds {}\n",
    s.stats.requests.load(Ordering::Relaxed),s.stats.decisions.load(Ordering::Relaxed),
    s.stats.allow.load(Ordering::Relaxed),s.stats.deny.load(Ordering::Relaxed),
    s.stats.step_up.load(Ordering::Relaxed),s.stats.defer.load(Ordering::Relaxed),
    s.stats.percentile(0.99),bundle_age(&b))}
async fn evaluate(State(s):State<AppState>, headers: HeaderMap, Json(r):Json<EvalRequest>)->(StatusCode,
Json<EvalResponse>){
 let workload_identity=extract_workload_identity(&headers);
 let st=Instant::now();
 s.stats.requests.fetch_add(1,Ordering::Relaxed);
 let b=s.bundle.read().clone();
 let age=bundle_age(&b);
 let sig_ok=signature_valid(&s,
 &b);
 let pin_ok=s.bundle_pin.as_ref().map(|p|p==&b.version).unwrap_or(true);
 let att_ok=attestation_required_ok(&s.attestation);
 if b.policies.is_empty() ||
age>s.max_bundle_age.as_secs() || !sig_ok || !pin_ok || !att_ok {
     let decision=if s.fail_mode.eq_ignore_ascii_case("FAIL_OPEN"){"ALLOW"}
     else{"DENY"};
     let us=st.elapsed().as_micros();
     s.stats.record(us.min(u64::MAX as u128) as u64,decision);
     return (StatusCode::OK,Json(EvalResponse{decision:decision.into(),reason:if !att_ok {
             "hardware/workload attestation not satisfied".into()} else
{"policy bundle unavailable, stale, or signature invalid".into()}
         ,policy:None,engine:"rust-fast-path".into(),bundle_version:b.version.clone(),
         bundle_age_seconds:age,latency_us:us,bundle_hash:b.bundle_hash.clone(),
         workload_identity:workload_identity.clone(),evidence:build_evidence(&s,
         decision,&None,&b,&workload_identity)}));
         }
 let mut matches: Vec<&FastPolicy>=Vec::new();
 for p in &b.policies { if p.principal_type.as_deref().is_some_and(|x|x!=r.principal.kind){
         continue;
         } if !p.actions.is_empty()&&!p.actions.iter().any(|x|x==&r.action.name){
         continue;
         } if p.resource_type.as_deref().is_some_and(|x|x!=r.resource.kind){
         continue;
         } if !p.fast_path { let us=st.elapsed().as_micros();
         s.stats.record(us.min(u64::MAX as u128) as u64,
         "DEFER");
         return (StatusCode::OK,Json(EvalResponse{decision:"DEFER".into(),
             reason:"a matching policy requires authoritative control-plane evaluation".into(),
             policy:Some(p.name.clone()),engine:"rust-fast-path".into(),bundle_version:b.version.clone(),
             bundle_age_seconds:age,latency_us:us,bundle_hash:b.bundle_hash.clone(),
             workload_identity:workload_identity.clone(), evidence:build_evidence(&s,
             "DEFER",&Some(p.name.clone()),&b,&workload_identity)}));
             } if let Some(c)=&p.condition{
         if !condition_match(c,&r.context){continue;
         }} matches.push(p);
         }
 let (decision,reason,policy) = if let Some(p)=matches.iter().find(|p|p.effect.eq_ignore_ascii_case("deny")){
     ("DENY","denied by fast-path policy",Some(p.name.clone()))} else if let
Some(p)=matches.iter().find(|p|p.effect.eq_ignore_ascii_case("step_up")){
     ("STEP_UP","step-up required by fast-path policy",Some(p.name.clone()))}
 else if let Some(p)=matches.iter().find(|p|p.effect.eq_ignore_ascii_case("allow")){
     ("ALLOW","allowed by fast-path policy",Some(p.name.clone()))} else {("DEFER",
     "no supported fast-path policy matched; use control plane",None)};
 let us=st.elapsed().as_micros();
 s.stats.record(us.min(u64::MAX as u128) as u64,decision);
 (StatusCode::OK,
 Json(EvalResponse{decision:decision.into(),reason:reason.into(),policy:policy.clone(),
     engine:"rust-fast-path".into(),bundle_version:b.version.clone(),bundle_age_seconds:age,
     latency_us:us,bundle_hash:b.bundle_hash.clone(),workload_identity:workload_identity.clone(),
     evidence:build_evidence(&s,decision,&policy,&b,&workload_identity)}))
}
fn extract_workload_identity(headers:&HeaderMap)->Option<String>{
 let raw=headers.get("x-forwarded-client-cert")?.to_str().ok()?.to_string();
 for part in raw.split(';') { let p=part.trim();
     if let Some(uri)=p.strip_prefix("URI="){
         return Some(uri.trim_matches('"').to_string());
         } }
 None
}
fn build_evidence(s:&AppState,decision:&str,policy:&Option<String>,b:&Bundle,
identity:&Option<String>)->Option<Evidence>{
 let key=s.evidence_key.as_ref()?;
 let timestamp=chrono::Utc::now().to_rfc3339();
 let material=serde_json::json!({"decision":decision,"policy":policy,"bundleVersion":b.version,
     "bundleHash":b.bundle_hash,"workloadIdentity":identity,"attestationProvider":s.attestation.provider,
     "attestationStatus":s.attestation.status,"attestationDocumentHash":s.attestation.document_hash,
     "timestamp":timestamp});
 let payload=serde_json::to_string(&material).ok()?;
 let hash=format!("{:x}",Sha256::digest(payload.as_bytes()));
 let signature=B64.encode(key.sign(payload.as_bytes()).to_bytes());
 Some(Evidence{decision:decision.to_string(),policy:policy.clone(),bundle_version:b.version.clone(),
     bundle_hash:b.bundle_hash.clone(),workload_identity:identity.clone(),timestamp,
     payload_hash:hash,signature,key_id:s.evidence_key_id.clone()})
}
async fn attestation_endpoint(State(s):State<AppState>)->Json<AttestationState>{Json(s.attestation.clone())}
fn attestation_required_ok(a:&AttestationState)->bool{a.mode.eq_ignore_ascii_case("DISABLED") ||
a.status.eq_ignore_ascii_case("VERIFIED")}
fn load_attestation()->AttestationState{
 let provider=env::var("ZT_ATTESTATION_PROVIDER").unwrap_or_else(|_|"NONE".into());
 let mode=env::var("ZT_ATTESTATION_MODE").unwrap_or_else(|_|"DISABLED".into());
 let expected_pcr3=env::var("ZT_NITRO_EXPECTED_PCR3").ok();
 let expected_pcr8=env::var("ZT_NITRO_EXPECTED_PCR8").ok();
 let nonce=env::var("ZT_ATTESTATION_NONCE").ok();
 let key_id=env::var("ZT_ATTESTATION_KMS_KEY_ID").ok();
 let document=env::var("ZT_NITRO_ATTESTATION_DOCUMENT_B64").ok();
 let document_hash=document.as_ref().and_then(|d|B64.decode(d).ok()).map(|b|format!("{:x}",Sha256::digest(b)));
 let status: String=if mode.eq_ignore_ascii_case("DISABLED"){"DISABLED".into()}
 else if mode.eq_ignore_ascii_case("DOCUMENT_HASH") && document_hash.is_some(){
     "INTEGRITY_ONLY".into()} else if mode.eq_ignore_ascii_case("EXTERNAL_VERIFIED") {
     "VERIFIED".into()} else {"UNVERIFIED".into()};
 let reason=if status=="VERIFIED"{"attestation accepted by external verifier; PCR/signature verification is outside the Rust data plane"}
 else if status=="INTEGRITY_ONLY"{"attestation document is integrity-hashed only; cryptographic AWS/Nitro verification is not asserted"}
 else if mode.eq_ignore_ascii_case("DISABLED"){"attestation gate disabled"}
 else {"no verified attestation evidence available"};
 AttestationState{provider,mode,status:status.clone(),document_hash,expected_pcr3,expected_pcr8,
     nonce,key_id,verified_at:if status=="VERIFIED"{Some(chrono::Utc::now().to_rfc3339())}
     else{None},reason:reason.into()}
}
async fn identity_endpoint(headers:HeaderMap)->Json<serde_json::Value>{
 let identity=extract_workload_identity(&headers);
 Json(serde_json::json!({"authenticated":identity.is_some(),"workloadIdentity":identity,
     "identitySource":"envoy-x-forwarded-client-cert","cryptographicBoundary":"mTLS"}
 ))
}
fn condition_match(c:&str,ctx:&HashMap<String,serde_json::Value>)->bool{
let c=c.trim();
if c.is_empty(){return true}if c.contains(" and ")||c.contains(" or ")||c.contains("(")||c.contains(")")||c.contains(" in ")||c.contains(" contains "){
        return false}let ops=[">=","<=","!=","==",">","<"];
        let op=ops.iter().find(|o|c.contains(**o));
    let Some(op)=op else{return false};
    let mut parts=c.splitn(2,op);
    let key=parts.next().unwrap_or("").trim();
    let rhs=parts.next().unwrap_or("").trim().trim_matches('"');
    let Some(v)=ctx.get(key) else{
        return false};
        if let Some(n)=v.as_f64(){if let Ok(r)=rhs.parse::<f64>(){
            return cmp_num(n,r,op)}}if let Some(s)=v.as_str(){return match *op{"=="=>s==rhs,
            "!="=>s!=rhs,_=>false}}false}
fn cmp_num(a:f64,b:f64,op:&str)->bool{match op{">"=>a>b,"<"=>a<b,">="=>a>=b,
        "<="=>a<=b,"=="=>(a-b).abs()<f64::EPSILON,"!="=>(a-b).abs()>=f64::EPSILON,
        _=>false}}
async fn refresh(s:&AppState){
 let url=format!("{}/v1/policy-bundles/fast",s.control_plane.trim_end_matches('/'));
 let mut delay=Duration::from_secs(1);
 for attempt in 0..4 {
  match s.client.get(&url).header("X-Tenant-Id",env::var("ZT_TENANT_ID").unwrap_or_default()).header("X-API-Key",
  env::var("ZT_API_KEY").unwrap_or_else(|_|"dev-master-key".into())).send().await {
   Ok(r) if r.status().is_success() => match r.json::<Bundle>().await {
    Ok(b)=>{
      let pin_ok=s.bundle_pin.as_ref().map(|p|p==&b.version).unwrap_or(true);
      if signature_valid(s,&b) && pin_ok { *s.bundle.write()=b;
          return;
      }
      warn!(version=%b.version,pin_ok,"policy bundle rejected");
      s.stats.errors.fetch_add(1,Ordering::Relaxed);
      return;
    },
    Err(e)=>warn!(attempt, error=%e,"invalid policy bundle")
   },
   Ok(r)=>warn!(attempt,status=%r.status(),"policy bundle refresh returned non-success"),
   Err(e)=>warn!(attempt,error=%e,"policy bundle refresh failed; keeping last-known-good bundle")
  }
  s.stats.errors.fetch_add(1,Ordering::Relaxed);
  tokio::time::sleep(delay).await;
  delay=(delay*2).min(Duration::from_secs(8));
 }
}
#[cfg(test)]mod tests{use super::*;
    #[test]fn numeric_condition_works(){
        let mut c=HashMap::new();
        c.insert("context.amount".into(),serde_json::json!(150));
        assert!(condition_match("context.amount > 100",&c));
        assert!(!condition_match("context.amount > 200",
&c));
}#[test]fn compound_condition_defers(){let c=HashMap::new();
assert!(!condition_match("context.amount > 100 and risk.score >= 70",
        &c));
        }#[test]fn canonical_is_deterministic(){let b=Bundle{version:"1".into(),
            generated_at:"x".into(),signature:None,key_id:None,bundle_hash:None,policies:vec![]}
        ;
        assert_eq!(canonical_payload(&b),"version=1\ngenerated_at=x\n");
        }}
