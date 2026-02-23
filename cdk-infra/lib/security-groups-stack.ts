import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';

export interface SecurityGroupsStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
}

/**
 * Security Groups Stack – Least-privilege SGs per tier.
 * Groups: ALB, ECS Producer, ECS Consumer, MSK Broker, DynamoDB Endpoint.
 *
 * TASK-02.4 – implementation placeholder (expand in TASK-02)
 */
export class SecurityGroupsStack extends cdk.Stack {
    public readonly albSg: ec2.ISecurityGroup;
    public readonly ecsProducerSg: ec2.ISecurityGroup;
    public readonly ecsConsumerSg: ec2.ISecurityGroup;
    public readonly mskSg: ec2.ISecurityGroup;

    constructor(scope: Construct, id: string, props: SecurityGroupsStackProps) {
        super(scope, id, props);

        const { vpc } = props.vpcStack;

        this.albSg = new ec2.SecurityGroup(this, 'AlbSg', {
            vpc,
            description: 'ALB security group – allows inbound HTTPS 443',
            allowAllOutbound: false,
        });

        this.ecsProducerSg = new ec2.SecurityGroup(this, 'EcsProducerSg', {
            vpc,
            description: 'ECS Producer task security group',
            allowAllOutbound: true,
        });

        this.ecsConsumerSg = new ec2.SecurityGroup(this, 'EcsConsumerSg', {
            vpc,
            description: 'ECS Consumer task security group',
            allowAllOutbound: true,
        });

        this.mskSg = new ec2.SecurityGroup(this, 'MskSg', {
            vpc,
            description: 'MSK broker security group – TLS 9094',
            allowAllOutbound: false,
        });

        // Core rules (placeholder – expanded in TASK-02.4)
        (this.albSg as ec2.SecurityGroup).addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'HTTPS from internet');
        (this.mskSg as ec2.SecurityGroup).addIngressRule(this.ecsProducerSg, ec2.Port.tcp(9094), 'MSK TLS from producer');
        (this.mskSg as ec2.SecurityGroup).addIngressRule(this.ecsConsumerSg, ec2.Port.tcp(9094), 'MSK TLS from consumer');
    }
}
