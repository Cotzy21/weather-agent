package no.weatheragent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Avviser for store forespørsler før de leses inn i minnet. JSON-body har ingen øvre grense i Spring
 * som standard, så én stor POST kan fylle et lite minne (Render free: 512 MB). Garmin-import har en
 * egen, større grense. Sjekker både Content-Length og faktisk lest mengde (chunked uten lengde).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final String IMPORT_PATH = "/api/trening/import/garmin";

    private final long maxBytes;
    private final long maxImportBytes;

    public RequestSizeLimitFilter(@Value("${app.max-body-bytes:1048576}") long maxBytes,
                                  @Value("${app.max-import-bytes:8388608}") long maxImportBytes) {
        this.maxBytes = maxBytes;
        this.maxImportBytes = maxImportBytes;
    }

    /** Kastes når kroppen er større enn tillatt mens den leses. */
    static class TooLargeException extends IOException {
        TooLargeException() {
            super("Forespørselen er for stor.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = IMPORT_PATH.equals(request.getRequestURI()) ? maxImportBytes : maxBytes;
        if (request.getContentLengthLong() > limit) {
            tooLarge(response);
            return;
        }
        try {
            chain.doFilter(new LimitedRequest(request, limit), response);
        } catch (TooLargeException e) {
            tooLarge(response);
        } catch (ServletException e) {
            if (e.getCause() instanceof TooLargeException || (e.getCause() != null && e.getCause().getCause() instanceof TooLargeException)) {
                tooLarge(response);
            } else {
                throw e;
            }
        }
    }

    private static void tooLarge(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Forespørselen er for stor.\"}");
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0 && ++read > limit) throw new TooLargeException();
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0 && (read += n) > limit) throw new TooLargeException();
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }
    }
}
