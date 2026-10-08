package ztsecurity

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	BaseURL, APIKey, TenantID, WorkspaceID string
	HTTP                                   *http.Client
	Retries                                int
}
type Error struct {
	Status    int
	RequestID string
	Message   string
}

func (e *Error) Error() string { return fmt.Sprintf("zt security http %d: %s", e.Status, e.Message) }
func New(baseURL, apiKey, tenantID string) *Client {
	return &Client{BaseURL: strings.TrimRight(baseURL, "/"), APIKey: apiKey, TenantID: tenantID, HTTP: &http.Client{Timeout: 10 * time.Second}, Retries: 2}
}
func (c *Client) Do(ctx context.Context, method, path string, in, out any, idempotency string) error {
	var b []byte
	var err error
	if in != nil {
		b, err = json.Marshal(in)
		if err != nil {
			return err
		}
	}
	for i := 0; i <= c.Retries; i++ {
		req, er := http.NewRequestWithContext(ctx, method, c.BaseURL+path, bytes.NewReader(b))
		if er != nil {
			return er
		}
		req.Header.Set("content-type", "application/json")
		req.Header.Set("x-api-key", c.APIKey)
		req.Header.Set("x-tenant-id", c.TenantID)
		if c.WorkspaceID != "" {
			req.Header.Set("x-workspace-id", c.WorkspaceID)
		}
		if idempotency != "" {
			req.Header.Set("idempotency-key", idempotency)
		}
		r, er := c.HTTP.Do(req)
		if er != nil {
			err = er
		} else {
			raw, _ := io.ReadAll(r.Body)
			r.Body.Close()
			if r.StatusCode >= 200 && r.StatusCode < 300 {
				if out != nil && len(raw) > 0 {
					return json.Unmarshal(raw, out)
				}
				return nil
			}
			err = &Error{Status: r.StatusCode, RequestID: r.Header.Get("x-request-id"), Message: string(raw)}
			if r.StatusCode != 429 && r.StatusCode != 502 && r.StatusCode != 503 && r.StatusCode != 504 {
				return err
			}
		}
		if i < c.Retries {
			time.Sleep(time.Duration(250*(1<<i)) * time.Millisecond)
		}
	}
	return err
}
func (c *Client) Evaluate(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/actions/evaluate", in, out, fmt.Sprintf("sdk-%d", time.Now().UnixNano()))
}
func (c *Client) CreateExecutionContract(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/execution/contracts", in, out, "")
}
func (c *Client) ExecuteContract(ctx context.Context, id string, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/execution/contracts/"+id+"/execute", map[string]any{}, out, "")
}
func (c *Client) VerifyExecution(ctx context.Context, id string, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/execution/"+id+"/verify", map[string]any{}, out, "")
}

func (c *Client) CreateEvidence(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/evidence", in, out, "")
}
func (c *Client) RequestApproval(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/approvals", in, out, "")
}
func (c *Client) Approve(ctx context.Context, id string, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/approvals/"+id+"/approve", in, out, "")
}
func (c *Client) RegisterAgent(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/agents", in, out, "")
}
func (c *Client) CreateMission(ctx context.Context, agentID string, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/agents/"+agentID+"/missions", in, out, "")
}
func (c *Client) CreateSimulation(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/simulations", in, out, "")
}
func (c *Client) RunSimulation(ctx context.Context, id string, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/simulations/"+id+"/run", in, out, "")
}
func (c *Client) ApplyKubernetesPolicy(ctx context.Context, in map[string]any, out *map[string]any) error {
	return c.Do(ctx, http.MethodPost, "/v1/kubernetes/policies", in, out, "")
}

func (c *Client) ObservabilityMetrics(ctx context.Context, out *map[string]any) error {
	return c.Do(ctx, http.MethodGet, "/v1/observability/metrics", nil, out, "")
}

func (c *Client) ReplayEvents(ctx context.Context, traceID string, limit int, out *[]map[string]any) error {
	path := fmt.Sprintf("/v1/observability/events?limit=%d", limit)
	if traceID != "" {
		path += "&traceId=" + url.QueryEscape(traceID)
	}
	return c.Do(ctx, http.MethodGet, path, nil, out, "")
}
