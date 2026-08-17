package legaljobs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InfraiErrorClient implements FailureReporter {
    private static final String CAPTURE_PATH = "/v1/errors/capture";
    private static final int MAX_ATTEMPTS = 4;

    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiErrorClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiErrorClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void capture(JobFailure failure) {
        String requestId = UUID.randomUUID().toString();
        String payload = Json.write(Map.of(
                "title", failure.title(),
                "message", failure.message(),
                "level", "error",
                "fingerprint", failure.fingerprint(),
                "exception", failure.exception(),
                "context", failure.context()));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpResponse<String> response = send(payload, requestId);
            Map<String, Object> envelope = Json.object(response.body());
            Object ok = envelope.get("ok");
            Object data = envelope.get("data");
            Object error = envelope.get("error");
            Object metadata = envelope.get("metadata");

            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(ok)) {
                throw InfraiException.from(error, response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException("transport", "HTTP " + response.statusCode(), response.statusCode());
            }
            // Reading data and metadata is intentional: every response follows the same envelope.
            if (data == null && metadata == null) {
                return;
            }
            return;
        }
        throw new InfraiException("rate_limit", "Retry budget exhausted", 429);
    }

    private HttpResponse<String> send(String payload, String requestId) {
        HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(CAPTURE_PATH))
                .timeout(config.requestTimeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", requestId)
                .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException cause) {
            throw new InfraiException("transport", cause.getMessage(), 0);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new InfraiException("interrupted", cause.getMessage(), 0);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiErrorClient::parseRetryAfter)
                .orElse(Duration.ofSeconds(1L << attempt));
    }

    private static Duration parseRetryAfter(String value) {
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(1);
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new InfraiException("interrupted", cause.getMessage(), 0);
        }
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;

        InfraiException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        static InfraiException from(Object value, int status) {
            if (value instanceof Map<?, ?> fields) {
                Object code = fields.containsKey("code") ? fields.get("code") : "rejected";
                Object message = fields.containsKey("message") ? fields.get("message") : fields;
                return new InfraiException(
                        String.valueOf(code), String.valueOf(message), status);
            }
            return new InfraiException("rejected", String.valueOf(value), status);
        }

        public String code() { return code; }
        public int status() { return status; }
    }

    static final class Json {
        static String write(Object value) {
            if (value == null) return "null";
            if (value instanceof String text) return quote(text);
            if (value instanceof Boolean || value instanceof Number) return value.toString();
            if (value instanceof Map<?, ?> map) {
                StringBuilder out = new StringBuilder("{");
                Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<?, ?> entry = entries.next();
                    out.append(quote(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
                    if (entries.hasNext()) out.append(',');
                }
                return out.append('}').toString();
            }
            if (value instanceof Iterable<?> items) {
                StringBuilder out = new StringBuilder("[");
                Iterator<?> iterator = items.iterator();
                while (iterator.hasNext()) {
                    out.append(write(iterator.next()));
                    if (iterator.hasNext()) out.append(',');
                }
                return out.append(']').toString();
            }
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }

        static Map<String, Object> object(String source) {
            Object value = new Parser(source).parse();
            if (!(value instanceof Map<?, ?> map)) {
                throw new InfraiException("transport", "Response is not a JSON object", 0);
            }
            @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }

        private static String quote(String value) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '\"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                        else out.append(c);
                    }
                }
            }
            return out.append('\"').toString();
        }

        private static final class Parser {
            private final String source;
            private int index;

            Parser(String source) { this.source = source; }

            Object parse() {
                Object value = value();
                whitespace();
                if (index != source.length()) throw invalid();
                return value;
            }

            private Object value() {
                whitespace();
                if (index >= source.length()) throw invalid();
                return switch (source.charAt(index)) {
                    case '{' -> object();
                    case '[' -> array();
                    case '\"' -> string();
                    case 't' -> literal("true", true);
                    case 'f' -> literal("false", false);
                    case 'n' -> literal("null", null);
                    default -> number();
                };
            }

            private Map<String, Object> object() {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                index++;
                whitespace();
                if (take('}')) return map;
                do {
                    whitespace();
                    String key = string();
                    whitespace();
                    expect(':');
                    map.put(key, value());
                    whitespace();
                } while (take(','));
                expect('}');
                return map;
            }

            private List<Object> array() {
                List<Object> list = new java.util.ArrayList<>();
                index++;
                whitespace();
                if (take(']')) return list;
                do { list.add(value()); whitespace(); } while (take(','));
                expect(']');
                return list;
            }

            private String string() {
                expect('\"');
                StringBuilder out = new StringBuilder();
                while (index < source.length()) {
                    char c = source.charAt(index++);
                    if (c == '\"') return out.toString();
                    if (c != '\\') { out.append(c); continue; }
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '\"', '\\', '/' -> out.append(escaped);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                            index += 4;
                        }
                        default -> throw invalid();
                    }
                }
                throw invalid();
            }

            private Object number() {
                int start = index;
                while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
                try { return Double.valueOf(source.substring(start, index)); }
                catch (NumberFormatException cause) { throw invalid(); }
            }

            private Object literal(String text, Object value) {
                if (!source.startsWith(text, index)) throw invalid();
                index += text.length();
                return value;
            }

            private void whitespace() {
                while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
            }

            private boolean take(char expected) {
                if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
                return false;
            }

            private void expect(char expected) {
                if (!take(expected)) throw invalid();
            }

            private InfraiException invalid() {
                return new InfraiException("transport", "Invalid JSON response", 0);
            }
        }
    }
}
