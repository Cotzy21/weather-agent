package no.weatheragent.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sikkerhetsoppsett: appen er en OAuth2 resource server som validerer Supabase
 * sine JWT-er (signatur via JWKS, se application.properties). Lese-API-et for vær
 * og ruter er åpent; brukerspesifikke endepunkter krever innlogging.
 *
 * Stateless (ingen sesjon/CSRF) siden klientene sender Bearer-token per kall.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/me",
                                "/api/ruter", "/api/ruter/**",
                                "/api/treningsokter", "/api/treningsokter/**",
                                "/api/ovelser/**",
                                "/api/trening/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
