import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import { INFRA_CONFIG } from './infrastructure-config';
export class DatabaseStack extends cdk.Stack{
    public readonly db:rds.DatabaseInstance;
    constructor(scope:cdk.App,id:string,
    p:{vpc:ec2.Vpc}){super(scope,id);
        this.db=new rds.DatabaseInstance(this,
        'Postgres',{engine:rds.DatabaseInstanceEngine.postgres({version:rds.PostgresEngineVersion.VER_16}
            ),vpc:p.vpc,vpcSubnets:{subnetType:ec2.SubnetType.PRIVATE_WITH_EGRESS}
            ,instanceType:ec2.InstanceType.of(ec2.InstanceClass.T4G,ec2.InstanceSize.MICRO),
            databaseName: INFRA_CONFIG.database.name,multiAz:false,publiclyAccessible:false,allocatedStorage: INFRA_CONFIG.database.allocatedStorageGiB,
            storageEncrypted:true,backupRetention:cdk.Duration.days(INFRA_CONFIG.database.backupRetentionDays),deletionProtection:false,
            credentials:rds.Credentials.fromGeneratedSecret('ztadmin')});
            }}
