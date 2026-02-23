import * as cdk from 'aws-cdk-lib';
import * as msk from 'aws-cdk-lib/aws-msk';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';
import { SecurityGroupsStack } from './security-groups-stack';

export interface MskStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
    sgStack: SecurityGroupsStack;
}

/**
 * MSK Stack – Managed Kafka cluster.
 * 3 brokers (1/AZ), kafka.m5.xlarge, RF=3, TLS, SASL/IAM.
 *
 * TASK-03.1 – implementation placeholder (expand in TASK-03)
 */
export class MskStack extends cdk.Stack {
    public readonly cluster: msk.CfnCluster;
    public readonly bootstrapBrokers: string;

    constructor(scope: Construct, id: string, props: MskStackProps) {
        super(scope, id, props);

        const { vpc } = props.vpcStack;
        const subnetIds = vpc.privateSubnets.map(s => s.subnetId);

        this.cluster = new msk.CfnCluster(this, 'T20MskCluster', {
            clusterName: 't20-score-kafka',
            kafkaVersion: '3.5.1',
            numberOfBrokerNodes: 3,
            brokerNodeGroupInfo: {
                instanceType: 'kafka.m5.xlarge',
                clientSubnets: subnetIds,
                securityGroups: [props.sgStack.mskSg.securityGroupId],
                storageInfo: {
                    ebsStorageInfo: { volumeSize: 1000 },
                },
            },
            encryptionInfo: {
                encryptionInTransit: {
                    clientBroker: 'TLS',
                    inCluster: true,
                },
            },
            clientAuthentication: {
                sasl: { iam: { enabled: true } },
                unauthenticated: { enabled: false },
            },
            enhancedMonitoring: 'PER_BROKER',
            openMonitoring: {
                prometheus: {
                    jmxExporter: { enabledInBroker: true },
                    nodeExporter: { enabledInBroker: true },
                },
            },
        });

        // Bootstrap brokers endpoint is retrieved post-deployment via:
        //   aws kafka get-bootstrap-brokers --cluster-arn <clusterArn>
        // Injected into ECS tasks via Secrets Manager (implemented in TASK-03.1)
        this.bootstrapBrokers = this.cluster.attrArn; // placeholder – real endpoint in TASK-03.1

        new cdk.CfnOutput(this, 'MskClusterArn', {
            value: this.cluster.attrArn,
            exportName: 'T20MskClusterArn',
        });
    }
}
