package dev.twelvetimes.vector.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("vector")
public record VectorProperties(
        @Min(1) @Max(16) int workerCount,
        @Min(1) @Max(10000) int queueCapacity,
        @Min(1) @Max(100000) int maxPendingTasks,
        @Min(1) @Max(100000) int maxDocuments,
        @Min(1) @Max(16) int searchPermits,
        @Min(1) @Max(20000) int maxTextCodePoints,
        @Min(1) @Max(1048576) int maxRequestBytes,
        @Min(1) @Max(100) int maxTopK,
        @Min(1) @Max(100) int maxPageSize,
        @Min(1) @Max(10000) int cpuRounds,
        @Min(50) @Max(60000) int scanIntervalMs,
        @Min(1) @Max(1000) int scanBatchSize,
        @Min(1) @Max(120) int shutdownWaitSeconds) {
}

