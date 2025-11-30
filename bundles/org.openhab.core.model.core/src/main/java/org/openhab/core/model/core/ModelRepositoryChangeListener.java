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
package org.openhab.core.model.core;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface for listeners that are notified when models in the repository change.
 * Implementations can react to model additions, modifications, and removals.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public interface ModelRepositoryChangeListener {

    /**
     * Called when a model in the repository has changed.
     * Performs dispatch of all binding configs and
     * fires all {@link org.openhab.core.items.ItemRegistryChangeListener}s if {@code modelName} ends with "items".
     *
     * @param modelName the name of the model that changed, must not be null
     * @param type the type of change event (ADDED, MODIFIED, or REMOVED), must not be null
     */
    void modelChanged(String modelName, EventType type);
}
