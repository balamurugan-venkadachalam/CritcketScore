import * as cdk from 'aws-cdk-lib';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';
import { VpcStack } from './vpc-stack';
import { SecurityGroupsStack } from './security-groups-stack';

export interface AlbStackProps extends cdk.StackProps {
    vpcStack: VpcStack;
    sgStack: SecurityGroupsStack;
}

/**
 * ALB Stack – Internet-facing Application Load Balancer for Producer API.
 *
 * TASK-03.8 – implementation placeholder (expand in TASK-03)
 */
export class AlbStack extends cdk.Stack {
    public readonly alb: elbv2.IApplicationLoadBalancer;
    public readonly httpsListener: elbv2.ApplicationListener;

    constructor(scope: Construct, id: string, props: AlbStackProps) {
        super(scope, id, props);

        const alb = new elbv2.ApplicationLoadBalancer(this, 'T20Alb', {
            loadBalancerName: 't20-score-alb',
            vpc: props.vpcStack.vpc,
            internetFacing: true,
            securityGroup: props.sgStack.albSg,
            vpcSubnets: { subnetType: cdk.aws_ec2.SubnetType.PUBLIC },
        });

        this.alb = alb;

        // HTTPS listener (ACM cert ARN injected via context in TASK-03.8)
        this.httpsListener = alb.addListener('HttpsListener', {
            port: 443,
            protocol: elbv2.ApplicationProtocol.HTTPS,
            defaultAction: elbv2.ListenerAction.fixedResponse(200, {
                contentType: 'application/json',
                messageBody: '{"status":"ok"}',
            }),
        });

        new cdk.CfnOutput(this, 'AlbDnsName', { value: alb.loadBalancerDnsName, exportName: 'T20AlbDnsName' });
    }
}
