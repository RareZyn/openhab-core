/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.model.core.internal;

import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.model.core.SafeEMF;
import org.osgi.service.component.annotations.Component;

/**
 * Implementation of a safe EMF caller.
 * Executes EMF operations in a synchronized manner to ensure thread safety.
 *
 * @author Markus Rathgeb - Initial contribution
 */
@Component
@NonNullByDefault
public class SafeEMFImpl implements SafeEMF {

    /**
     * Calls the given function in a synchronized manner.
     *
     * @param <T> the return type of the function
     * @param func the function to call, must not be null
     * @return the return value of the called function
     * @throws IllegalArgumentException if func is null
     */
    @Override
    public synchronized <T> T call(Supplier<T> func) {
        if (func == null) {
            throw new IllegalArgumentException("Function cannot be null");
        }
        return func.get();
    }

    /**
     * Calls the given runnable in a synchronized manner.
     *
     * @param func the runnable to call, must not be null
     * @throws IllegalArgumentException if func is null
     */
    @Override
    public synchronized void call(Runnable func) {
        if (func == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        func.run();
    }
}
