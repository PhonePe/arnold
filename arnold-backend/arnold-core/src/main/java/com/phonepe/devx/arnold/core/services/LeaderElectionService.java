package com.phonepe.devx.arnold.core.services;

public interface LeaderElectionService {
    Leadership acquireOrRenew(String electionName, String contenderId, long leaseMs);

    Leadership current(String electionName);

    void release(String electionName, String contenderId);

    record Leadership(boolean leader, String ownerId, long leaseUntil) {
    }
}
