package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/runtime/schema"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/cache"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/healthz"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
	metricsserver "sigs.k8s.io/controller-runtime/pkg/metrics/server"
	"sigs.k8s.io/controller-runtime/pkg/predicate"
)

var policyGVK = schema.GroupVersionKind{Group: "security.zt-platform.io", Version: "v1alpha1", Kind: "ZeroTrustPolicy"}

type platformClient struct {
	baseURL, tenant, workspace, tokenFile string
	http                                  *http.Client
}

type policyRow struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	Version    int64  `json:"version"`
	Status     string `json:"status"`
	PolicyText string `json:"policyText"`
}

func (p *platformClient) request(ctx context.Context, method, path string, body any, out any) error {
	var payload []byte
	var err error
	if body != nil {
		payload, err = json.Marshal(body)
		if err != nil {
			return err
		}
	}
	// Read every request so Secret rotation does not require a restart.
	token, err := os.ReadFile(p.tokenFile)
	if err != nil {
		return errors.New("cannot read platform bearer token file")
	}
	if strings.TrimSpace(string(token)) == "" {
		return errors.New("platform bearer token is empty")
	}
	req, err := http.NewRequestWithContext(ctx, method, p.baseURL+path, bytes.NewReader(payload))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+strings.TrimSpace(string(token)))
	req.Header.Set("X-Tenant-Id", p.tenant)
	if p.workspace != "" {
		req.Header.Set("X-Workspace-Id", p.workspace)
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := p.http.Do(req)
	if err != nil {
		return errors.New("platform request failed")
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("platform returned HTTP %d", resp.StatusCode)
	}
	return json.NewDecoder(io.LimitReader(resp.Body, 8<<20)).Decode(out)
}

func (p *platformClient) syncDraft(ctx context.Context, name string, version int64, text string) (policyRow, error) {
	var rows []policyRow
	if err := p.request(ctx, http.MethodGet, "/v1/policies/all", nil, &rows); err != nil {
		return policyRow{}, err
	}
	for _, row := range rows {
		if row.Name == name && row.Version == version {
			if row.PolicyText != text {
				return policyRow{}, errors.New("policy version already exists with different content")
			}
			return row, nil
		}
	}
	var row policyRow
	err := p.request(ctx, http.MethodPost, "/v1/policies", map[string]any{
		"name": name, "version": version, "status": "DRAFT", "policyText": text,
	}, &row)
	if err != nil {
		return policyRow{}, err
	}
	if row.ID == "" || row.Name != name || row.Version != version || row.PolicyText != text || row.Status != "DRAFT" {
		return policyRow{}, errors.New("platform returned an inconsistent policy acknowledgement")
	}
	return row, nil
}

type reconciler struct {
	client.Client
	platform *platformClient
}

func policyObject() *unstructured.Unstructured {
	p := &unstructured.Unstructured{}
	p.SetGroupVersionKind(policyGVK)
	return p
}

func (r *reconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	p := policyObject()
	if err := r.Get(ctx, req.NamespacedName, p); err != nil {
		return ctrl.Result{}, client.IgnoreNotFound(err)
	}
	if !p.GetDeletionTimestamp().IsZero() {
		return ctrl.Result{}, nil
	}
	if p.GetUID() == "" || p.GetGeneration() < 1 || p.GetGeneration() > 2147483647 {
		return ctrl.Result{}, r.setStatus(ctx, p, "Rejected", "InvalidPolicyVersion", "Resource UID and a generation within the platform version range are required.", "", "")
	}
	text, _, _ := unstructured.NestedString(p.Object, "spec", "policyText")
	if strings.TrimSpace(text) == "" {
		return ctrl.Result{}, r.setStatus(ctx, p, "Rejected", "PolicyTextRequired", "Legacy workload fields do not implement identity, attestation, or capability enforcement.", "", "")
	}
	if len(text) > 65536 {
		return ctrl.Result{}, r.setStatus(ctx, p, "Rejected", "PolicyTextTooLarge", "Policy text must not exceed 65536 bytes.", "", "")
	}
	for _, field := range []string{"workload", "identityRequired", "attestationRequired", "capabilities", "decision", "approvalRequired"} {
		if _, exists, _ := unstructured.NestedFieldNoCopy(p.Object, "spec", field); exists {
			return ctrl.Result{}, r.setStatus(ctx, p, "Rejected", "UnsupportedLegacyControls", "Remove legacy workload controls; this operator only synchronizes policy DSL drafts.", "", "")
		}
	}
	hash := sha256.Sum256([]byte(text))
	row, err := r.platform.syncDraft(ctx, "k8s-"+string(p.GetUID()), p.GetGeneration(), text)
	if err != nil {
		if statusErr := r.setStatus(ctx, p, "Error", "SynchronizationFailed", err.Error(), "", ""); statusErr != nil {
			return ctrl.Result{}, statusErr
		}
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}
	if err := r.setStatus(ctx, p, "Synced", "PolicyStored", "Policy version is stored; use Policy Studio to validate, simulate and activate it. This is not workload enforcement.", row.ID, hex.EncodeToString(hash[:])); err != nil {
		return ctrl.Result{}, err
	}
	return ctrl.Result{RequeueAfter: 5 * time.Minute}, nil
}

