import { ZtClient } from "@zt-security/sdk";

const client = new ZtClient({
  baseUrl: "http://localhost:8080",
  apiKey: "example-key",
  tenantId: "tenant-a",
});

const result = await client.governance.execute({
  subject: "workload/payment-api",
  action: "rotate-credential",
  resource: "secret/payment-db",
  tenantId: "tenant-a",
});

console.log(result);
