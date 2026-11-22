package com.sakhtyar.knowledge.infrastructure.persian;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Reads the startup-generated compute policy. Python owns hardware probing; Java consumes the auditable policy. */
public final class HardwareCapabilityManager {
    private final Path repositoryRoot;
    public HardwareCapabilityManager(Path repositoryRoot) { this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize(); }
    public Path hardwareProfilePath() { return repositoryRoot.resolve(".local/hardware/hardware-profile.json"); }
    public Path computePolicyPath() { return repositoryRoot.resolve(".local/hardware/compute-policy.json"); }
    public Optional<String> readComputePolicy() {
        try { var p=computePolicyPath(); return Files.isRegularFile(p) ? Optional.of(Files.readString(p)) : Optional.empty(); }
        catch (IOException e) { return Optional.empty(); }
    }
    public boolean isProfileReady() { return Files.isRegularFile(hardwareProfilePath()) && Files.isRegularFile(computePolicyPath()); }
}