#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import{NetworkStack}from'../lib/network-stack';
import{EksDataPlaneStack}from'../lib/eks-data-plane-stack';
import{DatabaseStack}
from'../lib/database-stack';
import{CacheStack}from'../lib/cache-stack';
import{ApplicationStack}from'../lib/application-stack';
import{SecurityStack}
from'../lib/security-stack';
const app=new cdk.App();
const n=new NetworkStack(app,'ZtNetwork');
const d=new DatabaseStack(app,
'ZtDatabase',{vpc:n.vpc});
const r=new CacheStack(app,'ZtCache',{vpc:n.vpc}
);
new ApplicationStack(app,'ZtApplication',{vpc:n.vpc,db:d.db,redis:r.redis}
);
new SecurityStack(app,'ZtSecurity');
if(app.node.tryGetContext('enableEks'))new EksDataPlaneStack(app,
'ZtEksDataPlane',{vpc:n.vpc});
