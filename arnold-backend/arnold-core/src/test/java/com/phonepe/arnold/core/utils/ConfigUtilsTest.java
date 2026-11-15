/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.utils;

import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigUtilsTest {

    @Test
    void mappedSupplier_withNullSupplier_throwsNullPointerException() {
        Supplier<String> supplier = null;
        Function<String, Integer> mapper = Integer::parseInt;
        Supplier<Integer> mappedSupplier = ConfigUtils.mappedSupplier(supplier, mapper);
        assertThrows(NullPointerException.class, () -> mappedSupplier.get());
    }

    @Test
    void mappedSupplier_withNullMapper_throwsNullPointerException() {
        Supplier<String> supplier = () -> "123";
        Function<String, Integer> mapper = null;
        Supplier<Integer> mappedSupplier = ConfigUtils.mappedSupplier(supplier, mapper);
        assertThrows(NullPointerException.class, () -> mappedSupplier.get());
    }

    @Test
    void mappedSupplier_withNonNullArguments_returnsMappedSupplier() {
        Supplier<String> supplier = () -> "123";
        Function<String, Integer> mapper = Integer::parseInt;
        Supplier<Integer> mappedSupplier = ConfigUtils.mappedSupplier(supplier, mapper);
        assertEquals(123, mappedSupplier.get());
    }
}
