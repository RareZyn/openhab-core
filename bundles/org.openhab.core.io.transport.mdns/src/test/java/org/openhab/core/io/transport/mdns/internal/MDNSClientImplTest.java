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
package org.openhab.core.io.transport.mdns.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.Vector;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.net.NetworkAddressService;

/**
 * Tests {@link MDNSClientImpl}.
 *
 * @author Nuraiman Danial - Initial contribution
 */
@NonNullByDefault
class MDNSClientImplTest {

    @Test
    public void testGetAllInetAddress() throws Exception {
        NetworkInterfaceProvider provider = mock(NetworkInterfaceProvider.class);
        NetworkAddressService nas = mock(NetworkAddressService.class);

        NetworkInterface iface = mock(NetworkInterface.class);
        when(iface.isUp()).thenReturn(true);
        when(iface.isLoopback()).thenReturn(false);
        when(iface.isPointToPoint()).thenReturn(false);

        InetAddress address = InetAddress.getByName("192.168.1.100");
        Enumeration<InetAddress> addrs = Collections.enumeration(Collections.singletonList(address));
        when(iface.getInetAddresses()).thenReturn(addrs);

        Vector<NetworkInterface> ifaces = new Vector<>();
        ifaces.add(iface);
        when(provider.getNetworkInterfaces()).thenReturn(ifaces.elements());

        // networkAddressService defaults
        when(nas.isUseOnlyOneAddress()).thenReturn(false);
        when(nas.isUseIPv6()).thenReturn(false);

        MDNSClientImpl client = new MDNSClientImpl(nas, provider);
        Method m = MDNSClientImpl.class.getDeclaredMethod("getAllInetAddresses");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<InetAddress> result = (Set<InetAddress>) m.invoke(client);

        assertTrue(result.contains(address));
    }
}
