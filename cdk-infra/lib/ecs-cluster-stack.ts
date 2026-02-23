import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';

export interface EcsClusterStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
}

/**
 * ECS Cluster Stack – Fargate cluster with Container Insights.
 *
 * TASK-03.5 – implementation placeholder (expand in TASK-03)
 */
export class EcsClusterStack extends cdk.Stack {
    public readonly cluster: ecs.ICluster;

    constructor(scope: Construct, id: string, props: EcsClusterStackProps) {
        super(scope, id, props);

        this.cluster = new ecs.Cluster(this, 'T20EcsCluster', {
            clusterName: 't20-score-cluster',
            vpc: props.vpcStack.vpc,
            containerInsights: true,
            enableFargateCapacityProviders: true,
        });

        new cdk.CfnOutput(this, 'ClusterName', { value: this.cluster.clusterName, exportName: 'T20EcsClusterName' });
        new cdk.CfnOutput(this, 'ClusterArn', { value: this.cluster.clusterArn, exportName: 'T20EcsClusterArn' });
    }
}
