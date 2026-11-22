package com.sakhtyar.quality;

import java.util.Arrays;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** No runtime toggles. Profile and test permission are resolved only at application startup. */
@Component
public final class Phase09RuntimePolicy {
    private final Environment environment;
    public Phase09RuntimePolicy(Environment environment) { this.environment=environment; }
    public boolean production() { return Arrays.asList(environment.getActiveProfiles()).contains("production"); }
    public boolean development() { return !production() && Arrays.asList(environment.getActiveProfiles()).contains("development"); }
    public String profile() { return production()?"production":development()?"development":"legacy-local"; }
    public void requireDevelopment() {
        if(!development()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Business E2E execution requires the dedicated development profile; other safety gates still apply");
    }
    public Map<String,Object> publicMode() {
        return Map.of("profile",profile(),"production",production(),"development",development(),
            "uiPreflightAvailable",development(),"businessWritesAuthorizedByProfile",false,
            "testIsolationStillRequired",true,"seedRunnerDisabledByProfile",production()||development(),
            "automaticFlywayExpectedDisabled",true);
    }
}
