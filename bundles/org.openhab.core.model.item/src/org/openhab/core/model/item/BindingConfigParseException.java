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
package org.openhab.core.model.item;

import java.io.Serial;

/**
 * This exception is used by {@link BindingConfigReader} instances if parsing configurations fails.
 * Indicates that a binding configuration string could not be parsed correctly.
 *
 * @author Kai Kreuzer - Initial contribution
 */
public class BindingConfigParseException extends Exception {

    @Serial
    private static final long serialVersionUID = 1434607160082879845L;

    /**
     * Constructs a new BindingConfigParseException with the specified detail message.
     *
     * @param msg the detail message describing the parsing failure, must not be null
     */
    public BindingConfigParseException(String msg) {
        super(msg);
    }

    /**
     * Constructs a new BindingConfigParseException with the specified detail message and cause.
     *
     * @param msg the detail message describing the parsing failure, must not be null
     * @param e the cause of the exception, may be null
     */
    public BindingConfigParseException(String msg, Exception e) {
        super(msg, e);
    }
}
