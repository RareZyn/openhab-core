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
package org.openhab.core.model.item.runtime.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.model.ItemsStandaloneSetup;
import org.openhab.core.model.core.ModelParser;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGi component that activates the Items DSL runtime module.
 * This activator registers the Items model parser with the model repository,
 * allowing the system to parse and process .items configuration files.
 * Implements ModelParser to provide the file extension for items files.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
@Component(immediate = true)
public class ItemRuntimeActivator implements ModelParser {

    private final Logger logger = LoggerFactory.getLogger(ItemRuntimeActivator.class);

    /**
     * Activates the Items runtime module by setting up the standalone Xtext infrastructure.
     * This registers the Items DSL parser with the model repository.
     *
     * @throws Exception if the setup fails
     */
    public void activate() throws Exception {
        try {
            ItemsStandaloneSetup.doSetup();
            logger.debug("Registered 'item' configuration parser");
        } catch (Exception e) {
            logger.error("Failed to activate Items runtime module: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Deactivates the Items runtime module by unregistering the Xtext infrastructure.
     * This removes the Items DSL parser from the model repository.
     *
     * @throws Exception if the unregistration fails
     */
    public void deactivate() throws Exception {
        try {
            ItemsStandaloneSetup.unregister();
            logger.debug("Unregistered 'item' configuration parser");
        } catch (Exception e) {
            logger.error("Failed to deactivate Items runtime module: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Returns the file extension that this parser handles.
     *
     * @return the file extension "items" (without the dot)
     */
    @Override
    public String getExtension() {
        return "items";
    }
}
