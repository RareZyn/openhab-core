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
package org.openhab.core.config.discovery.addon.usb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openhab.core.addon.AddonDiscoveryMethod;
import org.openhab.core.addon.AddonInfo;
import org.openhab.core.addon.AddonMatchProperty;
import org.openhab.core.config.discovery.usbserial.UsbSerialDeviceInformation;
import org.openhab.core.config.discovery.usbserial.UsbSerialDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * This contains tests for the {@link UsbAddonFinder} class.
 *
 * @author Andrew Fiddian-Green - Initial contribution
 */
@NonNullByDefault
@TestInstance(Lifecycle.PER_CLASS)
class UsbAddonFinderTests {

    @Test
    void testSuggestionFinder() {
        Logger root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ((ch.qos.logback.classic.Logger) root).setLevel(Level.ERROR);

        AddonMatchProperty matchProperty = new AddonMatchProperty("product", "(?i).*zigbee.*");

        AddonDiscoveryMethod discoveryMethod = new AddonDiscoveryMethod();
        discoveryMethod.setMatchProperties(List.of(matchProperty)).setServiceType("usb");

        List<AddonInfo> addons = new ArrayList<>();
        addons.add(AddonInfo.builder("id", "binding").withName("name").withDescription("description")
                .withDiscoveryMethods(List.of(discoveryMethod)).build());

        UsbAddonFinder finder = new UsbAddonFinder();
        finder.setAddonCandidates(addons);

        finder.usbSerialDeviceDiscovered(
                new UsbSerialDeviceInformation(0x123, 0x234, null, null, null, 0, "n/a", "n/a"));

        Set<AddonInfo> suggestions = finder.getSuggestedAddons();
        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());

        finder.usbSerialDeviceDiscovered(
                new UsbSerialDeviceInformation(0x345, 0x456, null, null, "some zigBEE product", 0, "n/a", "n/a"));

        suggestions = finder.getSuggestedAddons();
        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
    }

    @Test
    void testBadSyntax() {
        Logger root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ((ch.qos.logback.classic.Logger) root).setLevel(Level.ERROR);

        AddonMatchProperty matchProperty = new AddonMatchProperty("aardvark", "(?i).*zigbee.*");

        AddonDiscoveryMethod discoveryMethod = new AddonDiscoveryMethod();
        discoveryMethod.setMatchProperties(List.of(matchProperty)).setServiceType("usb");

        List<AddonInfo> addons = new ArrayList<>();
        addons.add(AddonInfo.builder("id", "binding").withName("name").withDescription("description")
                .withDiscoveryMethods(List.of(discoveryMethod)).build());

        UsbAddonFinder finder = new UsbAddonFinder();
        finder.setAddonCandidates(addons);

        finder.usbSerialDeviceDiscovered(
                new UsbSerialDeviceInformation(0x123, 0x234, null, null, null, 0, "n/a", "n/a"));

        Set<AddonInfo> suggestions = finder.getSuggestedAddons();
        assertNotNull(suggestions);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void testNonBlockingConcurrency() throws InterruptedException {
        UsbAddonFinder finder = new UsbAddonFinder();

        // Thread 1: Add/Remove Discoveries (Write)
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                UsbSerialDiscovery mockDiscovery = mock(UsbSerialDiscovery.class);
                finder.addUsbSerialDiscovery(mockDiscovery);
                finder.removeUsbSerialDiscovery(mockDiscovery); // Should not block
            }
        });

        // Thread 2: Read Suggestions (Read)
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                finder.getSuggestedAddons(); // Should be able to read while writer is working
            }
        });

        writer.start();
        reader.start();

        writer.join(2000);
        reader.join(2000);

        // If threads joined successfully, no deadlock occurred.
    }
}
