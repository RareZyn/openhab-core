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
package org.openhab.core.io.rest.sse.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.events.Event;

/**
 * Marker interface for a publisher of events using SSE.
 *
 * <p>
 * Implementations must be thread-safe.
 * </p>
 *
 * @author Markus Rathgeb - Initial contribution
 */
@NonNullByDefault
public interface SsePublisher {
    /**
     * Broadcasts an event to all relevant SSE listeners.
     *
     * @param event the event to broadcast
     */
    void broadcast(final Event event);
}
