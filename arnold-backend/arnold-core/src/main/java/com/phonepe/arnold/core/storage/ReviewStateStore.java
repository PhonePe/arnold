package com.phonepe.arnold.core.storage;

import com.phonepe.arnold.core.model.review.ArnoldReview;

import java.util.Collection;
import java.util.Map;

public interface ReviewStateStore {
    Map<String, ArnoldReview> get(Collection<String> mergeRequestIds);
}
