import * as cdk from 'aws-cdk-lib';
import * as wafv2 from 'aws-cdk-lib/aws-wafv2';
import { Construct } from 'constructs';
import { AlbStack } from './alb-stack';

export interface WafStackProps extends cdk.StackProps {
    albStack: AlbStack;
}

/**
 * WAF Stack – WebACL attached to the ALB.
 * Rules: Rate limiting (1000/5min), AWS Managed SQL injection, XSS.
 *
 * TASK-04.5 – implementation placeholder (expand in TASK-04)
 */
export class WafStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props: WafStackProps) {
        super(scope, id, props);

        const webAcl = new wafv2.CfnWebACL(this, 'T20WebAcl', {
            name: 't20-score-waf',
            scope: 'REGIONAL',
            defaultAction: { allow: {} },
            visibilityConfig: {
                sampledRequestsEnabled: true,
                cloudWatchMetricsEnabled: true,
                metricName: 'T20WebAcl',
            },
            rules: [
                {
                    name: 'RateLimitPerIp',
                    priority: 1,
                    action: { block: {} },
                    statement: {
                        rateBasedStatement: {
                            limit: 1000,
                            aggregateKeyType: 'IP',
                        },
                    },
                    visibilityConfig: {
                        sampledRequestsEnabled: true,
                        cloudWatchMetricsEnabled: true,
                        metricName: 'RateLimitPerIp',
                    },
                },
                {
                    name: 'AWSManagedRulesCommonRuleSet',
                    priority: 2,
                    overrideAction: { none: {} },
                    statement: {
                        managedRuleGroupStatement: {
                            vendorName: 'AWS',
                            name: 'AWSManagedRulesCommonRuleSet',
                        },
                    },
                    visibilityConfig: {
                        sampledRequestsEnabled: true,
                        cloudWatchMetricsEnabled: true,
                        metricName: 'CommonRuleSet',
                    },
                },
                {
                    name: 'AWSManagedRulesSQLiRuleSet',
                    priority: 3,
                    overrideAction: { none: {} },
                    statement: {
                        managedRuleGroupStatement: {
                            vendorName: 'AWS',
                            name: 'AWSManagedRulesSQLiRuleSet',
                        },
                    },
                    visibilityConfig: {
                        sampledRequestsEnabled: true,
                        cloudWatchMetricsEnabled: true,
                        metricName: 'SQLiRuleSet',
                    },
                },
            ],
        });

        // Associate with ALB
        new wafv2.CfnWebACLAssociation(this, 'WafAlbAssociation', {
            resourceArn: props.albStack.alb.loadBalancerArn,
            webAclArn: webAcl.attrArn,
        });

        new cdk.CfnOutput(this, 'WebAclArn', { value: webAcl.attrArn, exportName: 'T20WebAclArn' });
    }
}
