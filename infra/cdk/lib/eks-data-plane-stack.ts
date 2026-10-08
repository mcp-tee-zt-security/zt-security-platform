import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as eks from 'aws-cdk-lib/aws-eks';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { INFRA_CONFIG } from './infrastructure-config';

export class EksDataPlaneStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: { vpc: ec2.Vpc }) {
    super(scope, id);
    const cluster = new eks.Cluster(this, 'Cluster', {
      vpc: props.vpc,
      version: eks.KubernetesVersion.V1_33,
      defaultCapacity: 0,
      endpointAccess: eks.EndpointAccess.PUBLIC_AND_PRIVATE,
      clusterLogging: [eks.ClusterLoggingTypes.API, eks.ClusterLoggingTypes.AUDIT,
      eks.ClusterLoggingTypes.AUTHENTICATOR],
    });
    cluster.addNodegroupCapacity('System', {
      desiredSize: INFRA_CONFIG.eks.systemDesiredSize,
      minSize: INFRA_CONFIG.eks.systemMinSize,
      maxSize: INFRA_CONFIG.eks.systemMaxSize,
      instanceTypes: [new ec2.InstanceType(INFRA_CONFIG.eks.systemInstanceType)],
      amiType: eks.NodegroupAmiType.AL2023_ARM_64_STANDARD,
      diskSize: INFRA_CONFIG.eks.systemDiskSizeGiB,
    });
    const sa = cluster.addServiceAccount('DataPlaneServiceAccount', { name: 'policy-data-plane' });
    sa.role.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName('CloudWatchAgentServerPolicy'));
    new cdk.CfnOutput(this, 'EksClusterName', { value: cluster.clusterName });
    new cdk.CfnOutput(this, 'EksEndpoint', { value: cluster.clusterEndpoint });
  }
}
