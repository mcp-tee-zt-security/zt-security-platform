import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import { Construct } from 'constructs';
import { INFRA_CONFIG } from './infrastructure-config';
export class CacheStack extends cdk.Stack{public readonly redis:elasticache.CfnCacheCluster;
    constructor(scope:Construct,id:string,p:{vpc:ec2.Vpc}){super(scope,id);
        const sg=new ec2.SecurityGroup(this,'RedisSg',{vpc:p.vpc});
        sg.addIngressRule(ec2.Peer.ipv4(p.vpc.vpcCidrBlock),
        ec2.Port.tcp(INFRA_CONFIG.cache.port),'VPC Redis access');
        const subnet=new elasticache.CfnSubnetGroup(this,
        'RedisSubnet',{description:'ZT Redis private subnet group',subnetIds:p.vpc.privateSubnets.map(x=>x.subnetId)}
        );
        this.redis=new elasticache.CfnCacheCluster(this,'Redis',{cacheNodeType: INFRA_CONFIG.cache.nodeType,
            engine:'redis',numCacheNodes: INFRA_CONFIG.cache.nodeCount,cacheSubnetGroupName:subnet.ref,vpcSecurityGroupIds:[sg.securityGroupId]}
        );
        }}
