import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';
import { KmsStack } from './kms-stack';

export interface DynamoDbStackProps extends cdk.StackProps {
    kmsStack: KmsStack;
}

/**
 * DynamoDB Stack – Event Store and Live Score tables.
 *
 * Tables:
 *   t20-score-events  – PK: matchId, SK: inning#over#ball (immutable store)
 *   t20-live-scores   – PK: matchId (materialized view)
 *   t20-replay-state  – PK: matchId (replay status tracking)
 *
 * TASK-03.2 – implementation placeholder (expand in TASK-03)
 */
export class DynamoDbStack extends cdk.Stack {
    public readonly scoreEventsTable: dynamodb.ITable;
    public readonly liveScoresTable: dynamodb.ITable;
    public readonly replayStateTable: dynamodb.ITable;

    constructor(scope: Construct, id: string, props: DynamoDbStackProps) {
        super(scope, id, props);

        // Event store – immutable log of all ball-by-ball events
        this.scoreEventsTable = new dynamodb.Table(this, 'ScoreEventsTable', {
            tableName: 't20-score-events',
            partitionKey: { name: 'matchId', type: dynamodb.AttributeType.STRING },
            sortKey: { name: 'eventSequence', type: dynamodb.AttributeType.STRING }, // inning#over#ball
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            encryption: dynamodb.TableEncryption.CUSTOMER_MANAGED,
            encryptionKey: props.kmsStack.dynamoDbKey,
            pointInTimeRecovery: true,
            timeToLiveAttribute: 'ttl',
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        // Materialized view – latest live score per match
        this.liveScoresTable = new dynamodb.Table(this, 'LiveScoresTable', {
            tableName: 't20-live-scores',
            partitionKey: { name: 'matchId', type: dynamodb.AttributeType.STRING },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            encryption: dynamodb.TableEncryption.CUSTOMER_MANAGED,
            encryptionKey: props.kmsStack.dynamoDbKey,
            pointInTimeRecovery: true,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        // Replay state tracking
        this.replayStateTable = new dynamodb.Table(this, 'ReplayStateTable', {
            tableName: 't20-replay-state',
            partitionKey: { name: 'matchId', type: dynamodb.AttributeType.STRING },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            encryption: dynamodb.TableEncryption.CUSTOMER_MANAGED,
            encryptionKey: props.kmsStack.dynamoDbKey,
            removalPolicy: cdk.RemovalPolicy.RETAIN,
        });

        // GSI: query events by timestamp range for audit
        (this.scoreEventsTable as dynamodb.Table).addGlobalSecondaryIndex({
            indexName: 'matchId-timestamp-index',
            partitionKey: { name: 'matchId', type: dynamodb.AttributeType.STRING },
            sortKey: { name: 'timestamp', type: dynamodb.AttributeType.STRING },
            projectionType: dynamodb.ProjectionType.ALL,
        });

        new cdk.CfnOutput(this, 'ScoreEventsTableName', { value: this.scoreEventsTable.tableName, exportName: 'T20ScoreEventsTableName' });
        new cdk.CfnOutput(this, 'LiveScoresTableName', { value: this.liveScoresTable.tableName, exportName: 'T20LiveScoresTableName' });
    }
}
