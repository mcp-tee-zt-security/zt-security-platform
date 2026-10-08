import { ZtClient } from "@zt-security/sdk";

const client = new ZtClient({
  baseUrl: "http://localhost:8080",
  apiKey: "dev-master-key",
  tenantId: "11111111-1111-1111-1111-111111111111",
  workspaceId: "88888888-8888-8888-8888-888888888801",
});

const result = await client.governance.execute({
  subject: "payment-agent",
  action: "payment.transfer",
  resource: "bank_account/ACC-1001",
  tenantId: "11111111-1111-1111-1111-111111111111",
  attributes: { amount: 100000, task_id: "payment-demo-task",
    tool_id: "33333333-3333-3333-3333-333333333301" },
});

console.log(result);
