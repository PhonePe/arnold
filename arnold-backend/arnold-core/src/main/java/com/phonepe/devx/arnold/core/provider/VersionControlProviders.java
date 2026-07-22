package com.phonepe.devx.arnold.core.provider;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class VersionControlProviders {
    private final Map<VersionControlProviderType, VersionControlProvider> providers;

    @Inject
    public VersionControlProviders(final Set<VersionControlProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                VersionControlProvider::type, Function.identity()));
    }

    public VersionControlProvider get(final VersionControlProviderType type) {
        final var provider = providers.get(type);
        if (provider == null) {
            throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED).build();
        }
        return provider;
    }
}
