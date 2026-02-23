package com.crickscore.consumer.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides TLS + SASL/IAM Kafka consumer properties required by MSK in
 * production.
 *
 * <p>
 * This configuration is <b>only active in the {@code prod} profile</b>.
 * In {@code local} and {@code test} profiles, Kafka uses plain-text with no
 * authentication.
 *
 * <h3>Security protocol: SASL_SSL + AWS_MSK_IAM</h3>
 * <ol>
 * <li>The ECS task assumes its IAM role via the container metadata
 * endpoint.</li>
 * <li>{@code IAMLoginModule} generates a signed SASL token using
 * {@link DefaultCredentialsProvider}.</li>
 * <li>MSK brokers validate the token against IAM and authorise consume access
 * based on attached resource policies.</li>
 * </ol>
 */
@Slf4j
@Configuration
@Profile("prod")
public class TlsSaslConfig {

    /** AWS Secrets Manager secret name for optional extra Kafka credentials. */
    @Value("${aws.secretsmanager.secret-name:}")
    private String secretName;

    /** AWS region for the Secrets Manager client. */
    @Value("${aws.region:ap-southeast-2}")
    private String awsRegion;

    /** SSL truststore path — MSK brokers use Amazon CA certificates. */
    @Value("${spring.kafka.consumer.properties.ssl.truststore.location:" +
            "/etc/ssl/certs/kafka.client.truststore.jks}")
    private String truststoreLocation;

    /**
     * Additional Kafka consumer properties for MSK TLS + SASL/IAM.
     *
     * <p>
     * Spring Boot's {@code KafkaAutoConfiguration} merges these into the
     * consumer factory alongside YAML-configured properties.
     */
    @Bean
    public Map<String, Object> mskSaslConsumerProperties() {
        Map<String, Object> props = new HashMap<>();

        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "AWS_MSK_IAM");
        props.put("sasl.jaas.config",
                "software.amazon.msk.auth.iam.IAMLoginModule required;");
        props.put("sasl.client.callback.handler.class",
                "software.amazon.msk.auth.iam.IAMClientCallbackHandler");

        props.put("ssl.truststore.location", truststoreLocation);
        props.put("ssl.truststore.type", "JKS");

        log.info("MSK SASL/IAM TLS consumer properties applied. trustStore={}",
                truststoreLocation);
        return props;
    }

    /**
     * AWS Secrets Manager client — resolves optional extra credentials stored
     * outside {@code application.yml} (e.g. future mTLS keystore password).
     *
     * <p>
     * Credentials resolved via {@link DefaultCredentialsProvider} chain:
     * ECS task role → EC2 instance role → env vars → {@code ~/.aws/credentials}.
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Fetches the raw JSON secret string from Secrets Manager.
     * Returns an empty string if {@code secretName} is blank (pure IAM auth mode).
     */
    public String fetchSecret(SecretsManagerClient client) {
        if (secretName == null || secretName.isBlank()) {
            log.debug("No Secrets Manager secret name configured — skipping fetch.");
            return "";
        }
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        GetSecretValueResponse response = client.getSecretValue(request);
        log.info("Successfully fetched Kafka consumer secret: {}", secretName);
        return response.secretString();
    }
}
