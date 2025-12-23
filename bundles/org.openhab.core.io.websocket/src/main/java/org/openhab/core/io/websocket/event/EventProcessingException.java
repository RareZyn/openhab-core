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
package org.openhab.core.io.websocket.event;

import java.io.Serial;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link EventProcessingException} is thrown when processing of incoming events fails.
 * This exception is used to indicate errors during event deserialization, validation, or processing.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class EventProcessingException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new EventProcessingException with the specified detail message.
     *
     * @param message the detail message describing the processing failure, must not be null
     */
    public EventProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new EventProcessingException with the specified detail message and cause.
     *
     * @param message the detail message describing the processing failure, must not be null
     * @param cause the cause of the exception, may be null
     */
    public EventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
