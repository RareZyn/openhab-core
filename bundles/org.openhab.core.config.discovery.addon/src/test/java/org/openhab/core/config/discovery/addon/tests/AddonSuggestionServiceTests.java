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
package org.openhab.core.config.discovery.addon.tests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.openhab.core.config.discovery.addon.AddonFinderConstants.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openhab.core.addon.AddonDiscoveryMethod;
import org.openhab.core.addon.AddonInfo;
import org.openhab.core.addon.AddonInfoProvider;
import org.openhab.core.addon.AddonMatchProperty;
import org.openhab.core.addon.AddonParameter;
import org.openhab.core.config.discovery.addon.AddonFinder;
import org.openhab.core.config.discovery.addon.AddonFinderConstants;
import org.openhab.core.config.discovery.addon.AddonSuggestionService;
import org.openhab.core.i18n.LocaleProvider;

/**
 * JUnit tests for the {@link AddonSuggestionService}.
 *
 * @author Andrew Fiddian-Green - Initial contribution
 * @author Mark Herwege - Adapted to finders in separate packages
 */
@NonNullByDefault
@TestInstance(Lifecycle.PER_CLASS)
public class AddonSuggestionServiceTests {

    public static final String MDNS_SERVICE_TYPE = "mdnsServiceType";

    private @NonNullByDefault({}) LocaleProvider localeProvider;
    private @NonNullByDefault({}) AddonInfoProvider addonInfoProvider;
    private @NonNullByDefault({}) AddonFinder mdnsAddonFinder;
    private @NonNullByDefault({}) AddonFinder upnpAddonFinder;
    private @NonNullByDefault({}) AddonSuggestionService addonSuggestionService;

    private final HashMap<String, Object> config = new HashMap<>(
            Map.of(AddonFinderConstants.CFG_FINDER_MDNS, true, AddonFinderConstants.CFG_FINDER_UPNP, true));

    @AfterAll
    public void cleanUp() {
        assertNotNull(addonSuggestionService);
        try {
            addonSuggestionService.deactivate();
        } catch (Exception e) {
            fail(e);
        }
    }

    @BeforeAll
    public void setup() {
        setupMockLocaleProvider();
        setupMockAddonInfoProvider();
        setupMockMdnsAddonFinder();
        setupMockUpnpAddonFinder();
        addonSuggestionService = createAddonSuggestionService();
    }

    @Test
    public void testConcurrentModification() throws InterruptedException {
        // 1. Setup
        AddonSuggestionService service = new AddonSuggestionService(localeProvider, config);

        // Fill with initial data
        for (int i = 0; i < 50; i++) {
            AddonFinder mockFinder = mock(AddonFinder.class);
            service.addAddonFinder(mockFinder);
        }

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Exception> threadError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);

