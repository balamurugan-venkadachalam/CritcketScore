package com.crickscore.producer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Application-level configuration.
 *
 * <ul>
 * <li>Configures the primary {@link ObjectMapper} with Java 8 time
 * support.</li>
 * <li>Enables {@link AppProperties} binding.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

    /**
     * Primary {@link ObjectMapper} shared across the application.
     *
     * <p>
     * Key settings:
     * <ul>
     * <li>Serialises {@link java.time.Instant} as ISO-8601 strings (not epoch
     * numbers).</li>
     * <li>{@link ParameterNamesModule} allows Jackson to read constructor param
     * names
     * — required for deserialising Java {@code record} types without
     * {@code @JsonProperty}.</li>
     * </ul>
     */
    @Primary
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
