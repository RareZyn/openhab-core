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
package org.openhab.core.io.websocket.log;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link LogFilterDTO} is used for serialization and deserialization of log filters.
 * Represents filter criteria for querying log messages by time range, logger names, and sequence numbers.
 *
 * @author Chris Jackson - Initial contribution
 */
@NonNullByDefault
public class LogFilterDTO {

    /** Start time for filtering logs (Unix timestamp in milliseconds), may be null */
    public @Nullable Long timeStart;
    /** Stop time for filtering logs (Unix timestamp in milliseconds), may be null */
    public @Nullable Long timeStop;
    /** List of logger names to filter by, may be null */
    public @Nullable List<String> loggerNames;
    /** Starting sequence number for filtering logs, may be null */
    public @Nullable Long sequenceStart;
}