func (r *reconciler) setStatus(ctx context.Context, p *unstructured.Unstructured, phase, reason, message, id, hash string) error {
	before := p.DeepCopy()
	status := map[string]any{
		"phase": phase, "reason": reason, "message": message,
		"observedGeneration": p.GetGeneration(), "observedHash": hash, "platformPolicyId": id,
	}
	old, _, _ := unstructured.NestedMap(p.Object, "status")
	a, _ := json.Marshal(old)
	b, _ := json.Marshal(status)
	if bytes.Equal(a, b) {
		return nil
	}
	if err := unstructured.SetNestedMap(p.Object, status, "status"); err != nil {
		return err
	}
	return r.Status().Patch(ctx, p, client.MergeFrom(before))
}

func requiredEnv(name string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		panic(name + " must be configured")
	}
	return value
}

func main() {
	ctrl.SetLogger(zap.New())
	namespace := requiredEnv("WATCH_NAMESPACE")
	base := strings.TrimRight(requiredEnv("PLATFORM_URL"), "/")
	u, err := url.Parse(base)
	if err != nil || u.Host == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || u.Path != "" {
		panic("PLATFORM_URL must be an origin without credentials, path, query or fragment")
	}
	if u.Scheme != "https" && !(u.Scheme == "http" && os.Getenv("ALLOW_INSECURE_PLATFORM_HTTP") == "true") {
		panic("PLATFORM_URL requires HTTPS; local testing may explicitly enable ALLOW_INSECURE_PLATFORM_HTTP")
	}
	scheme := runtime.NewScheme()
	scheme.AddKnownTypeWithName(policyGVK, policyObject())
	list := &unstructured.UnstructuredList{}
	list.SetGroupVersionKind(policyGVK.GroupVersion().WithKind("ZeroTrustPolicyList"))
	scheme.AddKnownTypeWithName(list.GroupVersionKind(), list)
	metav1.AddToGroupVersion(scheme, policyGVK.GroupVersion())
	mgr, err := ctrl.NewManager(ctrl.GetConfigOrDie(), ctrl.Options{
		Scheme: scheme, Cache: cache.Options{DefaultNamespaces: map[string]cache.Config{namespace: {}}},
		Metrics: metricsserver.Options{BindAddress: "0"}, HealthProbeBindAddress: ":8081",
		LeaderElection: true, LeaderElectionID: "zt-policy-operator.security.zt-platform.io", LeaderElectionNamespace: namespace,
	})
	if err != nil {
		panic(err)
	}
	r := &reconciler{Client: mgr.GetClient(), platform: &platformClient{
		baseURL: base, tenant: requiredEnv("PLATFORM_TENANT_ID"), workspace: os.Getenv("PLATFORM_WORKSPACE_ID"),
		tokenFile: requiredEnv("PLATFORM_TOKEN_FILE"), http: &http.Client{Timeout: 15 * time.Second,
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }},
	}}
	if err := ctrl.NewControllerManagedBy(mgr).For(policyObject()).WithEventFilter(predicate.GenerationChangedPredicate{}).Complete(r); err != nil {
		panic(err)
	}
	if err := mgr.AddHealthzCheck("healthz", healthz.Ping); err != nil {
		panic(err)
	}
	if err := mgr.AddReadyzCheck("readyz", healthz.Ping); err != nil {
		panic(err)
	}
	if err := mgr.Start(ctrl.SetupSignalHandler()); err != nil {
		panic(err)
	}
}
