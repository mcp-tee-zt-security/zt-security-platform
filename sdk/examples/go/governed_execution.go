package main

import (
	"context"
	"fmt"
	zt "github.com/zt-security/platform/sdk/go/ztsecurity"
)

func main() {
	c := zt.New("http://localhost:8080", "demo-key", "demo")
	r, err := c.GovernedExecute(context.Background(), zt.ActionContext{Subject: "agent:soc-01", Action: "isolate-workload", Resource: "workload:payments", TenantID: "demo"}, map[string]any{"type": "threat-observation"}, nil)
	fmt.Printf("%+v %v\n", r, err)
}
