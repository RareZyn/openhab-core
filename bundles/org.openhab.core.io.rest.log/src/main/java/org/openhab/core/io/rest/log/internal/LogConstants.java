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
package org.openhab.core.io.rest.log.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link LogConstants} class defines common constants, which are
 * used across the whole module for REST log handling.
 *
 * <p>
 * This class is intentionally non-instantiable.
 * </p>
 *
 * @author Sebastian Janzen - Initial contribution
 */
@NonNullByDefault
public final class LogConstants {

    private LogConstants() {
        throw new IllegalStateException("Utility class");
    }

    /** Log and response message to express, that the log severity addressed is not handled. */
    public static final String LOG_SEVERITY_IS_NOT_SUPPORTED = "Your log severity is not supported.";

    /** Message for backend logging errors. */
    public static final String LOG_HANDLE_ERROR = "Internal logging error.";

    /** slf4j log pattern to format received log messages, params are URL and message. */
    public static final String FRONTEND_LOG_PATTERN = "Frontend Log ({}): {}";

    /** Max number of logs stored in the ring buffer. */
    public static final int LOG_BUFFER_LIMIT = 500;
}
