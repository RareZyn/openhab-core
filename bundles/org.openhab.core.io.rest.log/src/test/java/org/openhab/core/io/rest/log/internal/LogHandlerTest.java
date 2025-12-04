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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.util.List;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Tests {@link LogHandler}.
 *
 * @author Nuraiman Danial - Initial contribution
 */
class LogHandlerTest {

    private LogHandler logHandler;
    private Logger logger;

    @BeforeEach
    void setup() throws Exception {
        logHandler = new LogHandler();
        logger = mock(Logger.class);

        var field = LogHandler.class.getDeclaredField("logger");
        field.setAccessible(true);
        field.set(logHandler, logger);

        when(logger.isDebugEnabled()).thenReturn(true);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isWarnEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);
    }

    // Test logMessage
    @Test
    void testNullLogMessage() {
        Response response = logHandler.log(null);
        assertEquals(500, response.getStatus());
    }

    @Test
    void testInvalidLogMessage() {
        LogHandler.LogMessage msg = new LogHandler.LogMessage();
        msg.severity = null;
        msg.message = "Test";

        Response response = logHandler.log(msg);
        assertEquals(400, response.getStatus());
    }

    @Test
    void testValidLogMessage() throws Exception {
        LogHandler.LogMessage msg = new LogHandler.LogMessage();
        msg.severity = "info";
        msg.message = "info - Test message";
        msg.url = new URL("http://test.com");

        Response response = logHandler.log(msg);

        assertEquals(200, response.getStatus());
        verify(logger).info(anyString(), any(), any());
    }

    // Test severity
    @Test
    void testUnsupportedSeverity() {
        LogHandler.LogMessage msg = new LogHandler.LogMessage();
        msg.severity = "fatal";
        msg.message = "Something happened";

        Response response = logHandler.log(msg);
        assertEquals(403, response.getStatus());
    }

    @Test
    void testSeverityParsing() {
        assertEquals(LogHandler.Severity.ERROR, LogHandler.Severity.fromString("error"));
        assertEquals(LogHandler.Severity.INFO, LogHandler.Severity.fromString("INFO"));
        assertNull(LogHandler.Severity.fromString("invalid"));
    }

    // Test buffer
    @Test
    void testBufferLimit() {
        for (int i = 0; i < 600; i++) {
            LogHandler.LogMessage msg = new LogHandler.LogMessage();
            msg.severity = "info";
            msg.message = "Msg " + i;
            logHandler.log(msg);
        }

        Response response = logHandler.getLastLogs(500);
        Object object = response.getEntity();
        assertTrue(object instanceof List<?>);

        assertEquals(500, ((List<?>) object).size());
    }

    // Test log ordering
    @Test
    void testLogOrdering() {
        LogHandler.LogMessage msg1 = new LogHandler.LogMessage();
        msg1.severity = "info";
        msg1.message = "First";
        logHandler.log(msg1);

        LogHandler.LogMessage msg2 = new LogHandler.LogMessage();
        msg2.severity = "info";
        msg2.message = "Second";
        logHandler.log(msg2);

        Response response = logHandler.getLastLogs(10);
        List<LogHandler.LogMessage> list = (List<LogHandler.LogMessage>) response.getEntity();

        assertEquals("First", list.get(0).message);
        assertEquals("Second", list.get(1).message);
    }
}
