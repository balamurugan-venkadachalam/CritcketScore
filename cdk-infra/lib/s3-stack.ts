import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';
import { KmsStack } from './kms-stack';

export interface S3StackProps extends cdk.StackProps {
    kmsStack: KmsStack;
}

/**
 * S3 Stack – Buckets for VPC flow logs, deployment artifacts, config.
 *
 * TASK-03.3 – implementation placeholder (expand in TASK-03)
 */
export class S3Stack extends cdk.Stack {
    public readonly flowLogsBucket: s3.IBucket;
    public readonly artifactsBucket: s3.IBucket;

    constructor(scope: Construct, id: string, props: S3StackProps) {
        super(scope, id, props);

        this.flowLogsBucket = new s3.Bucket(this, 'FlowLogsBucket', {
            bucketName: `t20-vpc-flow-logs-${this.account}-${this.region}`,
            encryption: s3.BucketEncryption.KMS,
            encryptionKey: props.kmsStack.s3Key,
            versioned: true,
            blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
            enforceSSL: true,
            lifecycleRules: [
                { expiration: cdk.Duration.days(90), id: 'ExpireFlowLogs90Days' },
            ],
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        this.artifactsBucket = new s3.Bucket(this, 'ArtifactsBucket', {
            bucketName: `t20-artifacts-${this.account}-${this.region}`,
            encryption: s3.BucketEncryption.KMS,
            encryptionKey: props.kmsStack.s3Key,
            versioned: true,
            blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
            enforceSSL: true,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        new cdk.CfnOutput(this, 'FlowLogsBucketName', { value: this.flowLogsBucket.bucketName, exportName: 'T20FlowLogsBucketName' });
        new cdk.CfnOutput(this, 'ArtifactsBucketName', { value: this.artifactsBucket.bucketName, exportName: 'T20ArtifactsBucketName' });
    }
}
