import * as cdk from 'aws-cdk-lib';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import { Construct } from 'constructs';

/**
 * ECR Stack – Container registries for producer and consumer images.
 *
 * TASK-03.4 – implementation placeholder (expand in TASK-03)
 */
export class EcrStack extends cdk.Stack {
    public readonly producerRepo: ecr.IRepository;
    public readonly consumerRepo: ecr.IRepository;

    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        this.producerRepo = new ecr.Repository(this, 'ProducerRepo', {
            repositoryName: 't20-score-producer',
            imageScanOnPush: true,
            imageTagMutability: ecr.TagMutability.IMMUTABLE,
            lifecycleRules: [
                { maxImageCount: 10, description: 'Keep last 10 images' },
            ],
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        this.consumerRepo = new ecr.Repository(this, 'ConsumerRepo', {
            repositoryName: 't20-score-consumer',
            imageScanOnPush: true,
            imageTagMutability: ecr.TagMutability.IMMUTABLE,
            lifecycleRules: [
                { maxImageCount: 10, description: 'Keep last 10 images' },
            ],
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        new cdk.CfnOutput(this, 'ProducerRepoUri', { value: this.producerRepo.repositoryUri, exportName: 'T20ProducerRepoUri' });
        new cdk.CfnOutput(this, 'ConsumerRepoUri', { value: this.consumerRepo.repositoryUri, exportName: 'T20ConsumerRepoUri' });
    }
}
