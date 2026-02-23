import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';
import { EcsProducerServiceStack } from './ecs-producer-service-stack';
import { EcsConsumerServiceStack } from './ecs-consumer-service-stack';
import { MskStack } from './msk-stack';
import { AlbStack } from './alb-stack';
import { KmsStack } from './kms-stack';

export interface CloudWatchStackProps extends cdk.StackProps {
    ecsProducerStack: EcsProducerServiceStack;
    ecsConsumerStack: EcsConsumerServiceStack;
    mskStack: MskStack;
    albStack: AlbStack;
    kmsStack: KmsStack;
}

/**
 * CloudWatch Stack – Dashboards, Alarms, and Log Groups.
 *
 * TASK-04.2 / 04.3 / 04.4 – implementation placeholder (expand in TASK-04)
 */
export class CloudWatchStack extends cdk.Stack {
    public readonly producerLogGroup: logs.ILogGroup;
    public readonly consumerLogGroup: logs.ILogGroup;

    constructor(scope: Construct, id: string, props: CloudWatchStackProps) {
        super(scope, id, props);

        // Log groups
        this.producerLogGroup = new logs.LogGroup(this, 'ProducerLogGroup', {
            logGroupName: '/t20/producer',
            retention: logs.RetentionDays.ONE_MONTH,
            encryptionKey: props.kmsStack.logsKey,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        this.consumerLogGroup = new logs.LogGroup(this, 'ConsumerLogGroup', {
            logGroupName: '/t20/consumer',
            retention: logs.RetentionDays.ONE_MONTH,
            encryptionKey: props.kmsStack.logsKey,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        // Dashboard placeholder – panels added in TASK-04.2
        new cloudwatch.Dashboard(this, 'T20Dashboard', {
            dashboardName: 'T20-Live-Scoring',
        });

        new cdk.CfnOutput(this, 'ProducerLogGroupName', { value: this.producerLogGroup.logGroupName });
        new cdk.CfnOutput(this, 'ConsumerLogGroupName', { value: this.consumerLogGroup.logGroupName });
    }
}
