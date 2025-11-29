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

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JSONRequired;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST handler for logging frontend messages to the backend
 * and retrieve recent logs.
 *
 * @author Sebastian Janzen - Initial contribution
 * @author Markus Rathgeb - Migrated to JAX-RS Whiteboard Specification
 * @author Wouter Born - Migrated to OpenAPI annotations
 */
@Component
@JaxrsResource
@JaxrsName(LogHandler.PATH_LOG)
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@JSONRequired
@Path(LogHandler.PATH_LOG)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = LogHandler.PATH_LOG)
@NonNullByDefault
public class LogHandler implements RESTResource {

    private final Logger logger = LoggerFactory.getLogger(LogHandler.class);
    public static final String PATH_LOG = "log";

    private static final String TEMPLATE_INTERNAL_ERROR = "{\"error\":\"%s\",\"severity\":\"%s\"}";

    /**
     * Rolling array to store the last LOG_BUFFER_LIMIT messages. Those can be fetched e.g. by a
     * diagnostic UI to display errors of other clients, where e.g. the logs are not easily accessible.
     */
    private final ConcurrentLinkedDeque<LogMessage> logBuffer = new ConcurrentLinkedDeque<>();

    /**
     * Container for a log message sent from frontend clients.
     *
     * <p>
     * Instances are mutable and intended to be deserialized from JSON requests. Each
     * message contains a severity, optional URL, message text, and timestamp.
     * </p>
     */
    public static class LogMessage {
        public long timestamp;

        /** Must be one of: error, warn, info, debug. */
        public @Nullable String severity;

        public @Nullable URL url;
        public @Nullable String message;

        /** Helper validation method for safer handling. */
        public boolean isValid() {
            return severity != null && !severity.isBlank() && message != null && !message.isBlank();
        }
    }

    /**
     * Returns the currently enabled log levels for the backend logger.
     *
     * @return A map containing the log levels.
     */
    @GET
    @Path("/levels")
    @Operation(operationId = "getLogLevels", summary = "Get log severities, which are logged by the current logger settings.", responses = {
            @ApiResponse(responseCode = "200", description = "This depends on the current log settings at the backend.") })
    public Response getLogLevels() {
        return Response.ok(createLogLevelsMap()).build();
    }

    private List<LogMessage> getLatestLogs(int limit) {
        List<LogMessage> snapshot = new ArrayList<>(logBuffer);

        int fromIndex = Math.max(0, snapshot.size() - limit);
        List<LogMessage> result = new ArrayList<>(snapshot.subList(fromIndex, snapshot.size()));

        Collections.reverse(result);
        return result;
    }

    /**
     * Returns the last logged frontend messages up to the specified limit.
     *
     * @param limit Maximum number of messages to return (default: {@link LogConstants#LOG_BUFFER_LIMIT})
     * @return A list of {@link LogMessage} objects.
     */
    @GET
    @Operation(operationId = "getLastLogMessagesForFrontend", summary = "Returns the last logged frontend messages. The amount is limited to the "
            + LogConstants.LOG_BUFFER_LIMIT + " last entries.")
    public Response getLastLogs(@DefaultValue(LogConstants.LOG_BUFFER_LIMIT
            + "") @QueryParam("limit") @Parameter(name = "limit", schema = @Schema(implementation = Integer.class, minimum = "1", maximum = ""
                    + LogConstants.LOG_BUFFER_LIMIT)) @Nullable Integer limit) {

        int effectiveLimit = (limit == null || limit < 1) ? LogConstants.LOG_BUFFER_LIMIT
                : Math.min(limit, LogConstants.LOG_BUFFER_LIMIT);

        return Response.ok(getLatestLogs(effectiveLimit)).build();
    }

    /**
     * Logs a frontend message to the backend.
     *
     * @param logMessage Log message sent from a client.
     * @return HTTP 200 if logged successfully, 400 for invalid payload, 403 if severity is unsupported, 500 if the
     *         payload is null
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "logMessageToBackend", summary = "Log a frontend log message to the backend.", responses = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = LogConstants.LOG_SEVERITY_IS_NOT_SUPPORTED) })
    public Response log(
            final @Parameter(name = "logMessage", description = "Severity is required and can be one of error, warn, info or debug, depending on activated severities which you can GET at /logLevels.", example = "{\"severity\": \"error\", \"url\": \"http://example.org\", \"message\": \"Error message\"}") @Nullable LogMessage logMessage) {
        if (logMessage == null) {
            logger.debug("Received null log message model!");
            return Response.status(500)
                    .entity(String.format(TEMPLATE_INTERNAL_ERROR, LogConstants.LOG_HANDLE_ERROR, "ERROR")).build();
        }
        logMessage.timestamp = ZonedDateTime.now().toInstant().toEpochMilli();

        if (!logMessage.isValid()) {
            logger.debug("Invalid log message received: {}", logMessage);
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Invalid log message payload\"}")
                    .build();
        }

        if (!doLog(logMessage)) {
            return Response.status(403).entity(String.format(TEMPLATE_INTERNAL_ERROR,
                    LogConstants.LOG_SEVERITY_IS_NOT_SUPPORTED, logMessage.severity)).build();
        }

        logBuffer.addFirst(logMessage);
        while (logBuffer.size() > LogConstants.LOG_BUFFER_LIMIT) {
            logBuffer.pollLast();
        }

        return Response.ok(null, MediaType.TEXT_PLAIN).build();
    }

    /**
     * Executes the logging call using slf4j.
     *
     * @param logMessage The message to log
     * @return False if severity is not supported, true if successfully logged.
     */
    private boolean doLog(LogMessage logMessage) {
        Severity severity = Severity.fromString(logMessage.severity);

        switch (severity) {
            case ERROR -> logger.error(LogConstants.FRONTEND_LOG_PATTERN, logMessage.url, logMessage.message);
            case WARN -> logger.warn(LogConstants.FRONTEND_LOG_PATTERN, logMessage.url, logMessage.message);
            case INFO -> logger.info(LogConstants.FRONTEND_LOG_PATTERN, logMessage.url, logMessage.message);
            case DEBUG -> logger.debug(LogConstants.FRONTEND_LOG_PATTERN, logMessage.url, logMessage.message);
            case null -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Return map of currently logged messages. They can change at runtime.
     */
    private Map<String, Boolean> createLogLevelsMap() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("error", logger.isErrorEnabled());
        result.put("warn", logger.isWarnEnabled());
        result.put("info", logger.isInfoEnabled());
        result.put("debug", logger.isDebugEnabled());
        return result;
    }

    /**
     * Reduce usage of large switch on strings in {@link LogHandler#doLog(LogMessage)}
     *
     */
    public enum Severity {
        ERROR,
        WARN,
        INFO,
        DEBUG;

        public static @Nullable Severity fromString(@Nullable String s) {
            if (s == null)
                return null;
            try {
                return Severity.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
