package com.phonepe.devx.arnold.core.utils;

import com.google.common.base.Strings;
import com.phonepe.devx.arnold.core.auth.ArnoldPrincipal;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.stream.Collectors;

@UtilityClass
public class NameUtils {

    public String sanitizedName(ArnoldPrincipal principal) {
        if (!Strings.isNullOrEmpty(principal.getDisplayName())) {
            return sanitizedName(principal.getDisplayName());
        }
        return sanitizedName(Strings.isNullOrEmpty(principal.getEmail()) ? principal.getId() : principal.getEmail());
    }

    public String sanitizedName(String email) {
        if (email == null) {
            return "";
        }
        final var trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        final var atIndex = trimmed.indexOf('@');
        final var localPart = atIndex >= 0 ? trimmed.substring(0, atIndex) : trimmed;

        // Splitting by non-letters turns numbers and symbols into delimiters automatically
        return Arrays.stream(localPart.split("[^a-zA-Z]+"))
                .filter(token -> !token.isEmpty())
                .map(NameUtils::capitalize)
                .collect(Collectors.joining(" "));
    }

    private String capitalize(String token) {
        // At this point, token ONLY contains pure a-z / A-Z letters
        return Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase();
    }
}
