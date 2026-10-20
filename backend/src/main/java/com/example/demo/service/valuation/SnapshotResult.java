package com.example.demo.service.valuation;

public record SnapshotResult(int saved, int skipped) {

    public SnapshotResult {
        if (saved < 0 || skipped < 0) {
            throw new IllegalArgumentException("saved and skipped must be non-negative");
        }
    }
}
