package no.weatheragent.hiking;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpRequest;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Avviser svar som er større enn {@code maxBytes} FØR de er lest inn i minnet. Et Overpass-svar leses til et
 * JSON-tre, som fort tar mange ganger så mye plass som selve teksten, og gratisinstansen har bare ca. 330 MB heap
 * (med {@code ExitOnOutOfMemoryError} tar en OOM hele tjenesten ned). Sjekker {@code Content-Length} først, og teller
 * ellers byte for byte for svar uten lengde (chunked).
 */
final class ResponseSizeLimit implements ClientHttpRequestInterceptor {

    /** Kastes (som IOException, så RestClient behandler det som en nettverksfeil) når svaret er for stort. */
    static final class TooLargeException extends IOException {
        TooLargeException(long limit) {
            super("Svaret er større enn " + limit + " byte");
        }
    }

    private final long maxBytes;

    ResponseSizeLimit(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getHeaders().getContentLength() > maxBytes) {
            response.close();
            throw new TooLargeException(maxBytes);
        }
        return new Limited(response, maxBytes);
    }

    /** Er feilen (eller en årsak bak den) et for stort svar? RestClient pakker IOException fra lesingen inn. */
    static boolean isTooLarge(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TooLargeException) {
                return true;
            }
        }
        return false;
    }

    private static final class Limited implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final long maxBytes;

        Limited(ClientHttpResponse delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            return new CountingStream(delegate.getBody(), maxBytes);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class CountingStream extends FilterInputStream {
        private final long maxBytes;
        private long count;

        CountingStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                add(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        private void add(long n) throws IOException {
            count += n;
            if (count > maxBytes) {
                close();
                throw new TooLargeException(maxBytes);
            }
        }
    }
}
