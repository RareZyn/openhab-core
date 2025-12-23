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
package org.openhab.core.io.transport.mqtt;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection.MqttVersion;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection.Protocol;

/**
 * Contains configuration for a MqttBrokerConnection.
 *
 * @author David Graeff - Initial contribution
 * @author Mark Herwege - Added flag for hostname validation
 * @author Mark Herwege - Added parameter for cleanSession/cleanStart
 */
@NonNullByDefault
public class MqttBrokerConnectionConfig {
    // Optional connection name
    public @Nullable String name;
    // Connection parameters (host+port+secure+hostnameValidated)
    public @Nullable String host;
    public @Nullable Integer port;
    public boolean secure = true;
    public boolean hostnameValidated = true;
    public boolean cleanSessionStart = true;
    // Protocol parameters
    public Protocol protocol = MqttBrokerConnection.DEFAULT_PROTOCOL;
    public MqttVersion mqttVersion = MqttBrokerConnection.DEFAULT_MQTT_VERSION;
    // Authentication parameters
    public @Nullable String username;
    public @Nullable String password;
    public @Nullable String clientID;
    // MQTT parameters
    public Integer qos = MqttBrokerConnection.DEFAULT_QOS;
    /** Keepalive in seconds */
    public @Nullable Integer keepAlive;
    // Last will parameters
    public @Nullable String lwtTopic;
    public @Nullable String lwtMessage;
    public Integer lwtQos = MqttBrokerConnection.DEFAULT_QOS;
    public Boolean lwtRetain = false;

    /**
     * Return the brokerID of this connection. This is either the name or host:port(:s), for instance "myhost:8080:s".
     * This method will return an empty string, if none of the parameters is set.
     */
    public String getBrokerID() {
        final String name = this.name;
        if (name != null && !name.isEmpty()) {
            return name;
        } else {
            StringBuilder b = new StringBuilder();
            if (host != null) {
                b.append(host);
            }
            final Integer port = this.port;
            if (port != null) {
                b.append(":");
                b.append(port.toString());
            }
            if (secure) {
                b.append(":s");
            }
            return b.toString();
        }
    }

    /**
     * Output the name, host, port, secure flag and hostname validation flag
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (name != null) {
            b.append(name);
            b.append(", ");
        }
        if (host != null) {
            b.append(host);
        }
        final Integer port = this.port;
        if (port != null) {
            b.append(":");
            b.append(port.toString());
        }
        if (secure) {
            b.append(":s");
        }
        if (hostnameValidated) {
            b.append(":v");
        }
        return b.toString();
    }

    /**
     * Validates the configuration parameters.
     * 
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public void validate() throws IllegalArgumentException {
        final String hostValue = this.host;
        if (hostValue == null || hostValue.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        final Integer portValue = this.port;
        if (portValue != null && (portValue <= 0 || portValue > 65535)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("QoS must be 0, 1, or 2");
        }
        if (lwtQos < 0 || lwtQos > 2) {
            throw new IllegalArgumentException("Last Will QoS must be 0, 1, or 2");
        }
        final Integer keepAliveValue = this.keepAlive;
        if (keepAliveValue != null && keepAliveValue <= 0) {
            throw new IllegalArgumentException("Keep alive interval must be greater than 0");
        }
        final String clientIDValue = this.clientID;
        if (clientIDValue != null && clientIDValue.length() > 65535) {
            throw new IllegalArgumentException("Client ID cannot be longer than 65535 characters");
        }
    }

    /**
     * Checks if the configuration has all required parameters set.
     * 
     * @return true if configuration is complete, false otherwise
     */
    public boolean isComplete() {
        return host != null && !host.isEmpty();
    }
}
