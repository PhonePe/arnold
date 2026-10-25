package com.phonepe.arnold.core.runner;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.model.runner.RunnerJob;
import com.phonepe.arnold.core.storage.RunnerJobRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryRunnerJobRepository implements RunnerJobRepository {
    private final Map<String, Versioned<RunnerJob, Long>> items = new ConcurrentHashMap<>();

    @Override
    public void create(final RunnerJob item) {
        if (items.putIfAbsent(item.id(), Versioned.of(item, 1L)) != null) {
            throw new IllegalStateException("exists");
        }
    }

    @Override
    public void update(final RunnerJob item) {
        items.compute(item.id(), (id, old) -> Versioned.of(item, old == null ? 1L : old.version() + 1));
    }

    @Override
    public void delete(final String id) {
        items.remove(id);
    }

    @Override
    public Optional<RunnerJob> get(final String id) {
        return Optional.ofNullable(items.get(id)).map(Versioned::value);
    }

    @Override
    public Map<String, RunnerJob> get(final List<String> ids) {
        final var result = new java.util.HashMap<String, RunnerJob>();
        ids.forEach(id -> get(id).ifPresent(value -> result.put(id, value)));
        return result;
    }

    @Override
    public List<RunnerJob> list() {
        return new ArrayList<>(items.values().stream().map(Versioned::value).toList());
    }

    @Override
    public Optional<Versioned<RunnerJob, Long>> getWithVersion(final String id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public synchronized void checkAndUpdate(final RunnerJob item, final Long expectedVersion) {
        final var current = items.get(item.id());
        if (current == null || !current.version().equals(expectedVersion)) {
            throw new ConcurrentUpdateException("version mismatch");
        }
        items.put(item.id(), Versioned.of(item, expectedVersion + 1));
    }
}
