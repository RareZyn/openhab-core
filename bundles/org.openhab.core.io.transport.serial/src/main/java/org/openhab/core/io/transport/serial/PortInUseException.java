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
package org.openhab.core.io.transport.serial;

import java.io.Serial;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Exception that marks that a port is currently already in use.
 *
 * @author Markus Rathgeb - Initial contribution
 */
@NonNullByDefault
public class PortInUseException extends Exception {

    @Serial
    private static final long serialVersionUID = -2709480420743139383L;

    /**
     * Constructs a new PortInUseException with the specified detail message.
     *
     * @param message the detail message
     */
    public PortInUseException(String message) {
        super(message);
    }

    /**
     * Constructs a new PortInUseException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public PortInUseException(String message, Exception cause) {
        super(message, cause);
    }

    /**
     * Constructs a new PortInUseException with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public PortInUseException(Exception cause) {
        super(cause);
    }
}
