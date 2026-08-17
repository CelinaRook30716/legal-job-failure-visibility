package legaljobs;

import java.util.List;
import java.util.Map;

public record JobFailure(
        String title,
        String message,
        String exception,
        List<String> fingerprint,
        Map<String, String> context) {
}
