import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as path from 'path';
import { INFRA_CONFIG } from './infrastructure-config';
export class ApplicationStack extends cdk.Stack{constructor(scope:cdk.App,
    id:string,p:{vpc:ec2.Vpc,db:rds.DatabaseInstance,redis:elasticache.CfnCacheCluster}
    ){super(scope,id);
        const cluster=new ecs.Cluster(this,'Cluster',{vpc:p.vpc}
        );
        const task=new ecs.FargateTaskDefinition(this,'Task',{
          cpu: INFRA_CONFIG.application.cpu,
          memoryLimitMiB: INFRA_CONFIG.application.memoryLimitMiB,
        }
        );
        const c=task.addContainer('Api',{image:ecs.ContainerImage.fromAsset(path.join(__dirname,
            '../../../'),{file:'apps/authorization-api/Dockerfile'}),environment:{
                SPRING_PROFILES_ACTIVE:'prod',REDIS_HOST:p.redis.attrRedisEndpointAddress,
                REDIS_PORT:String(INFRA_CONFIG.application.redisPort),DB_URL:`jdbc:postgresql://${p.db.attrEndpointAddress}:${p.db.attrEndpointPort}/zt`},
                secrets:{DB_PASSWORD:ecs.Secret.fromSecretsManager(p.db.secret!,
                'password'),DB_USERNAME:ecs.Secret.fromSecretsManager(p.db.secret!,
                'username')},logging:ecs.LogDrivers.awsLogs({streamPrefix:'zt-api'})});
                c.addPortMappings({ containerPort: INFRA_CONFIG.application.containerPort });
                p.db.connections.allowDefaultPortFrom(task.taskDefinition);
                const
svc=new ecs.FargateService(this,'Service',{cluster,taskDefinition:task,
desiredCount: INFRA_CONFIG.application.desiredCount,assignPublicIp:false,vpcSubnets:{subnetType:ec2.SubnetType.PRIVATE_WITH_EGRESS}});
const
alb=new elbv2.ApplicationLoadBalancer(this,'Alb',{vpc:p.vpc,internetFacing:true});
const
listener=alb.addListener('Http', { port: INFRA_CONFIG.application.loadBalancerPort });
listener.addTargets('Api',{port: INFRA_CONFIG.application.containerPort,targets:[svc],healthCheck:{path:'/v1/health'}});
new
cdk.CfnOutput(this,'Endpoint',{value:`http://${alb.loadBalancerDnsName}`});
}}
