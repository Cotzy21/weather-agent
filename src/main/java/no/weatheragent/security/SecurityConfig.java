package no.weatheragent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Sikkerhetsoppsett: appen er en OAuth2 resource server som validerer Supabase
 * sine JWT-er. Lese-API-et for vær og ruter er åpent; brukerspesifikke endepunkter
 * krever innlogging. Stateless (ingen sesjon/CSRF) - klientene sender Bearer-token.
 *
 * CORS er slått på så mobil-appene (Capacitor: capacitor://localhost / http://localhost)
 * og dev-frontend kan kalle API-et fra et annet opphav. Tillatte opphav settes i
 * {@code app.cors.allowed-origins}.
 */
@Configuration
public class SecurityConfig {

    /**
     * Innholdspolicy for frontenden: kun egne skript (ingen inline/eval), kartfliser fra de tre
     * kartleverandørene, og kall mot egen backend + Supabase (innlogging). Stopper at injisert
     * HTML/script kan laste kode utenfra eller sende data til andre servere.
     */
    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob: https://*.tile.openstreetmap.org https://*.tile.opentopomap.org https://server.arcgisonline.com",
            "font-src 'self' data:",
            "connect-src 'self' https://*.supabase.co wss://*.supabase.co",
            "worker-src 'self'",
            "manifest-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .frameOptions(f -> f.deny())
                        .permissionsPolicyHeader(p -> p.policy("geolocation=(self), camera=(self), microphone=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/me",
                                "/api/ruter", "/api/ruter/**",
                                "/api/treningsokter", "/api/treningsokter/**",
                                "/api/ovelser/**",
                                "/api/trening/**",
                                "/api/recovery", "/api/recovery/**",
                                "/api/vaner", "/api/vaner/**",
                                "/api/kropp/**",
                                "/api/innstillinger/**",
                                "/api/konto", "/api/konto/**",
                                "/api/kosthold/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
