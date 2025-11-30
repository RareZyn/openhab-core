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
package org.openhab.core.karaf.internal;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * This is a java bean that is used to define logger settings for the REST interface.
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class LoggerBean {

    public final List<LoggerInfo> loggers;

    /**
     * Information about a single logger configuration.
     */
    public static class LoggerInfo {
        public final String loggerName;
        public final String level;

        /**
         * Constructs a new LoggerInfo with the specified logger name and level.
         *
         * @param loggerName the logger name, must not be null
         * @param level the log level, must not be null
         */
        public LoggerInfo(String loggerName, String level) {
            if (loggerName == null) {
                throw new IllegalArgumentException("Logger name cannot be null");
            }
            if (level == null) {
                throw new IllegalArgumentException("Log level cannot be null");
            }
            this.loggerName = loggerName;
            this.level = level;
        }
    }

    /**
     * Constructs a new LoggerBean from a map of logger names to log levels.
     *
     * @param logLevels the map of logger names to log levels, must not be null
     */
    public LoggerBean(Map<String, String> logLevels) {
        if (logLevels == null) {
            throw new IllegalArgumentException("Log levels map cannot be null");
        }
        loggers = logLevels.entrySet().stream().map(l -> new LoggerInfo(l.getKey(), l.getValue())).toList();
    }
}
