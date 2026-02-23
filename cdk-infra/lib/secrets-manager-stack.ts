import * as cdk from 'aws-cdk-lib';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { KmsStack } from './kms-stack';

export interface SecretsManagerStackProps extends cdk.StackProps {
    kmsStack: KmsStack;
}

/**
 * Secrets Manager Stack – Stores Kafka, DB, and API credentials.
 *
 * TASK-02.5 – implementation placeholder (expand in TASK-02)
 */
export class SecretsManagerStack extends cdk.Stack {
    public readonly mskProducerSecret: secretsmanager.ISecret;
    public readonly mskConsumerSecret: secretsmanager.ISecret;

    constructor(scope: Construct, id: string, props: SecretsManagerStackProps) {
        super(scope, id, props);

        this.mskProducerSecret = new secretsmanager.Secret(this, 'MskProducerSecret', {
            secretName: 't20/producer/msk-credentials',
            description: 'MSK credentials for T20 Score Producer',
            encryptionKey: props.kmsStack.secretsKey,
        });

        this.mskConsumerSecret = new secretsmanager.Secret(this, 'MskConsumerSecret', {
            secretName: 't20/consumer/msk-credentials',
            description: 'MSK credentials for T20 Score Consumer',
            encryptionKey: props.kmsStack.secretsKey,
        });

        new cdk.CfnOutput(this, 'MskProducerSecretArn', {
            value: this.mskProducerSecret.secretArn,
            exportName: 'T20MskProducerSecretArn',
        });
        new cdk.CfnOutput(this, 'MskConsumerSecretArn', {
            value: this.mskConsumerSecret.secretArn,
            exportName: 'T20MskConsumerSecretArn',
        });
    }
}
