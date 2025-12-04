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
package org.openhab.core.io.rest.mdns.internal;

import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.openhab.core.io.transport.mdns.MDNSService;
import org.openhab.core.io.transport.mdns.ServiceDescription;
import org.openhab.core.net.HttpServiceUtil;
import org.osgi.framework.BundleContext;

/**
 * Tests {@link MDNSAnnouncer}.
 *
 * @author Nuraiman Danial - Initial contribution
 */
class MDNSAnnouncerTest {

    private MDNSService mdnsService;
    private BundleContext bundleContext;
    private MDNSAnnouncer announcer;

    @BeforeEach
    void setup() {
        mdnsService = mock(MDNSService.class);
        bundleContext = mock(BundleContext.class);
        announcer = new MDNSAnnouncer();
        announcer.setMDNSService(mdnsService);
    }

    @Test
    void testActivate() {
        when(bundleContext.getProperty("mdnsName")).thenReturn("openhab");

        Map<String, Object> config = new HashMap<>();
        config.put("enabled", "true");

        try (MockedStatic<HttpServiceUtil> mocked = mockStatic(HttpServiceUtil.class)) {
            mocked.when(() -> HttpServiceUtil.getHttpServicePort(bundleContext)).thenReturn(8080);
            mocked.when(() -> HttpServiceUtil.getHttpServicePortSecure(bundleContext)).thenReturn(8443);

            announcer.activate(bundleContext, config);
        }

        ArgumentCaptor<ServiceDescription> captor = ArgumentCaptor.forClass(ServiceDescription.class);
        verify(mdnsService, times(2)).registerService(captor.capture());

        boolean httpFound = captor.getAllValues().stream()
                .anyMatch(s -> s.servicePort == 8080 && s.serviceType.contains("server._tcp"));
        boolean httpsFound = captor.getAllValues().stream()
                .anyMatch(s -> s.servicePort == 8443 && s.serviceType.contains("server-ssl._tcp"));

        assert (httpFound);
        assert (httpsFound);
    }

    @Test
    void testActivateWhenDisabled() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", "false");

        announcer.activate(bundleContext, config);

        verify(mdnsService, never()).registerService(any());
    }

    @Test
    void testActivateNoService() {
        announcer.unsetMDNSService(mdnsService);

        Map<String, Object> config = new HashMap<>();
        config.put("enabled", "true");

        announcer.activate(bundleContext, config);

        verify(mdnsService, never()).registerService(any());
    }

    @Test
    void testDeactivate() {
        when(bundleContext.getProperty("mdnsName")).thenReturn("openhab");

        Map<String, Object> config = new HashMap<>();
        announcer.activate(bundleContext, config);

        try (MockedStatic<HttpServiceUtil> mocked = mockStatic(HttpServiceUtil.class)) {
            mocked.when(() -> HttpServiceUtil.getHttpServicePort(bundleContext)).thenReturn(8080);
            mocked.when(() -> HttpServiceUtil.getHttpServicePortSecure(bundleContext)).thenReturn(8443);

            announcer.activate(bundleContext, config);
        }

        announcer.deactivate();

        verify(mdnsService, times(2)).unregisterService(any());
    }
}
