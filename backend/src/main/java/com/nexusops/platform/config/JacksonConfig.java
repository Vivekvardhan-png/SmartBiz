package com.nexusops.platform.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson / JSON serialization conventions.
 *
 * <ul>
 *   <li>ISO-8601 dates (not Unix timestamps).</li>
 *   <li>Null fields are omitted from API responses.</li>
 *   <li>Unknown properties in request bodies are ignored gracefully.</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        // ISO-8601 strings, not epoch integers.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Omit null fields — keep responses lean.
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Tolerate unknown fields in incoming requests.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}

