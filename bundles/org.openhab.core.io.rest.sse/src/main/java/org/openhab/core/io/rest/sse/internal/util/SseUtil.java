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
package org.openhab.core.io.rest.sse.internal.util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.Event;
import org.openhab.core.io.rest.sse.internal.dto.EventDTO;

/**
 * Utility class providing helper methods used by the SSE infrastructure.
 *
 * @author Ivan Iliev - Initial contribution
 * @author Dennis Nobel - Changed EventBean
 * @author Markus Rathgeb - Don't depend on specific application but use APIs if possible
 */
@NonNullByDefault
public class SseUtil {
    /** Regex pattern allowing comma-separated tokens with optional "*" wildcard. */
    static final String TOPIC_VALIDATE_PATTERN = "(\\w*\\*?\\/?,?:?-?\\s*)*";

    private SseUtil() {
    }

    /**
     * Converts an {@link Event} to an {@link EventDTO}.
     *
     * @param event the event to convert
     * @return the DTO representing the event
     */
    public static EventDTO buildDTO(final Event event) {
        EventDTO dto = new EventDTO();
        dto.setTopic(event.getTopic());
        dto.setType(event.getType());
        dto.setPayload(event.getPayload());
        return dto;
    }

    /**
     * Creates a new {@link OutboundSseEvent} object containing an {@link EventDTO} created for the given {@link Event}.
     *
     * @param eventBuilder the builder that should be used
     * @param event the event data transfer object
     * @return a new OutboundEvent
     */
    public static OutboundSseEvent buildEvent(OutboundSseEvent.Builder eventBuilder, EventDTO event) {
        return eventBuilder.name("message").mediaType(MediaType.APPLICATION_JSON_TYPE).data(event).build();
    }

    /**
     * Validates the given topicFilter is acceptable for use by SSE.
     *
     * @param topicFilter the topic filter
     * @return true if the given input filter is empty or a valid topic filter string
     *
     */
    public static boolean isValidTopicFilter(@Nullable String topicFilter) {
        return topicFilter == null || topicFilter.isBlank() || topicFilter.matches(TOPIC_VALIDATE_PATTERN);
    }

    /**
     * Converts a comma-separated wildcard topicFilter into a list of regex patterns.
     *
     * @param topicFilter filter expressions
     * @return list of regex patterns
     */
    public static List<String> convertToRegex(@Nullable String topicFilter) {
        List<String> filters = new ArrayList<>();

        if (topicFilter == null || topicFilter.isBlank()) {
            filters.add(".*");
            return filters;
        }

        StringTokenizer tokenizer = new StringTokenizer(topicFilter, ",");
        while (tokenizer.hasMoreElements()) {
            String token = tokenizer.nextToken().trim();
            if (!token.isEmpty()) {
                filters.add(token.replace("*", ".*") + "$");
            }
        }

        return filters;
    }
}
