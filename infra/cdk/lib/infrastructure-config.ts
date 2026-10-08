export const INFRA_CONFIG = {
  network: {
    maxAzs: 2,
    natGateways: 1,
  },
  application: {
    cpu: 512,
    memoryLimitMiB: 1024,
    desiredCount: 1,
    containerPort: 8080,
    loadBalancerPort: 80,
    redisPort: 6379,
  },
  database: {
    name: 'zt',
    allocatedStorageGiB: 20,
    backupRetentionDays: 7,
  },
  cache: {
    nodeType: 'cache.t4g.micro',
    nodeCount: 1,
    port: 6379,
  },
  eks: {
    systemDesiredSize: 3,
    systemMinSize: 3,
    systemMaxSize: 8,
    systemDiskSizeGiB: 50,
    systemInstanceType: 'm7g.large',
  },
  audit: {
    objectLockRetentionDays: 365,
  },
} as const;
