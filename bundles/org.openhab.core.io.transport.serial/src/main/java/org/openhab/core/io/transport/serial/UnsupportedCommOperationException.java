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
 * Exception that marks that a driver does not allow the specific operation.
 *
 * @author Markus Rathgeb - Initial contribution
 */
@NonNullByDefault
public class UnsupportedCommOperationException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new UnsupportedCommOperationException with no detail message.
     */
    public UnsupportedCommOperationException() {
    }

    /**
     * Constructs a new UnsupportedCommOperationException with the specified detail message.
     *
     * @param message the detail message
     */
    public UnsupportedCommOperationException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnsupportedCommOperationException with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public UnsupportedCommOperationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new UnsupportedCommOperationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public UnsupportedCommOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
