import * as cdk from 'aws-cdk-lib';
import * as kms from 'aws-cdk-lib/aws-kms';
import { Construct } from 'constructs';

/**
 * KMS Stack – Customer Managed Keys for encryption at rest.
 * Used by: DynamoDB, S3, Secrets Manager, CloudWatch Logs.
 *
 * TASK-02.1 – implementation placeholder (expand in TASK-02)
 */
export class KmsStack extends cdk.Stack {
    /** CMK for DynamoDB table encryption */
    public readonly dynamoDbKey: kms.IKey;
    /** CMK for Secrets Manager encryption */
    public readonly secretsKey: kms.IKey;
    /** CMK for CloudWatch Logs encryption */
    public readonly logsKey: kms.IKey;
    /** CMK for S3 bucket encryption */
    public readonly s3Key: kms.IKey;

    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        // Placeholder keys – fully implemented in TASK-02.1
        this.dynamoDbKey = new kms.Key(this, 'DynamoDbKey', {
            description: 'T20 DynamoDB encryption key',
            enableKeyRotation: true,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        this.secretsKey = new kms.Key(this, 'SecretsKey', {
            description: 'T20 Secrets Manager encryption key',
            enableKeyRotation: true,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        this.logsKey = new kms.Key(this, 'LogsKey', {
            description: 'T20 CloudWatch Logs encryption key',
            enableKeyRotation: true,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        this.s3Key = new kms.Key(this, 'S3Key', {
            description: 'T20 S3 bucket encryption key',
            enableKeyRotation: true,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        // Exports
        new cdk.CfnOutput(this, 'DynamoDbKeyArn', { value: this.dynamoDbKey.keyArn, exportName: 'T20DynamoDbKeyArn' });
        new cdk.CfnOutput(this, 'SecretsKeyArn', { value: this.secretsKey.keyArn, exportName: 'T20SecretsKeyArn' });
        new cdk.CfnOutput(this, 'LogsKeyArn', { value: this.logsKey.keyArn, exportName: 'T20LogsKeyArn' });
        new cdk.CfnOutput(this, 'S3KeyArn', { value: this.s3Key.keyArn, exportName: 'T20S3KeyArn' });
    }
}
