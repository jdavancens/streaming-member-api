package com.streaming.memberapi.profile.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.aws.mcs.auth.SigV4AuthProvider;

import javax.net.ssl.SSLContext;

@Configuration
public class CassandraConfig {

    @Bean
    @ConditionalOnProperty(name = "cassandra.keyspaces.enabled", havingValue = "true")
    public CqlSessionBuilderCustomizer keyspacesCustomizer(
            @Value("${spring.data.cassandra.local-datacenter:us-east-1}") String region) {
        return builder -> {
            try {
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(null, null, null);
                builder.withSslContext(ssl)
                       .withAuthProvider(new SigV4AuthProvider(region));
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure Keyspaces SSL/SigV4", e);
            }
        };
    }
}
