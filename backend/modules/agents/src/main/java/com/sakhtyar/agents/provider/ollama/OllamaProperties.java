package com.sakhtyar.agents.provider.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.ollama")
public class OllamaProperties {

    private String baseUrl = "http://127.0.0.1:11434";
    private String model = "qwen3.5:4b";
    private double temperature = 0.1d;
    private boolean think = false;
    private int timeoutSeconds = 90;

    // Request-level performance tuning.
    private int contextLength = 2048;
    private int maxPredictTokens = 128;
    private int numBatch = 128;
    private int numThreads = 0; // 0 = auto-detect physical CPU cores.
    private boolean useMmap = true;
    private String keepAlive = "-1";
    private boolean structuredSchema = true;

    // Ollama server-level tuning.
    private int numParallel = 1;
    private int maxLoadedModels = 1;
    private boolean preloadModel = true;
    private int preloadTimeoutSeconds = 120;

    /** Start `ollama serve` automatically when the local API is not reachable. */
    private boolean autoStart = true;

    /** Keep inference on CPU unless explicitly disabled. */
    private boolean forceCpu = true;

    private String llmLibrary = "cpu_avx2";
    private boolean noCloud = true;
    private boolean stopOnShutdown = true;
    private int startupTimeoutSeconds = 30;

    /**
     * Optional absolute path to ollama.exe. When empty, SakhtYar checks common
     * Windows installation locations and finally falls back to PATH.
     */
    private String executable = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isThink() {
        return think;
    }

    public void setThink(boolean think) {
        this.think = think;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getContextLength() {
        return contextLength;
    }

    public void setContextLength(int contextLength) {
        this.contextLength = contextLength;
    }

    public int getMaxPredictTokens() {
        return maxPredictTokens;
    }

    public void setMaxPredictTokens(int maxPredictTokens) {
        this.maxPredictTokens = maxPredictTokens;
    }

    public int getNumBatch() {
        return numBatch;
    }

    public void setNumBatch(int numBatch) {
        this.numBatch = numBatch;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public boolean isUseMmap() {
        return useMmap;
    }

    public void setUseMmap(boolean useMmap) {
        this.useMmap = useMmap;
    }

    public String getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(String keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isStructuredSchema() {
        return structuredSchema;
    }

    public void setStructuredSchema(boolean structuredSchema) {
        this.structuredSchema = structuredSchema;
    }

    public int getNumParallel() {
        return numParallel;
    }

    public void setNumParallel(int numParallel) {
        this.numParallel = numParallel;
    }

    public int getMaxLoadedModels() {
        return maxLoadedModels;
    }

    public void setMaxLoadedModels(int maxLoadedModels) {
        this.maxLoadedModels = maxLoadedModels;
    }

    public boolean isPreloadModel() {
        return preloadModel;
    }

    public void setPreloadModel(boolean preloadModel) {
        this.preloadModel = preloadModel;
    }

    public int getPreloadTimeoutSeconds() {
        return preloadTimeoutSeconds;
    }

    public void setPreloadTimeoutSeconds(int preloadTimeoutSeconds) {
        this.preloadTimeoutSeconds = preloadTimeoutSeconds;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isForceCpu() {
        return forceCpu;
    }

    public void setForceCpu(boolean forceCpu) {
        this.forceCpu = forceCpu;
    }

    public String getLlmLibrary() {
        return llmLibrary;
    }

    public void setLlmLibrary(String llmLibrary) {
        this.llmLibrary = llmLibrary;
    }

    public boolean isNoCloud() {
        return noCloud;
    }

    public void setNoCloud(boolean noCloud) {
        this.noCloud = noCloud;
    }

    public boolean isStopOnShutdown() {
        return stopOnShutdown;
    }

    public void setStopOnShutdown(boolean stopOnShutdown) {
        this.stopOnShutdown = stopOnShutdown;
    }

    public int getStartupTimeoutSeconds() {
        return startupTimeoutSeconds;
    }

    public void setStartupTimeoutSeconds(int startupTimeoutSeconds) {
        this.startupTimeoutSeconds = startupTimeoutSeconds;
    }

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }
}
