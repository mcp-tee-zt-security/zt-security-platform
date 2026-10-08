-- Add narrowly scoped allow policies for the existing local demo tenant.
-- Keep V1 unchanged so existing databases retain a valid Flyway checksum.
SELECT set_config('app.tenant_id', '11111111-1111-1111-1111-111111111111', true);
SELECT set_config('app.workspace_id', '88888888-8888-8888-8888-888888888801', true);

INSERT INTO policies
    (tenant_id, workspace_id, name, version, status, priority, effect, policy_text, created_by)
SELECT '11111111-1111-1111-1111-111111111111'::uuid,
       '88888888-8888-8888-8888-888888888801'::uuid,
       seed.name, 1, 'ACTIVE', 100, 'allow', seed.policy_text, 'demo'
FROM (VALUES
    ('demo_payment_allow', $policy$policy "demo_payment_allow" {
 priority 100
 effect allow
 principal.type == "AI_AGENT"
 action == "payment.transfer"
 resource.type == "bank_account"
 condition {
  principal.id == "payment-agent" and context.amount > 0 and context.amount <= 10000000
 }
}$policy$),
    ('demo_refund_allow', $policy$policy "demo_refund_allow" {
 priority 100
 effect allow
 principal.type == "AI_AGENT"
 action == "refund.create"
 resource.type == "customer_record"
 condition {
  principal.id == "refund-agent" and context.amount > 0 and context.amount <= 500000
 }
}$policy$)
) AS seed(name, policy_text)
WHERE EXISTS (SELECT 1 FROM tenants WHERE id = '11111111-1111-1111-1111-111111111111'::uuid)
  AND EXISTS (SELECT 1 FROM workspaces WHERE id = '88888888-8888-8888-8888-888888888801'::uuid)
ON CONFLICT (tenant_id, name, version) DO NOTHING;
