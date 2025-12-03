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
package org.openhab.core.config.discovery.addon.mdns.tests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.openhab.core.config.discovery.addon.mdns.MDNSAddonFinder.MDNS_SERVICE_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.openhab.core.addon.AddonDiscoveryMethod;
import org.openhab.core.addon.AddonInfo;
import org.openhab.core.addon.AddonMatchProperty;
import org.openhab.core.addon.AddonParameter;
import org.openhab.core.config.discovery.addon.AddonFinder;
import org.openhab.core.config.discovery.addon.AddonFinderConstants;
import org.openhab.core.config.discovery.addon.AddonSuggestionService;
import org.openhab.core.config.discovery.addon.mdns.MDNSAddonFinder;
import org.openhab.core.io.transport.mdns.MDNSClient;

/**
 * JUnit tests for the {@link AddonSuggestionService}.
 *
 * @author Andrew Fiddian-Green - Initial contribution
 * @author Mark Herwege - Adapted to finders in separate packages
 */
@NonNullByDefault
@TestInstance(Lifecycle.PER_CLASS)
public class MDNSAddonFinderTests {

    private @NonNullByDefault({}) MDNSClient mdnsClient;
    private @NonNullByDefault({}) AddonFinder addonFinder;
    private List<AddonInfo> addonInfos = new ArrayList<>();

    @BeforeAll
    public void setup() {
        setupMockMdnsClient();
        setupAddonInfos();
        createAddonFinder();
    }

    private void createAddonFinder() {
        MDNSAddonFinder mdnsAddonFinder = new MDNSAddonFinder(mdnsClient);
        assertNotNull(mdnsAddonFinder);

        for (ServiceInfo service : mdnsClient.list("_hue._tcp.local.")) {
            mdnsAddonFinder.addService(service, true);
        }
        for (ServiceInfo service : mdnsClient.list("_printer._tcp.local.")) {
            mdnsAddonFinder.addService(service, true);
        }

        // Since we reengineered addService to be Asynchronous (Queue-based),
        // we must wait a moment for the background thread to process these initial services.
        try {
            Thread.sleep(1000); // Wait 1 second for the batch processor to catch up
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        addonFinder = mdnsAddonFinder;
    }

    private void setupMockMdnsClient() {
        // create the mock
        mdnsClient = mock(MDNSClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mdnsClient.list(anyString())).thenReturn(new ServiceInfo[] {});
        ServiceInfo hueService = ServiceInfo.create("hue", "hue", 0, 0, 0, false, "hue service");
        when(mdnsClient.list(eq("_hue._tcp.local."))).thenReturn(new ServiceInfo[] { hueService });
        ServiceInfo hpService = ServiceInfo.create("printer", "hpprinter", 0, 0, 0, false, "hp printer service");
        hpService.setText(Map.of("ty", "hp printer", "rp", "anything"));
        when(mdnsClient.list(eq("_printer._tcp.local."))).thenReturn(new ServiceInfo[] { hpService });

        // check that it works
        assertNotNull(mdnsClient);
        ServiceInfo[] result;
        result = mdnsClient.list("_printer._tcp.local.");
        assertEquals(1, result.length);
        assertEquals("hpprinter", result[0].getName());
        assertEquals(2, Collections.list(result[0].getPropertyNames()).size());
        assertEquals("hp printer", result[0].getPropertyString("ty"));
        result = mdnsClient.list("_hue._tcp.local.");
        assertEquals(1, result.length);
        assertEquals("hue", result[0].getName());
        result = mdnsClient.list("aardvark");
        assertEquals(0, result.length);
    }

    private void setupAddonInfos() {
        AddonDiscoveryMethod hp = new AddonDiscoveryMethod().setServiceType(AddonFinderConstants.SERVICE_TYPE_MDNS)
                .setMatchProperties(
                        List.of(new AddonMatchProperty("rp", ".*"), new AddonMatchProperty("ty", "hp (.*)")))
                .setParameters(List.of(new AddonParameter(MDNS_SERVICE_TYPE, "_printer._tcp.local.")));
        addonInfos.add(AddonInfo.builder("hpprinter", "binding").withName("HP").withDescription("HP Printer")
                .withDiscoveryMethods(List.of(hp)).build());

        AddonDiscoveryMethod hue = new AddonDiscoveryMethod().setServiceType(AddonFinderConstants.SERVICE_TYPE_MDNS)
                .setParameters(List.of(new AddonParameter(MDNS_SERVICE_TYPE, "_hue._tcp.local.")));
        addonInfos.add(AddonInfo.builder("hue", "binding").withName("Hue").withDescription("Hue Bridge")
                .withDiscoveryMethods(List.of(hue)).build());
    }

    @Test
    public void testGetSuggestedAddons() {
        addonFinder.setAddonCandidates(addonInfos);
        Set<AddonInfo> addons = addonFinder.getSuggestedAddons();
        assertEquals(2, addons.size());
        assertFalse(addons.stream().anyMatch(a -> "aardvark".equals(a.getUID())));
        assertTrue(addons.stream().anyMatch(a -> "binding-hue".equals(a.getUID())));
        assertTrue(addons.stream().anyMatch(a -> "binding-hpprinter".equals(a.getUID())));
    }

    // [ADDED] Test for Batch Processing
    @Test
    public void testBatchProcessing() throws InterruptedException {
        // 1. Create finder
        MDNSAddonFinder finder = new MDNSAddonFinder(mdnsClient);

        // 2. Simulate an Event Storm (100 events instantly)
        for (int i = 0; i < 100; i++) {
            ServiceInfo mockService = mock(ServiceInfo.class);
            when(mockService.getQualifiedName()).thenReturn("service" + i);
            when(mockService.getType()).thenReturn("_hue._tcp.local."); // Matches Hue Binding
            finder.addService(mockService, true);
        }

        // 3. Assert - Initially, the map might be empty (or partially full) depending on the scheduler
        // But we want to ensure it DOES process them eventually.

        // Wait for batch interval (e.g., > 500ms)
        Thread.sleep(1000);

        // 4. Set candidates to trigger the matching logic (which reads from the map)
        finder.setAddonCandidates(addonInfos);

        // 5. Assert - Should find the binding because the queue was processed
        Set<AddonInfo> result = finder.getSuggestedAddons();
        assertTrue(result.stream().anyMatch(a -> "binding-hue".equals(a.getUID())),
                "Hue binding should be suggested after batch processing");
    }
}
