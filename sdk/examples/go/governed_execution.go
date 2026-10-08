package main

import (
	"context"
	"fmt"
	zt "github.com/zt-security/platform/sdk/go/ztsecurity"
)

func main() {
	c := zt.New("http://localhost:8080", "dev-master-key", "11111111-1111-1111-1111-111111111111")
	c.WorkspaceID = "88888888-8888-8888-8888-888888888801"
	r, err := c.GovernedExecute(context.Background(), zt.ActionContext{Subject: "payment-agent", Action: "payment.transfer", Resource: "bank_account/ACC-1001", TenantID: c.TenantID, Attributes: map[string]any{"amount": 100000, "task_id": "payment-demo-task", "tool_id": "33333333-3333-3333-3333-333333333301"}}, map[string]any{"type": "threat-observation"}, nil)
	fmt.Printf("%+v %v\n", r, err)
}
