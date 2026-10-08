package main

import (
	"context"
	"os"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
)

type ZeroTrustPolicy struct {
	Spec   ZeroTrustPolicySpec   `json:"spec,omitempty"`
	Status ZeroTrustPolicyStatus `json:"status,omitempty"`
}
type ZeroTrustPolicySpec struct {
	Workload            string   `json:"workload"`
	IdentityRequired    bool     `json:"identityRequired"`
	AttestationRequired bool     `json:"attestationRequired"`
	Capabilities        []string `json:"capabilities,omitempty"`
	Decision            string   `json:"decision,omitempty"`
	ApprovalRequired    bool     `json:"approvalRequired"`
}
type ZeroTrustPolicyStatus struct {
	Phase        string `json:"phase,omitempty"`
	ObservedHash string `json:"observedHash,omitempty"`
}

func reconcile(ctx context.Context, c client.Client, p *ZeroTrustPolicy) error { return nil }
func main() {
	ctrl.SetLogger(zap.New(zap.UseDevMode(true)))
	_ = context.Background()
	if err := ctrl.SetupSignalHandler().Err(); err != nil {
		os.Exit(1)
	}
}
