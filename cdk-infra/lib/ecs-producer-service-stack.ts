import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';
import { EcsClusterStack } from './ecs-cluster-stack';
import { EcrStack } from './ecr-stack';
import { IamStack } from './iam-stack';
import { MskStack } from './msk-stack';
import { SecretsManagerStack } from './secrets-manager-stack';
import { AlbStack } from './alb-stack';

export interface EcsProducerServiceStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
    ecsClusterStack: EcsClusterStack;
    ecrStack: EcrStack;
    iamStack: IamStack;
    mskStack: MskStack;
    secretsStack: SecretsManagerStack;
    albStack: AlbStack;
}

/**
 * ECS Producer Service Stack – Fargate service for the score producer.
 * 1 vCPU / 2 GB RAM, ALB-integrated, CPU autoscaling.
 *
 * TASK-03.6 – implementation placeholder (expand in TASK-03)
 */
export class EcsProducerServiceStack extends cdk.Stack {
    public readonly service: ecs.FargateService;

    constructor(scope: Construct, id: string, props: EcsProducerServiceStackProps) {
        super(scope, id, props);

        const taskDef = new ecs.FargateTaskDefinition(this, 'ProducerTaskDef', {
            cpu: 1024,
            memoryLimitMiB: 2048,
            taskRole: props.iamStack.producerTaskRole,
        });

        taskDef.addContainer('ProducerContainer', {
            image: ecs.ContainerImage.fromEcrRepository(props.ecrStack.producerRepo, 'latest'),
            containerName: 't20-score-producer',
            portMappings: [{ containerPort: 8081 }],
            environment: {
                SPRING_PROFILES_ACTIVE: 'prod',
                AWS_REGION: this.region,
            },
            logging: ecs.LogDrivers.awsLogs({ streamPrefix: 't20-producer' }),
        });

        this.service = new ecs.FargateService(this, 'ProducerService', {
            cluster: props.ecsClusterStack.cluster,
            taskDefinition: taskDef,
            serviceName: 't20-score-producer-service',
            desiredCount: 2,
            minHealthyPercent: 100,
            maxHealthyPercent: 200,
            assignPublicIp: false,
        });

        new cdk.CfnOutput(this, 'ProducerServiceName', {
            value: this.service.serviceName,
            exportName: 'T20ProducerServiceName',
        });
    }
}
