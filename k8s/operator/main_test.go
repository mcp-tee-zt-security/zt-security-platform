package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	apiextensions "k8s.io/apiextensions-apiserver/pkg/apis/apiextensions"
	apiextensionsv1 "k8s.io/apiextensions-apiserver/pkg/apis/apiextensions/v1"
	structuralschema "k8s.io/apiextensions-apiserver/pkg/apiserver/schema"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/validation/field"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client/fake"
	"sigs.k8s.io/yaml"
)

func testPlatform(t *testing.T, handler http.HandlerFunc) *platformClient {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	file := filepath.Join(t.TempDir(), "token")
	if err := os.WriteFile(file, []byte("test-token"), 0600); err != nil {
		t.Fatal(err)
	}
	return &platformClient{baseURL: server.URL, tenant: "tenant", tokenFile: file, http: server.Client()}
}

func TestCRDSchemaIsStructural(t *testing.T) {
	data, err := os.ReadFile("config/crd/bases/security.zt-platform.io_zerotrustpolicies.yaml")
	if err != nil {
		t.Fatal(err)
	}
	var crd apiextensionsv1.CustomResourceDefinition
	if err := yaml.UnmarshalStrict(data, &crd); err != nil {
		t.Fatal(err)
	}
	var internal apiextensions.JSONSchemaProps
	if err := apiextensionsv1.Convert_v1_JSONSchemaProps_To_apiextensions_JSONSchemaProps(crd.Spec.Versions[0].Schema.OpenAPIV3Schema, &internal, nil); err != nil {
		t.Fatal(err)
	}
	s, err := structuralschema.NewStructural(&internal)
	if err != nil {
		t.Fatal(err)
	}
	if errors := structuralschema.ValidateStructural(field.NewPath("schema"), s); len(errors) != 0 {
		t.Fatal(errors)
	}
}

func TestReconcileStoresDraftAndAcknowledgesGeneration(t *testing.T) {
	rows := []policyRow{}
	posts := 0
	p := testPlatform(t, func(w http.ResponseWriter, req *http.Request) {
		if req.Method == http.MethodGet {
			json.NewEncoder(w).Encode(rows)
			return
		}
		var row policyRow
		if err := json.NewDecoder(req.Body).Decode(&row); err != nil {
			t.Error(err)
		}
		row.ID = "stored"
		rows = append(rows, row)
		posts++
		json.NewEncoder(w).Encode(row)
	})
	object := policyObject()
	object.SetName("test")
	object.SetNamespace("test")
	object.SetUID(types.UID("resource-uid"))
	object.SetGeneration(2)
	object.Object["spec"] = map[string]any{"policyText": "policy text"}
	scheme := runtime.NewScheme()
	scheme.AddKnownTypeWithName(policyGVK, policyObject())
	c := fake.NewClientBuilder().WithScheme(scheme).WithStatusSubresource(object).WithObjects(object).Build()
	r := &reconciler{Client: c, platform: p}
	req := ctrl.Request{NamespacedName: types.NamespacedName{Namespace: "test", Name: "test"}}
	for i := 0; i < 2; i++ {
		result, err := r.Reconcile(context.Background(), req)
		if err != nil || result.RequeueAfter == 0 {
			t.Fatalf("reconcile failed: %v", err)
		}
	}
	got := policyObject()
	if err := c.Get(context.Background(), req.NamespacedName, got); err != nil {
		t.Fatal(err)
	}
	phase, _, _ := unstructured.NestedString(got.Object, "status", "phase")
	generation, _, _ := unstructured.NestedInt64(got.Object, "status", "observedGeneration")
	if phase != "Synced" || generation != 2 || posts != 1 {
		t.Fatalf("incorrect acknowledgement: phase=%s generation=%d posts=%d", phase, generation, posts)
	}
	if rows[0].Name != "k8s-resource-uid" || rows[0].Version != 2 || rows[0].Status != "DRAFT" {
		t.Fatal("incorrect backend scope or activation")
	}
}

func TestDraftSyncIsIdempotentAndDoesNotReactivate(t *testing.T) {
	posts := 0
	rows := []policyRow{}
	p := testPlatform(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer test-token" || r.Header.Get("X-Tenant-Id") != "tenant" {
			t.Error("missing authentication/scope")
		}
		if r.Method == http.MethodGet {
			json.NewEncoder(w).Encode(rows)
			return
		}
		posts++
		var row policyRow
		if err := json.NewDecoder(r.Body).Decode(&row); err != nil {
			t.Error(err)
		}
		if row.Status != "DRAFT" {
			t.Error("operator must not activate policies")
		}
		row.ID = "saved"
		rows = append(rows, row)
		json.NewEncoder(w).Encode(row)
	})
	if _, err := p.syncDraft(context.Background(), "owned", 1, "policy text"); err != nil {
		t.Fatal(err)
	}
	rows[0].Status = "ACTIVE" // Review/activation in the dashboard must survive reconciliation.
	if _, err := p.syncDraft(context.Background(), "owned", 1, "policy text"); err != nil {
		t.Fatal(err)
	}
	if posts != 1 {
		t.Fatalf("duplicate policy created: %d", posts)
	}
	if _, err := p.syncDraft(context.Background(), "owned", 1, "changed text"); err == nil {
		t.Fatal("conflicting version accepted")
	}
}

func TestTokenRotationAndHTTPFailures(t *testing.T) {
	p := testPlatform(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer rotated" {
			t.Error("old token retained")
		}
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte("secret response"))
	})
	if err := os.WriteFile(p.tokenFile, []byte("rotated"), 0600); err != nil {
		t.Fatal(err)
	}
	_, err := p.syncDraft(context.Background(), "owned", 1, "text")
	if err == nil || err.Error() != "platform returned HTTP 401" {
		t.Fatalf("unsafe failure: %v", err)
	}
}

func TestReconcileRejectsUnsupportedControlsAndRetries(t *testing.T) {
	for _, legacy := range []bool{true, false} {
		t.Run(map[bool]string{true: "legacy", false: "unavailable"}[legacy], func(t *testing.T) {
			calls := 0
			p := testPlatform(t, func(w http.ResponseWriter, r *http.Request) { calls++; w.WriteHeader(503) })
			object := policyObject()
			object.SetName("test")
			object.SetNamespace("test")
			object.SetUID(types.UID("uid"))
			object.SetGeneration(1)
			spec := map[string]any{"policyText": "policy text"}
			if legacy {
				spec["attestationRequired"] = true
			}
			object.Object["spec"] = spec
			scheme := runtime.NewScheme()
			scheme.AddKnownTypeWithName(policyGVK, policyObject())
			c := fake.NewClientBuilder().WithScheme(scheme).WithStatusSubresource(object).WithObjects(object).Build()
			r := &reconciler{Client: c, platform: p}
			result, err := r.Reconcile(context.Background(), ctrl.Request{NamespacedName: types.NamespacedName{Namespace: "test", Name: "test"}})
			if err != nil {
				t.Fatal(err)
			}
			got := policyObject()
			if err := c.Get(context.Background(), types.NamespacedName{Namespace: "test", Name: "test"}, got); err != nil {
				t.Fatal(err)
			}
			phase, _, _ := unstructured.NestedString(got.Object, "status", "phase")
			if legacy && (phase != "Rejected" || calls != 0) {
				t.Fatal("unsupported controls reached API")
			}
			if !legacy && (phase != "Error" || result.RequeueAfter == 0) {
				t.Fatal("failure reported as success or no retry")
			}
		})
	}
}
