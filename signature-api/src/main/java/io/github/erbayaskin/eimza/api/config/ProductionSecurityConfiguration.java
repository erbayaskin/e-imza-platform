package io.github.erbayaskin.eimza.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@Profile("!local & !test")
public class ProductionSecurityConfiguration {

    @Bean
    SecurityFilterChain productionSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(
                                csp -> csp.policyDirectives(
                                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .referrerPolicy(
                                referrer -> referrer.policy(
                                        ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/actuator/health/**",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasAuthority("SCOPE_eimza.admin")
                                        .requestMatchers("/admin/**")
                                        .hasAuthority("SCOPE_eimza.admin")
                                        .requestMatchers("/api/v1/validations/**")
                                        .hasAuthority("SCOPE_eimza.validate")
                                        .requestMatchers("/api/v1/signatures/**")
                                        .hasAuthority("SCOPE_eimza.sign")
                                        .requestMatchers("/api/v1/signing-sessions/**")
                                        .hasAuthority("SCOPE_eimza.sign")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {}))
                .build();
    }
}
