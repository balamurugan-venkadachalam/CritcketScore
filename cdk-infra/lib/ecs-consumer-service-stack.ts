import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';
import { EcsClusterStack } from './ecs-cluster-stack';
import { EcrStack } from './ecr-stack';
import { IamStack } from './iam-stack';
import { MskStack } from './msk-stack';
import { DynamoDbStack } from './dynamodb-stack';
import { SecretsManagerStack } from './secrets-manager-stack';

export interface EcsConsumerServiceStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
    ecsClusterStack: EcsClusterStack;
    ecrStack: EcrStack;
    iamStack: IamStack;
    mskStack: MskStack;
    dynamoStack: DynamoDbStack;
    secretsStack: SecretsManagerStack;
}

/**
 * ECS Consumer Service Stack – 24 Fargate tasks × 8 threads = 192 consumer threads.
 * 2 vCPU / 4 GB RAM per task. Autoscales on Kafka consumer lag.
 *
 * TASK-03.7 – implementation placeholder (expand in TASK-03)
 */
export class EcsConsumerServiceStack extends cdk.Stack {
    public readonly service: ecs.FargateService;

    constructor(scope: Construct, id: string, props: EcsConsumerServiceStackProps) {
        super(scope, id, props);

        const taskDef = new ecs.FargateTaskDefinition(this, 'ConsumerTaskDef', {
            cpu: 2048,
            memoryLimitMiB: 4096,
            taskRole: props.iamStack.consumerTaskRole,
        });

        taskDef.addContainer('ConsumerContainer', {
            image: ecs.ContainerImage.fromEcrRepository(props.ecrStack.consumerRepo, 'latest'),
            containerName: 't20-score-consumer',
            portMappings: [{ containerPort: 8082 }],
            environment: {
                SPRING_PROFILES_ACTIVE: 'prod',
                AWS_REGION: this.region,
                // concurrency=8 threads per pod (192 threads total for 192 partitions)
                KAFKA_CONCURRENCY: '8',
                DYNAMODB_TABLE_EVENTS: 't20-score-events',
                DYNAMODB_TABLE_LIVE_SCORES: 't20-live-scores',
                DYNAMODB_TABLE_REPLAY_STATE: 't20-replay-state',
            },
            logging: ecs.LogDrivers.awsLogs({ streamPrefix: 't20-consumer' }),
        });

        this.service = new ecs.FargateService(this, 'ConsumerService', {
            cluster: props.ecsClusterStack.cluster,
            taskDefinition: taskDef,
            serviceName: 't20-score-consumer-service',
            desiredCount: 24,          // 24 tasks × 8 threads = 192 (= partition count)
            minHealthyPercent: 100,
            maxHealthyPercent: 200,
            assignPublicIp: false,
        });

        // Autoscaling on consumer lag (custom metric wired in TASK-03.7 & TASK-04)
        const scaling = this.service.autoScaleTaskCount({ minCapacity: 24, maxCapacity: 48 });
        scaling.scaleOnCpuUtilization('CpuScaling', { targetUtilizationPercent: 70 });

        new cdk.CfnOutput(this, 'ConsumerServiceName', {
            value: this.service.serviceName,
            exportName: 'T20ConsumerServiceName',
        });
    }
}
