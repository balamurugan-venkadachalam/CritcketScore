package com.crickscore.consumer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * Configures the AWS SDK v2 {@link DynamoDbClient} bean.
 *
 * <h3>Credential chain</h3>
 * Uses {@link DefaultCredentialsProvider} which resolves credentials in order:
 * <ol>
 * <li>Env vars: {@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY}</li>
 * <li>Java system properties</li>
 * <li>AWS profile file ({@code ~/.aws/credentials})</li>
 * <li>ECS container credentials (prod — via IAM task role)</li>
 * <li>EC2 instance metadata service</li>
 * </ol>
 *
 * <h3>Local profile</h3>
 * {@code aws.dynamodb.endpoint-override} is set to
 * {@code http://localhost:4566}
 * in {@code application-local.yml}, pointing the client at LocalStack. The
 * endpoint override is blank in prod, so the SDK resolves the real AWS
 * endpoint.
 */
@Slf4j
@Configuration
public class DynamoDbConfig {

    @Value("${aws.region:ap-southeast-2}")
    private String awsRegion;

    /**
     * Endpoint override for local development / testing (LocalStack).
     * Set to a blank string in prod — the SDK will resolve the real endpoint.
     */
    @Value("${aws.dynamodb.endpoint-override:}")
    private String endpointOverride;

    /**
     * Creates a {@link DynamoDbClient} configured for the current environment.
     *
     * <p>
     * The client is thread-safe and long-lived; it is registered as a
     * singleton Spring bean and shared across all repository beans.
     *
     * @return configured {@link DynamoDbClient}.
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
            log.info("DynamoDB client using endpoint override: {}", endpointOverride);
        } else {
            log.info("DynamoDB client using AWS region endpoint: {}", awsRegion);
        }

        return builder.build();
    }
}
