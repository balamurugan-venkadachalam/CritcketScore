package com.crickscore.producer.kafka;

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
 * Provides the TLS + SASL/IAM Kafka producer properties required by MSK in
 * production.
 *
 * <p>
 * This configuration is <b>only active in the {@code prod} profile</b>.
 * In {@code local} and {@code test} profiles, Kafka uses plain-text and no
 * authentication.
 *
 * <h3>Security protocol: SASL_SSL + AWS_MSK_IAM</h3>
 * MSK IAM authentication flow:
 * <ol>
 * <li>The ECS task assumes its IAM role via the container's metadata
 * endpoint.</li>
 * <li>{@code IAMLoginModule} generates a signed SASL token using
 * {@link DefaultCredentialsProvider}.</li>
 * <li>MSK brokers validate the token against IAM and authorise produce/admin
 * based on attached policies.</li>
 * </ol>
 *
 * <h3>Secrets Manager integration</h3>
 * Additional credentials (e.g. optional mTLS client keystores) can be resolved
 * from
 * Secrets Manager using the {@link SecretsManagerClient} bean. The bean is only
 * injected
 * when {@code AWS_SECRET_NAME} is set in the environment.
 *
 * <p>
 * To add SASL/SCRAM support instead of IAM, swap the JAAS config and mechanism
 * below.
 */
@Slf4j
@Configuration
@Profile("prod")
public class TlsSaslConfig {

    /**
     * AWS Secrets Manager secret name containing optional extra Kafka credentials.
     * Defaults to an empty string (no secret lookup needed for pure IAM auth).
     */
    @Value("${aws.secretsmanager.secret-name:}")
    private String secretName;

    /**
     * AWS region used for the Secrets Manager client.
     * Resolved from env var {@code AWS_REGION}, falls back to
     * {@code ap-southeast-2}.
     */
    @Value("${aws.region:ap-southeast-2}")
    private String awsRegion;

    /**
     * SSL truststore path — MSK brokers use Amazon CA certificates.
     * The trust store must be present in the container image (see Dockerfile).
     */
    @Value("${spring.kafka.producer.properties.ssl.truststore.location:" +
            "/etc/ssl/certs/kafka.client.truststore.jks}")
    private String truststoreLocation;

    // ─── Beans ───────────────────────────────────────────────────────────────

    /**
     * Additional Kafka producer properties for MSK TLS + SASL/IAM.
     *
     * <p>
     * Spring Boot's {@code KafkaAutoConfiguration} merges these into the producer
     * factory
     * when a {@code Map<String, Object>} bean named
     * {@code kafkaAdditionalProducerProps} is present.
     * Alternatively, wire these directly into
     * {@link KafkaProducerConfig#producerFactory()}.
     *
     * <p>
     * The JAAS {@code IAMLoginModule} is provided by the
     * {@code aws-msk-iam-auth} library — add it to {@code build.gradle} for prod
     * builds.
     */
    @Bean
    public Map<String, Object> mskSaslProperties() {
        Map<String, Object> props = new HashMap<>();

        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "AWS_MSK_IAM");
        props.put("sasl.jaas.config",
                "software.amazon.msk.auth.iam.IAMLoginModule required;");
        props.put("sasl.client.callback.handler.class",
                "software.amazon.msk.auth.iam.IAMClientCallbackHandler");

        // Trust store — MSK ships a JKS with Amazon CA roots
        props.put("ssl.truststore.location", truststoreLocation);
        props.put("ssl.truststore.type", "JKS");

        log.info("MSK SASL/IAM TLS producer properties applied. TrustStore={}",
                truststoreLocation);
        return props;
    }

    /**
     * AWS Secrets Manager client — used to resolve optional extra credentials or
     * config
     * stored outside the application.yml (e.g. future mTLS keystore password).
     *
     * <p>
     * Credentials are resolved via the {@link DefaultCredentialsProvider} chain:
     * ECS task role → EC2 instance role → env vars → ~/.aws/credentials.
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
     * Only called when {@code secretName} is non-empty.
     *
     * @param client the configured {@link SecretsManagerClient}.
     * @return the secret string, or an empty string if {@code secretName} is blank.
     */
    public String fetchSecret(SecretsManagerClient client) {
        if (secretName == null || secretName.isBlank()) {
            log.debug("No Secrets Manager secret name configured — skipping secret fetch.");
            return "";
        }

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse response = client.getSecretValue(request);
        log.info("Successfully fetched Kafka secret from Secrets Manager: {}", secretName);
        return response.secretString();
    }
}
