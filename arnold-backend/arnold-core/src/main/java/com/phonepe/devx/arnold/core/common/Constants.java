package com.phonepe.devx.arnold.core.common;

import com.google.common.base.Strings;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public final String HOSTNAME = hostname();
    public static final String OLYMPUS = "Olympus";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String OLYMPUS_TOKEN ="Olympus token";

    private String hostname() {
        final String host = System.getenv("HOST");
        return Strings.isNullOrEmpty(host) ? "UNKNOWN" : host;
    }
}
