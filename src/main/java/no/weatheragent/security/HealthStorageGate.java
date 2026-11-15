package no.weatheragent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.weatheragent.account.HealthStorageConsent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

@Configuration
public class HealthStorageGate {
    private final HealthStorageConsent consent;

    public HealthStorageGate(HealthStorageConsent consent) {
        this.consent = consent;
    }

    @Bean
    WebMvcConfigurer healthConsentInterceptor() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registerGate(registry);
            }
        };
    }

    private void registerGate(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws java.io.IOException {
                if ("DELETE".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) return true;
                if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth
                        && consent.get(UUID.fromString(auth.getToken().getSubject())).allowed()) return true;
                response.setStatus(403);
                response.setHeader("Cache-Control", "no-store");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"HEALTH_STORAGE_CONSENT_REQUIRED\",\"error\":\"Du må samtykke til helselagring under Konto.\"}");
                return false;
            }
        }).addPathPatterns("/api/treningsokter/**", "/api/ovelser/**", "/api/trening/**",
                "/api/recovery/**", "/api/vaner/**", "/api/kropp/**", "/api/kosthold/**",
                "/api/innstillinger/streak");
    }
}
