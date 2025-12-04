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
package org.openhab.core.io.rest.sse.internal.dto;

import org.osgi.dto.DTO;

/**
 * DTO representing an event broadcasted through SSE.
 * Intentionally simple and serialization-friendly.
 *
 * @author Ivan Iliev - Initial contribution
 * @author Dennis Nobel - Added event type and renamed object to payload
 * @author Markus Rathgeb - Follow the Data Transfer Objects Specification
 */
public class EventDTO extends DTO {

    private String topic;

    private String payload;

    private String type;

    public EventDTO() {
    }

    public EventDTO(String topic, String payload, String type) {
        this.topic = topic;
        this.payload = payload;
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
