package com.phonepe.devx.arnold.core.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

    public static boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }
}
