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
package org.openhab.core.io.transport.mqtt.internal;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.util.HexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

/**
 * This class keeps track of all the subscribers to a specific topic.
 * <p>
 * <b>Retained</b> messages for the topic are stored so they can be replayed to new subscribers.
 *
 * @author Jochen Klein - Initial contribution
 */
@NonNullByDefault
public class Subscription {
    private final Logger logger = LoggerFactory.getLogger(Subscription.class);
    private final Map<String, byte[]> retainedMessages = new ConcurrentHashMap<>();
    private final Collection<MqttMessageSubscriber> subscribers = ConcurrentHashMap.newKeySet();

    /**
     * Add a new subscriber to this subscription.
     * <p>
     * If there are any retained messages for this topic, they will be delivered to the new subscriber
     * immediately upon subscription.
     *
     * @param subscriber The subscriber to add. Must not be null.
     * @return true if the subscriber was successfully added, false if it was already present
     */
    public boolean add(MqttMessageSubscriber subscriber) {
        if (subscribers.add(subscriber)) {
            // new subscriber. deliver all known retained messages
            retainedMessages.entrySet().stream().forEach(entry -> {
                if (entry.getValue().length > 0) {
                    processMessage(subscriber, entry.getKey(), entry.getValue());
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Remove a subscriber from the list.
     *
     * @param subscriber The subscriber to remove. Must not be null.
     * @return true if the subscriber was removed, false if it was not in the list
     */
    public boolean remove(MqttMessageSubscriber subscriber) {
        return subscribers.remove(subscriber);
    }

    /**
     * Check if there are any active subscribers for this subscription.
     *
     * @return true if there are no subscribers, false otherwise
     */
    public boolean isEmpty() {
        return subscribers.isEmpty();
    }

    /**
     * Get the number of active subscribers for this subscription.
     *
     * @return The number of subscribers
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    /**
     * Handle an incoming MQTT v3 publish message.
     *
     * @param message The MQTT v3 publish message
     */
    public void messageArrived(Mqtt3Publish message) {
        messageArrived(message.getTopic().toString(), message.getPayloadAsBytes(), message.isRetain());
    }

    /**
     * Handle an incoming MQTT v5 publish message.
     *
     * @param message The MQTT v5 publish message
     */
    public void messageArrived(Mqtt5Publish message) {
        messageArrived(message.getTopic().toString(), message.getPayloadAsBytes(), message.isRetain());
    }

    /**
     * Process an incoming message for this subscription.
     * <p>
     * According to MQTT specification (http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc385349265),
     * only the first message delivered will have the retain flag; subsequent messages will not have the flag set.
     * Therefore, we check if we retained it in the past, and continue to retain it (even if it's now empty -
     * we need to know to continue to retain it).
     *
     * @param topic The topic the message was published to
     * @param payload The message payload
     * @param retain Whether this is a retained message
     */
    public void messageArrived(String topic, byte[] payload, boolean retain) {
        if (topic == null || topic.isEmpty()) {
            logger.warn("Received message with null or empty topic, ignoring");
            return;
        }
        if (payload == null) {
            logger.warn("Received message with null payload for topic '{}', using empty payload", topic);
            payload = new byte[0];
        }
        // http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc385349265
        // Only the first message delivered will have the retain flag; subsequent messages
        // will not have the flag set. So see if we retained it in the past, and continue
        // to retain it (even if it's now empty - we need to know to continue to retain it)
        if (retain || retainedMessages.containsKey(topic)) {
            retainedMessages.put(topic, payload);
        }
        subscribers.forEach(subscriber -> processMessage(subscriber, topic, payload));
    }

    /**
     * Process a message for a specific subscriber, handling any exceptions that may occur.
     *
     * @param subscriber The subscriber to process the message for
     * @param topic The topic of the message
     * @param payload The message payload
     */
    private void processMessage(MqttMessageSubscriber subscriber, String topic, byte[] payload) {
        try {
            subscriber.processMessage(topic, payload);
        } catch (RuntimeException e) {
            logger.warn("A subscriber of type '{}' failed to process message '{}' to topic '{}': {}",
                    subscriber.getClass().getName(), HexUtils.bytesToHex(payload), topic, e.getMessage(), e);
        }
    }
}
