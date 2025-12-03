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

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.Event;

/**
 * The {@link EventDTO} is used for serialization and deserialization of events
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class EventDTO {
    public @Nullable String type;
    public @Nullable String topic;
    public @Nullable String payload;
    public @Nullable String source;

    public @Nullable String eventId;

    /**
     * Default constructor for deserialization.
     */
    public EventDTO() {
    }

    /**
     * Constructs a new EventDTO with the specified values.
     *
     * @param type the event type, must not be null
     * @param topic the event topic, must not be null
     * @param payload the event payload, may be null
     * @param source the event source, may be null
     * @param eventId the event ID, may be null
     */
    public EventDTO(String type, String topic, @Nullable String payload, @Nullable String source,
            @Nullable String eventId) {
        this.type = type;
        this.topic = topic;
        this.payload = payload;
        this.source = source;
        this.eventId = eventId;
    }

    /**
     * Constructs a new EventDTO from an Event object.
     *
     * @param event the event to convert, must not be null
     */
    public EventDTO(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        type = event.getType();
        topic = event.getTopic();
        source = event.getSource();
        payload = event.getPayload();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventDTO eventDTO = (EventDTO) o;
        return Objects.equals(type, eventDTO.type) && Objects.equals(topic, eventDTO.topic)
                && Objects.equals(payload, eventDTO.payload) && Objects.equals(source, eventDTO.source)
                && Objects.equals(eventId, eventDTO.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, topic, payload, source, eventId);
    }

    @Override
    public String toString() {
        return "EventDTO{type='" + type + "', topic='" + topic + "', payload='" + payload + "', source='" + source
                + "', eventId='" + eventId + "'}";
    }
}
