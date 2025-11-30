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
package org.openhab.core.karaf.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.config.discovery.addon.AddonFinderService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * This service is an implementation of an openHAB {@link AddonFinderService} using the Karaf features
 * service. This service allows dynamic installation/removal of add-on suggestion finders.
 *
 * @author Mark Herwege - Initial contribution
 */
@Component(name = "org.openhab.core.karafaddonfinders", immediate = true)
@NonNullByDefault
public class KarafAddonFinderService implements AddonFinderService {
    private final FeatureInstaller featureInstaller;
    private boolean deactivated;

    /**
     * Constructs a new KarafAddonFinderService with the specified FeatureInstaller.
     *
     * @param featureInstaller the FeatureInstaller to use for add-on operations, must not be null
     */
    @Activate
    public KarafAddonFinderService(final @Reference FeatureInstaller featureInstaller) {
        if (featureInstaller == null) {
            throw new IllegalArgumentException("FeatureInstaller cannot be null");
        }
        this.featureInstaller = featureInstaller;
    }

    /**
     * Deactivates the service, preventing further add-on operations.
     */
    @Deactivate
    protected void deactivate() {
        deactivated = true;
    }

    /**
     * Installs an add-on finder with the specified ID.
     *
     * @param id the add-on finder ID to install, must not be null or empty
     */
    @Override
    public void install(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (!deactivated) {
            featureInstaller.addAddon(FeatureInstaller.FINDER_ADDON_TYPE, id);
        }
    }

    /**
     * Uninstalls an add-on finder with the specified ID.
     *
     * @param id the add-on finder ID to uninstall, must not be null or empty
     */
    @Override
    public void uninstall(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (!deactivated) {
            featureInstaller.removeAddon(FeatureInstaller.FINDER_ADDON_TYPE, id);
        }
    }
}
