import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import { KmsStack } from './kms-stack';

export interface VpcStackProps extends cdk.StackProps {
    kmsStack: KmsStack;
}

/**
 * VPC Stack – Core network boundary.
 * CIDR: 10.0.0.0/16, DNS enabled, flow logs to S3.
 *
 * TASK-02.2 – implementation placeholder (expand in TASK-02)
 */
export class VpcStack extends cdk.Stack {
    public readonly vpc: ec2.IVpc;

    constructor(scope: Construct, id: string, props: VpcStackProps) {
        super(scope, id, props);

        // Placeholder VPC – fully implemented in TASK-02.2
        this.vpc = new ec2.Vpc(this, 'T20Vpc', {
            ipAddresses: ec2.IpAddresses.cidr('10.0.0.0/16'),
            maxAzs: 3,
            natGateways: 3,
            subnetConfiguration: [
                { name: 'Public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
                { name: 'Private', subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, cidrMask: 24 },
                { name: 'Isolated', subnetType: ec2.SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
            ],
        });

        new cdk.CfnOutput(this, 'VpcId', { value: this.vpc.vpcId, exportName: 'T20VpcId' });
    }
}
