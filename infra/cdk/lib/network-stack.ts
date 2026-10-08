import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { INFRA_CONFIG } from './infrastructure-config';
export class NetworkStack extends cdk.Stack{public readonly vpc:ec2.Vpc;
    constructor(scope:cdk.App,id:string,props?:cdk.StackProps){super(scope,
        id,props);
        this.vpc=new ec2.Vpc(this,'Vpc',{
          maxAzs: INFRA_CONFIG.network.maxAzs,
          natGateways: INFRA_CONFIG.network.natGateways,subnetConfiguration:[{
name:'public',subnetType:ec2.SubnetType.PUBLIC},{name:'private',subnetType:ec2.SubnetType.PRIVATE_WITH_EGRESS}
            ]});
            }}
