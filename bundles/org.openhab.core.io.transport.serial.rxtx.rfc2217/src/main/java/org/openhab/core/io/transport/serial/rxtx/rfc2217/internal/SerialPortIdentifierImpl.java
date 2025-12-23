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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.rxtx.RxTxSerialPort;

import gnu.io.rfc2217.TelnetSerialPort;

/**
 * Specific serial port identifier implementation for RFC2217.
 *
 * @author Matthias Steigenberger - Initial contribution
 */
@NonNullByDefault
public class SerialPortIdentifierImpl implements SerialPortIdentifier {

    final TelnetSerialPort id;
    private final URI uri;

    /**
     * Constructs a new SerialPortIdentifierImpl for an RFC2217 remote serial port.
     *
     * @param id the underlying TelnetSerialPort implementation, must not be null
     * @param uri the URI of the remote serial port, must not be null
     * @throws IllegalArgumentException if id or uri is null
     */
    public SerialPortIdentifierImpl(final TelnetSerialPort id, URI uri) {
        if (id == null) {
            throw new IllegalArgumentException("TelnetSerialPort cannot be null");
        }
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        this.id = id;
        this.uri = uri;
    }

    /**
     * Gets the name of the serial port.
     *
     * @return the port name, or an empty string if the name is null
     */

    @Override
    public String getName() {
        final String name = id.getName();
        return name != null ? name : "";
    }

    /**
     * Opens the remote serial port for communication via RFC2217.
     *
     * @param owner the name of the application that will own the port
     * @param timeout the timeout in milliseconds to wait for the connection to be established
     * @return the opened serial port
     * @throws PortInUseException if the port is already in use or connection fails
     * @throws IllegalStateException if the connection cannot be established
     */
    @Override
    public SerialPort open(String owner, int timeout) throws PortInUseException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative");
        }
        final String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalStateException(String.format("Invalid URI: host is null or empty for %s", uri));
        }
        final int port = uri.getPort();
        if (port < 0 || port > 65535) {
            throw new IllegalStateException(String.format("Invalid URI: port is out of range (0-65535) for %s", uri));
        }
        try {
            id.getTelnetClient().setConnectTimeout(timeout);
            id.getTelnetClient().connect(host, port);
            return new RxTxSerialPort(id);
        } catch (java.net.SocketTimeoutException e) {
            throw new PortInUseException(
                    String.format("Connection timeout while connecting to remote serial port %s", uri), e);
        } catch (java.net.ConnectException e) {
            throw new PortInUseException(
                    String.format("Unable to connect to remote serial port %s (port may be in use)", uri), e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    String.format("Unable to establish remote connection to serial port %s", uri), e);
        }
    }

    /**
     * Determines whether the remote serial port is currently in use.
     *
     * @return true if the port is currently owned (socket is not available), false otherwise
     */
    @Override
    public boolean isCurrentlyOwned() {
        // Check if the socket is not available for use, if true interpret as being owned.
        return !id.getTelnetClient().isAvailable();
    }

    /**
     * Returns the current owner of the remote serial port.
     * For RFC2217 remote ports, the owner information is not available.
     *
     * @return null (owner information is not available for remote connections)
     */
    @Override
    public @Nullable String getCurrentOwner() {
        // Unknown who owns a socket connection. Therefore return null.
        return null;
    }
}
