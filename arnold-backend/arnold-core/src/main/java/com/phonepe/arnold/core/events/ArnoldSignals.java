/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.core.events;

import io.appform.signals.signals.ConsumingSyncSignal;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ArnoldSignals implements ArnoldSignalPublisher, ArnoldSignalSubscriber {
    private final Map<Class<? extends ArnoldSignal>, ConsumingSyncSignal<? extends ArnoldSignal>> signals =
            new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ArnoldSignal> void publish(final T event) {
        signal((Class<T>) event.getClass()).dispatch(event);
    }

    @Override
    public <T extends ArnoldSignal> void subscribe(final Class<T> signalType,
                                                   final String name,
                                                   final io.appform.signals.signalhandlers.SignalConsumer<T> consumer) {
        signal(signalType).connect(name, consumer);
    }

    @SuppressWarnings("unchecked")
    private <T extends ArnoldSignal> ConsumingSyncSignal<T> signal(final Class<T> signalType) {
        return (ConsumingSyncSignal<T>) signals.computeIfAbsent(signalType, ignored ->
                ConsumingSyncSignal.<T>builder()
                        .errorHandler(failure -> {
                            throw new IllegalStateException("Arnold signal handler failed", failure);
                        })
                        .build());
    }
}
