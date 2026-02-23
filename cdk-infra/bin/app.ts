#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { KmsStack } from '../lib/kms-stack';
import { VpcStack } from '../lib/vpc-stack';
import { SubnetStack } from '../lib/subnet-stack';
import { SecurityGroupsStack } from '../lib/security-groups-stack';
import { SecretsManagerStack } from '../lib/secrets-manager-stack';
import { MskStack } from '../lib/msk-stack';
import { DynamoDbStack } from '../lib/dynamodb-stack';
import { S3Stack } from '../lib/s3-stack';
import { EcrStack } from '../lib/ecr-stack';
import { EcsClusterStack } from '../lib/ecs-cluster-stack';
import { AlbStack } from '../lib/alb-stack';
import { IamStack } from '../lib/iam-stack';
import { EcsProducerServiceStack } from '../lib/ecs-producer-service-stack';
import { EcsConsumerServiceStack } from '../lib/ecs-consumer-service-stack';
import { CloudWatchStack } from '../lib/cloudwatch-stack';
import { WafStack } from '../lib/waf-stack';

const app = new cdk.App();

// ─── Environment ─────────────────────────────────────────────────────────────
const env: cdk.Environment = {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'ap-southeast-2',
};

const stackProps: cdk.StackProps = { env, terminationProtection: true };

// ─── Layer 0: Encryption ─────────────────────────────────────────────────────
const kmsStack = new KmsStack(app, 'T20KmsStack', stackProps);

// ─── Layer 1: Networking ─────────────────────────────────────────────────────
const vpcStack = new VpcStack(app, 'T20VpcStack', { ...stackProps, kmsStack });
vpcStack.addDependency(kmsStack);

const subnetStack = new SubnetStack(app, 'T20SubnetStack', { ...stackProps, vpcStack });
subnetStack.addDependency(vpcStack);

const sgStack = new SecurityGroupsStack(app, 'T20SecurityGroupsStack', {
    ...stackProps,
    vpcStack,
});
sgStack.addDependency(subnetStack);

// ─── Layer 2: Security & Secrets ─────────────────────────────────────────────
const secretsStack = new SecretsManagerStack(app, 'T20SecretsManagerStack', {
    ...stackProps,
    kmsStack,
});
secretsStack.addDependency(kmsStack);

// ─── Layer 3: Data & Messaging ───────────────────────────────────────────────
const mskStack = new MskStack(app, 'T20MskStack', {
    ...stackProps,
    vpcStack,
    sgStack,
});
mskStack.addDependency(sgStack);

const dynamoStack = new DynamoDbStack(app, 'T20DynamoDbStack', {
    ...stackProps,
    kmsStack,
});
dynamoStack.addDependency(kmsStack);

const s3Stack = new S3Stack(app, 'T20S3Stack', { ...stackProps, kmsStack });
s3Stack.addDependency(kmsStack);

// ─── Layer 4: Container Registry ─────────────────────────────────────────────
const ecrStack = new EcrStack(app, 'T20EcrStack', stackProps);

// ─── Layer 5: IAM ────────────────────────────────────────────────────────────
const iamStack = new IamStack(app, 'T20IamStack', {
    ...stackProps,
    mskStack,
    dynamoStack,
    secretsStack,
});
iamStack.addDependency(dynamoStack);
iamStack.addDependency(secretsStack);

// ─── Layer 6: Compute ────────────────────────────────────────────────────────
const ecsClusterStack = new EcsClusterStack(app, 'T20EcsClusterStack', {
    ...stackProps,
    vpcStack,
});
ecsClusterStack.addDependency(sgStack);

const albStack = new AlbStack(app, 'T20AlbStack', {
    ...stackProps,
    vpcStack,
    sgStack,
});
albStack.addDependency(ecsClusterStack);

const ecsProducerStack = new EcsProducerServiceStack(app, 'T20EcsProducerServiceStack', {
    ...stackProps,
    vpcStack,
    ecsClusterStack,
    ecrStack,
    iamStack,
    mskStack,
    secretsStack,
    albStack,
});
ecsProducerStack.addDependency(albStack);
ecsProducerStack.addDependency(iamStack);

const ecsConsumerStack = new EcsConsumerServiceStack(app, 'T20EcsConsumerServiceStack', {
    ...stackProps,
    vpcStack,
    ecsClusterStack,
    ecrStack,
    iamStack,
    mskStack,
    dynamoStack,
    secretsStack,
});
ecsConsumerStack.addDependency(ecsClusterStack);
ecsConsumerStack.addDependency(iamStack);

// ─── Layer 7: Observability & Security ───────────────────────────────────────
const cloudWatchStack = new CloudWatchStack(app, 'T20CloudWatchStack', {
    ...stackProps,
    ecsProducerStack,
    ecsConsumerStack,
    mskStack,
    albStack,
    kmsStack,
});
cloudWatchStack.addDependency(ecsConsumerStack);
cloudWatchStack.addDependency(ecsProducerStack);

const wafStack = new WafStack(app, 'T20WafStack', {
    ...stackProps,
    albStack,
});
wafStack.addDependency(albStack);

app.synth();
