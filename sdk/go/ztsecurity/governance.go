package ztsecurity

import "context"

type ActionContext struct {
	Subject, Action, Resource, TenantID string
	Attributes                          map[string]any
}
type GovernedResult struct{ Decision, Evidence, Approval, Contract, Execution, Verification map[string]any }

func (c *Client) GovernedExecute(ctx context.Context, a ActionContext, evidence, approval map[string]any) (GovernedResult, error) {
	var d map[string]any
	if err := c.Evaluate(ctx, map[string]any{"subject": a.Subject, "action": a.Action, "resource": a.Resource, "tenantId": a.TenantID, "attributes": a.Attributes}, &d); err != nil {
		return GovernedResult{}, err
	}
	if d["decision"] == "DENY" {
		return GovernedResult{Decision: d}, nil
	}
	var con map[string]any
	if err := c.CreateExecutionContract(ctx, map[string]any{"action": a.Action, "resource": a.Resource, "policyDecision": d, "evidence": evidence, "approval": approval}, &con); err != nil {
		return GovernedResult{Decision: d}, err
	}
	var ex map[string]any
	if err := c.ExecuteContract(ctx, toString(con["id"]), &ex); err != nil {
		return GovernedResult{Decision: d, Contract: con}, err
	}
	var v map[string]any
	if err := c.VerifyExecution(ctx, toString(ex["id"]), &v); err != nil {
		return GovernedResult{Decision: d, Contract: con, Execution: ex}, err
	}
	return GovernedResult{Decision: d, Evidence: evidence, Approval: approval, Contract: con, Execution: ex, Verification: v}, nil
}
func toString(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}
