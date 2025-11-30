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
package org.openhab.core.io.transport.serial.rxtx.rfc2217.internal;

import java.net.URI;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.ProtocolType;
import org.openhab.core.io.transport.serial.ProtocolType.PathType;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortProvider;
import org.osgi.service.component.annotations.Component;

import gnu.io.rfc2217.TelnetSerialPort;

/**
 * Serial port provider implementation for RFC2217 (Telnet Serial Port Control Protocol).
 * This provider supports remote serial ports accessed via TCP/IP using the RFC2217 protocol.
 * Uses the "rfc2217" protocol scheme for network-based serial port connections.
 *
 * @author Matthias Steigenberger - Initial contribution
 */
@NonNullByDefault
@Component(service = SerialPortProvider.class)
public class RFC2217PortProvider implements SerialPortProvider {

    private static final String PROTOCOL = "rfc2217";

    /**
     * Gets a serial port identifier for the specified RFC2217 port URI.
     *
     * @param portName the port URI (e.g., "rfc2217://hostname:port"), must not be null
     * @return the serial port identifier, or null if the port cannot be created
     */
    @Override
    public @Nullable SerialPortIdentifier getPortIdentifier(URI portName) {
        if (portName == null) {
            return null;
        }
        try {
            TelnetSerialPort telnetSerialPort = new TelnetSerialPort();
            telnetSerialPort.setName(portName.toString());
            return new SerialPortIdentifierImpl(telnetSerialPort, portName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the protocol types that this provider accepts.
     *
     * @return a stream containing the "rfc2217" protocol type for network ports
     */
    @Override
    public Stream<ProtocolType> getAcceptedProtocols() {
        return Stream.of(new ProtocolType(PathType.NET, PROTOCOL));
    }

    /**
     * Gets all available serial port identifiers.
     * RFC2217 ports cannot be discovered automatically, so this returns an empty stream.
     *
     * @return an empty stream (RFC2217 ports must be specified explicitly via URI)
     */
    @Override
    public Stream<SerialPortIdentifier> getSerialPortIdentifiers() {
        return Stream.empty();
    }
}