        // 2. Thread A: The "Reader" (Simulates UI refreshing the Addon Store)
        // This iterates the list repeatedly.
        Thread reader = new Thread(() -> {
            try {
                while (running.get()) {
                    service.getSuggestedAddons(Locale.US);
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                threadError.set(e);
            } finally {
                latch.countDown();
            }
        });

        // 3. Thread B: The "Writer" (Simulates OSGi adding/removing services)
        // This modifies the list while Thread A is reading.
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    AddonFinder newFinder = mock(AddonFinder.class);
                    service.addAddonFinder(newFinder); // WRITE operation
                    Thread.sleep(2);
                    if (i % 2 == 0)
                        service.removeAddonFinder(newFinder); // WRITE operation
                }
            } catch (Exception e) {
                threadError.set(e);
            } finally {
                running.set(false); // Tell reader to stop
                latch.countDown();
            }
        });

        // 4. Act - Start both threads
        reader.start();
        writer.start();
        latch.await(); // Wait for both to finish

        // 5. Assert - No exceptions should have occurred
        if (threadError.get() != null) {
            fail("Concurrency exception occurred: " + threadError.get().getMessage());
        }
    }

    private AddonSuggestionService createAddonSuggestionService() {
        AddonSuggestionService addonSuggestionService = new AddonSuggestionService(localeProvider, config);
        assertNotNull(addonSuggestionService);

        addonSuggestionService.addAddonFinder(mdnsAddonFinder);
        addonSuggestionService.addAddonFinder(upnpAddonFinder);

        return addonSuggestionService;
    }

    private void setupMockLocaleProvider() {
        // create the mock
        localeProvider = mock(LocaleProvider.class);
        when(localeProvider.getLocale()).thenReturn(Locale.US);

        // check that it works
        assertNotNull(localeProvider);
        assertEquals(Locale.US, localeProvider.getLocale());
    }

    private void setupMockAddonInfoProvider() {
        AddonDiscoveryMethod hp = new AddonDiscoveryMethod().setServiceType(AddonFinderConstants.SERVICE_TYPE_MDNS)
                .setMatchProperties(
                        List.of(new AddonMatchProperty("rp", ".*"), new AddonMatchProperty("ty", "hp (.*)")))
                .setParameters(List.of(new AddonParameter(MDNS_SERVICE_TYPE, "_printer._tcp.local.")));

        AddonDiscoveryMethod hue1 = new AddonDiscoveryMethod().setServiceType(AddonFinderConstants.SERVICE_TYPE_UPNP)
                .setMatchProperties(List.of(new AddonMatchProperty("modelName", "Philips hue bridge")));

        AddonDiscoveryMethod hue2 = new AddonDiscoveryMethod().setServiceType(AddonFinderConstants.SERVICE_TYPE_MDNS)
                .setParameters(List.of(new AddonParameter(MDNS_SERVICE_TYPE, "_hue._tcp.local.")));

        // create the mock
        addonInfoProvider = mock(AddonInfoProvider.class);
        Set<AddonInfo> addonInfos = new HashSet<>();
        addonInfos.add(AddonInfo.builder("hue", "binding").withName("Hue").withDescription("Hue Bridge")
                .withDiscoveryMethods(List.of(hue1, hue2)).build());

        addonInfos.add(AddonInfo.builder("hpprinter", "binding").withName("HP").withDescription("HP Printer")
                .withDiscoveryMethods(List.of(hp)).build());
        when(addonInfoProvider.getAddonInfos(any(Locale.class))).thenReturn(addonInfos);

        // check that it works
        assertNotNull(addonInfoProvider);
        Set<AddonInfo> addonInfos2 = addonInfoProvider.getAddonInfos(Locale.US);
        assertEquals(2, addonInfos2.size());
        assertTrue(addonInfos2.stream().anyMatch(a -> "binding-hue".equals(a.getUID())));
        assertTrue(addonInfos2.stream().anyMatch(a -> "binding-hpprinter".equals(a.getUID())));
    }

    private void setupMockMdnsAddonFinder() {
        // create the mock
        mdnsAddonFinder = mock(AddonFinder.class);

        Set<AddonInfo> addonInfos = addonInfoProvider.getAddonInfos(Locale.US).stream().filter(
                c -> c.getDiscoveryMethods().stream().anyMatch(m -> SERVICE_TYPE_MDNS.equals(m.getServiceType())))
                .collect(Collectors.toSet());
        when(mdnsAddonFinder.getSuggestedAddons()).thenReturn(addonInfos);

        // check that it works
        assertNotNull(mdnsAddonFinder);
        Set<AddonInfo> addonInfos2 = mdnsAddonFinder.getSuggestedAddons();
        assertEquals(2, addonInfos2.size());
        assertTrue(addonInfos2.stream().anyMatch(a -> "binding-hue".equals(a.getUID())));
        assertTrue(addonInfos2.stream().anyMatch(a -> "binding-hpprinter".equals(a.getUID())));
    }

    private void setupMockUpnpAddonFinder() {
        // create the mock
        upnpAddonFinder = mock(AddonFinder.class);

        Set<AddonInfo> addonInfos = addonInfoProvider.getAddonInfos(Locale.US).stream().filter(
                c -> c.getDiscoveryMethods().stream().anyMatch(m -> SERVICE_TYPE_UPNP.equals(m.getServiceType())))
                .collect(Collectors.toSet());
        when(upnpAddonFinder.getSuggestedAddons()).thenReturn(addonInfos);

        // check that it works
        assertNotNull(upnpAddonFinder);
        Set<AddonInfo> addonInfos2 = upnpAddonFinder.getSuggestedAddons();
        assertEquals(1, addonInfos2.size());
        assertTrue(addonInfos2.stream().anyMatch(a -> "binding-hue".equals(a.getUID())));
    }

    @Test
    public void testGetSuggestedAddons() {
        addonSuggestionService.addAddonInfoProvider(addonInfoProvider);
        Set<AddonInfo> addons = addonSuggestionService.getSuggestedAddons(localeProvider.getLocale());
        assertEquals(2, addons.size());
        assertFalse(addons.stream().anyMatch(a -> "aardvark".equals(a.getUID())));
        assertTrue(addons.stream().anyMatch(a -> "binding-hue".equals(a.getUID())));
        assertTrue(addons.stream().anyMatch(a -> "binding-hpprinter".equals(a.getUID())));
    }
}
