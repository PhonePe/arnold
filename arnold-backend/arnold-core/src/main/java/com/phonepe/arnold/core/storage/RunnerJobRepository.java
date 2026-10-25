package com.phonepe.arnold.core.storage;

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.Store;
import com.phonepe.arnold.core.model.runner.RunnerJob;

public interface RunnerJobRepository extends Store<RunnerJob>, ConcurrentStore<RunnerJob, Long> {
}
