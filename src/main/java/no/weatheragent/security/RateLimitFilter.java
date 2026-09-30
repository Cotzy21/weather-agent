package no.weatheragent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Begrenser hvor ofte én IP kan kalle API-et. De åpne endepunktene (værsøk, sted, rute) bruker
 * betalte eller delte tjenester (LLM, kartnøkkel, Overpass) uten innlogging, så uten tak kan hvem
 * som helst tømme budsjettet eller få oss utestengt. Hver regel har i tillegg en global grense som
 * fungerer som kostnadsbrems selv om noen skifter IP-adresse (eller lyver i X-Forwarded-For).
 *
 * Bak en proxy (Render) leses klient-IP fra første X-Forwarded-For-verdi; sett
 * {@code app.rate-limit.trust-forwarded-for=false} hvis appen kjører uten proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static final Pattern IP_LIKE = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    /** En regel: hvilke kall den gjelder, og grenser per IP (minutt/time) og globalt (time). */
    private record Rule(String name, String path, String method, SlidingWindowLimiter perMinute,
                        SlidingWindowLimiter perHour, SlidingWindowLimiter global) {
        boolean matches(HttpServletRequest r) {
            return (path.endsWith("/**") ? r.getRequestURI().startsWith(path.substring(0, path.length() - 2))
                    : r.getRequestURI().equals(path)) && (method == null || method.equals(r.getMethod()));
        }
    }

    private final List<Rule> rules;
    private final boolean trustForwardedFor;

    @Autowired
    public RateLimitFilter(@Value("${app.rate-limit.trust-forwarded-for:true}") boolean trustForwardedFor,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this(trustForwardedFor, enabled, Clock.systemUTC());
    }

    RateLimitFilter(boolean trustForwardedFor, boolean enabled, Clock clock) {
        this.trustForwardedFor = trustForwardedFor;
        this.rules = enabled ? List.of(
                // Værsøket kaller LLM + Overpass + MET: dyrest.
                rule("turvaer", "/api/turvaer", "GET", 10, 80, 600, clock),
                rule("sted", "/api/sted", "GET", 30, 300, 3000, clock),
                rule("rute", "/api/rute", "POST", 30, 300, 3000, clock),
                // Alt annet: bred grense mot skripting og passord-/token-gjetting.
                rule("api", "/api/**", null, 300, 6000, 60_000, clock)) : List.of();
    }

    private static Rule rule(String name, String path, String method, int perMinute, int perHour, int global, Clock clock) {
        return new Rule(name, path, method,
                new SlidingWindowLimiter(perMinute, MINUTE, clock),
                new SlidingWindowLimiter(perHour, HOUR, clock),
                new SlidingWindowLimiter(global, HOUR, clock));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"OPTIONS".equals(request.getMethod())) { // CORS preflight teller ikke
            String ip = clientIp(request);
            for (Rule rule : rules) {
                if (!rule.matches(request)) continue;
                long wait = Math.max(rule.perMinute.tryAcquire(ip),
                        Math.max(rule.perHour.tryAcquire(ip), rule.global.tryAcquire("*")));
                if (wait > 0) {
                    response.setStatus(429);
                    response.setHeader("Retry-After", String.valueOf(wait));
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"For mange forespørsler. Prøv igjen om "
                            + Math.max(1, (wait + 59) / 60) + " min.\"}");
                    return;
                }
                break; // første treff er den mest spesifikke regelen
            }
        }
        chain.doFilter(request, response);
    }

    String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = xff.split(",")[0].trim();
                if (IP_LIKE.matcher(first).matches()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
