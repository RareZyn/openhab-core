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

import java.util.Date;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.service.log.LogLevel;

/**
 * The {@link LogDTO} is used for serialization and deserialization of log messages.
 * Implements Comparable based on sequence number for ordering log entries.
 *
 * @author Jan N. Klug - Initial contribution
 * @author Chris Jackson - Add sequence and make Comparable based on sequence
 */
@NonNullByDefault
public class LogDTO implements Comparable<LogDTO> {
    public String loggerName;
    public LogLevel level;
    public Date timestamp;
    public long unixtime;
    public String message;
    public String stackTrace;
    public long sequence;

    /**
     * Constructs a new LogDTO with the specified values.
     *
     * @param sequence the sequence number for ordering, must be non-negative
     * @param loggerName the logger name, must not be null
     * @param level the log level, must not be null
     * @param unixtime the Unix timestamp in milliseconds
     * @param message the log message, must not be null
     * @param stackTrace the stack trace, may be null
     */
    public LogDTO(long sequence, String loggerName, LogLevel level, long unixtime, String message, String stackTrace) {
        if (loggerName == null) {
            throw new IllegalArgumentException("Logger name cannot be null");
        }
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        this.sequence = sequence;
        this.loggerName = loggerName;
        this.level = level;
        this.timestamp = new Date(unixtime);
        this.unixtime = unixtime;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    /**
     * Compares this LogDTO with another based on sequence number.
     *
     * @param o the LogDTO to compare with, must not be null
     * @return a negative integer, zero, or a positive integer as this sequence is less than, equal to, or greater than the other
     */
    @Override
    public int compareTo(LogDTO o) {
        if (o == null) {
            throw new IllegalArgumentException("Cannot compare with null LogDTO");
        }
        return Long.compare(sequence, o.sequence);
    }
}
