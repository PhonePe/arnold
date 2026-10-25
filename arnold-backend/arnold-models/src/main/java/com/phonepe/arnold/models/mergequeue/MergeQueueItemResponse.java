package com.phonepe.arnold.models.mergequeue;

public record MergeQueueItemResponse(String id, String mergeRequestId, String role, boolean blocked) {
}
