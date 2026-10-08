import * as cdk from "aws-cdk-lib";
import * as s3 from "aws-cdk-lib/aws-s3";
import { Construct } from "constructs";
import { INFRA_CONFIG } from './infrastructure-config';
export class SecurityStack extends cdk.Stack {
  public readonly auditBucket: s3.Bucket;
  constructor(scope: Construct,id:string,props?:cdk.StackProps){super(scope,id,props);
    this.auditBucket=new s3.Bucket(this,"AuditArchive",{
      encryption:s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess:s3.BlockPublicAccess.BLOCK_ALL,
      objectLockEnabled:true,
      objectLockDefaultRetention:{mode:s3.ObjectLockMode.COMPLIANCE,days: INFRA_CONFIG.audit.objectLockRetentionDays},
      versioned:true,
      enforceSSL:true,
      removalPolicy:cdk.RemovalPolicy.RETAIN
    });
  }
}
