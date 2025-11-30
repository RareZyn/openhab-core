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
import org.openhab.core.io.transport.serial.SerialPortEvent;

/**
 * Specific serial port event implementation.
 *
 * @author Markus Rathgeb - Initial contribution
 */
@NonNullByDefault
public class SerialPortEventImpl implements SerialPortEvent {

    private final javax.comm.SerialPortEvent event;

    /**
     * Constructs a new SerialPortEventImpl wrapper around a Java Communications API serial port event.
     *
     * @param event the underlying event implementation, must not be null
     * @throws IllegalArgumentException if event is null
     */
    public SerialPortEventImpl(final javax.comm.SerialPortEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("SerialPortEvent cannot be null");
        }
        this.event = event;
    }

    /**
     * Gets the event type from the underlying serial port event.
     *
     * @return the event type code
     */

    @Override
    public int getEventType() {
        return event.getEventType();
    }

    /**
     * Gets the new value associated with the serial port event.
     *
     * @return the new boolean value
     */
    @Override
    public boolean getNewValue() {
        return event.getNewValue();
    }
}
