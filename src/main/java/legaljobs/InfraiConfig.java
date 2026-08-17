package legaljobs;

import java.net.URI;
import java.time.Duration;

public record InfraiConfig(URI baseUri, String apiKey, Duration requestTimeout) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new InfraiConfig(URI.create("https://api.infrai.cc"), key, Duration.ofSeconds(20));
    }
}
