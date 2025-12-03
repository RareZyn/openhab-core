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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for the model core bundle.
 * Manages the bundle context for the model core functionality.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class ModelCoreActivator implements BundleActivator {

    private static @Nullable BundleContext context;

    /**
     * Gets the bundle context.
     *
     * @return the bundle context, or null if the bundle is not started
     */
    static @Nullable BundleContext getContext() {
        return context;
    }

    /**
     * Starts the bundle and stores the bundle context.
     *
     * @param bundleContext the bundle context, may be null
     * @throws Exception if an error occurs during startup
     */
    @Override
    public void start(@Nullable BundleContext bundleContext) throws Exception {
        ModelCoreActivator.context = bundleContext;
    }

    /**
     * Stops the bundle and clears the bundle context.
     *
     * @param bundleContext the bundle context, may be null
     * @throws Exception if an error occurs during shutdown
     */
    @Override
    public void stop(@Nullable BundleContext bundleContext) throws Exception {
        ModelCoreActivator.context = null;
    }
}
