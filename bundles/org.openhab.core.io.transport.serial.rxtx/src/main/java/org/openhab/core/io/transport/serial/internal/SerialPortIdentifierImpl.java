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
package org.openhab.core.io.transport.serial.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.rxtx.RxTxSerialPort;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;

/**
 * Specific serial port identifier implementation.
 *
 * @author Markus Rathgeb - Initial contribution
 */
@NonNullByDefault
public class SerialPortIdentifierImpl implements SerialPortIdentifier {

    final CommPortIdentifier id;

    /**
     * Constructs a new SerialPortIdentifierImpl wrapper around an RXTX comm port identifier.
     *
     * @param id the underlying comm port identifier implementation, must not be null
     * @throws IllegalArgumentException if id is null
     */
    public SerialPortIdentifierImpl(final CommPortIdentifier id) {
        if (id == null) {
            throw new IllegalArgumentException("CommPortIdentifier cannot be null");
        }
        this.id = id;
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
     * Opens the serial port for communication.
     *
     * @param owner the name of the application that will own the port
     * @param timeout the timeout in milliseconds to wait for the port to become available
     * @return the opened serial port
     * @throws PortInUseException if the port is already in use by another application
     */
    @Override
    public SerialPort open(String owner, int timeout) throws PortInUseException {
        try {
            final CommPort cp = id.open(owner, timeout);
            if (cp instanceof gnu.io.SerialPort port) {
                return new RxTxSerialPort(port);
            } else {
                throw new IllegalStateException(
                        String.format("We expect a serial port instead of '%s'", cp.getClass()));
            }
        } catch (gnu.io.PortInUseException e) {
            String message = e.getMessage();
            if (message != null) {
                throw new PortInUseException(message, e);
            } else {
                throw new PortInUseException(e);
            }
        }
    }

    @Override
    public boolean isCurrentlyOwned() {
        return id.isCurrentlyOwned();
    }

    @Override
    public @Nullable String getCurrentOwner() {
        return id.getCurrentOwner();
    }
}
