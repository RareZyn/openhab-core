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

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Default production implementation that delegates to {@link NetworkInterface#getNetworkInterfaces()}
 *
 * @author Nuraiman Danial - Initial contribution
 */
@NonNullByDefault
public class DefaultNetworkInterfaceProvider implements NetworkInterfaceProvider {

    @Override
    public Enumeration<NetworkInterface> getNetworkInterfaces() throws SocketException {
        return NetworkInterface.getNetworkInterfaces();
    }
}
