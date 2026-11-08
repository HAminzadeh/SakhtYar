package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase04RobotsPolicyTest {
    @Test void disallowAndSpecificAllowAndEndAnchor() {
        var p = Phase04RobotsPolicy.parse("User-agent: *\nDisallow: /private\nAllow: /private/public\nDisallow: /exact$\n", "SakhtYarBot");
        assertThrows(SecurityException.class, () -> p.requireAllowed(URI.create("https://example.org/private/secret")));
        assertDoesNotThrow(() -> p.requireAllowed(URI.create("https://example.org/private/public/read")));
        assertThrows(SecurityException.class, () -> p.requireAllowed(URI.create("https://example.org/exact")));
        assertDoesNotThrow(() -> p.requireAllowed(URI.create("https://example.org/exact/more")));
    }
    @Test void specificAgentOverridesWildcard() {
        var p=Phase04RobotsPolicy.parse("User-agent: *\nDisallow: /\nUser-agent: SakhtYarBot\nAllow: /ok\n", "SakhtYarBot/1.0");
        assertDoesNotThrow(() -> p.requireAllowed(URI.create("https://example.org/ok")));
    }
    @Test void unknownStatusIsBlockedRatherThanRobotsBypassed() {
        var dummy = new FetchedPage(URI.create("https://example.org/robots.txt"),
          URI.create("https://example.org/robots.txt"), 503, "text/plain", new byte[0], java.util.Map.of());
        assertThrows(SecurityException.class, () -> Phase04RobotsPolicy.fromResponse(dummy,"SakhtYarBot"));
    }
}
