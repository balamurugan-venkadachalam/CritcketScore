import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';

export interface SubnetStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
}

/**
 * Subnet Stack – Exports subnet IDs for cross-stack references.
 * 3 Public, 3 Private (with egress), 3 Isolated subnets.
 *
 * TASK-02.3 – implementation placeholder (expand in TASK-02)
 */
export class SubnetStack extends cdk.Stack {
    public readonly publicSubnets: ec2.ISubnet[];
    public readonly privateSubnets: ec2.ISubnet[];
    public readonly isolatedSubnets: ec2.ISubnet[];

    constructor(scope: Construct, id: string, props: SubnetStackProps) {
        super(scope, id, props);

        const { vpc } = props.vpcStack;
        this.publicSubnets = vpc.publicSubnets;
        this.privateSubnets = vpc.privateSubnets;
        this.isolatedSubnets = vpc.isolatedSubnets;

        // Outputs for reference
        new cdk.CfnOutput(this, 'PublicSubnetIds', {
            value: this.publicSubnets.map(s => s.subnetId).join(','),
            exportName: 'T20PublicSubnetIds',
        });
        new cdk.CfnOutput(this, 'PrivateSubnetIds', {
            value: this.privateSubnets.map(s => s.subnetId).join(','),
            exportName: 'T20PrivateSubnetIds',
        });
    }
}
