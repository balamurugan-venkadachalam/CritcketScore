import * as cdk from 'aws-cdk-lib';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { MskStack } from './msk-stack';
import { DynamoDbStack } from './dynamodb-stack';
import { SecretsManagerStack } from './secrets-manager-stack';

export interface IamStackProps extends cdk.StackProps {
    mskStack: MskStack;
    dynamoStack: DynamoDbStack;
    secretsStack: SecretsManagerStack;
}

/**
 * IAM Stack – Least-privilege task roles for producer and consumer.
 *
 * TASK-04.1 – implementation placeholder (expand in TASK-04)
 */
export class IamStack extends cdk.Stack {
    public readonly producerTaskRole: iam.IRole;
    public readonly consumerTaskRole: iam.IRole;

    constructor(scope: Construct, id: string, props: IamStackProps) {
        super(scope, id, props);

        // Producer: MSK write + Secrets read
        this.producerTaskRole = new iam.Role(this, 'ProducerTaskRole', {
            roleName: 't20-producer-task-role',
            assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
            description: 'Least-privilege role for T20 Score Producer ECS task',
        });

        // Consumer: MSK read + DynamoDB read/write + Secrets read + SNS publish
        this.consumerTaskRole = new iam.Role(this, 'ConsumerTaskRole', {
            roleName: 't20-consumer-task-role',
            assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
            description: 'Least-privilege role for T20 Score Consumer ECS task',
        });

        // Placeholder policies – fully scoped in TASK-04.1
        props.secretsStack.mskProducerSecret.grantRead(this.producerTaskRole);
        props.secretsStack.mskConsumerSecret.grantRead(this.consumerTaskRole);
        props.dynamoStack.scoreEventsTable.grantReadWriteData(this.consumerTaskRole);
        props.dynamoStack.liveScoresTable.grantReadWriteData(this.consumerTaskRole);
        props.dynamoStack.replayStateTable.grantReadWriteData(this.consumerTaskRole);

        new cdk.CfnOutput(this, 'ProducerTaskRoleArn', { value: this.producerTaskRole.roleArn, exportName: 'T20ProducerTaskRoleArn' });
        new cdk.CfnOutput(this, 'ConsumerTaskRoleArn', { value: this.consumerTaskRole.roleArn, exportName: 'T20ConsumerTaskRoleArn' });
    }
}
