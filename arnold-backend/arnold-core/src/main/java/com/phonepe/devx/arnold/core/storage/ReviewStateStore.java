package com.phonepe.devx.arnold.core.storage;

import com.phonepe.devx.arnold.core.model.review.ArnoldReview;

import java.util.Collection;
import java.util.Map;

public interface ReviewStateStore {
    Map<String, ArnoldReview> get(Collection<String> mergeRequestIds);
}
