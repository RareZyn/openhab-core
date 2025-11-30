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
package org.openhab.core.io.transport.mdns;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * This is a simple immutable data container a service description.
 *
 * @author Kai Kreuzer - Initial contribution
 */
public final class ServiceDescription {

    public final String serviceType;
    public final String serviceName;
    public final int servicePort;
    public final Map<String, String> serviceProperties;

    /**
     * Constructor for a {@link ServiceDescription}, which takes all details as parameters
     *
     * @param serviceType String service type, like "_openhab-server._tcp.local."
     * @param serviceName String service name, like "openHAB"
     * @param servicePort Int service port, like 8080
     * @param serviceProperties Hashtable service props, like url = "/rest"
     */
    public ServiceDescription(String serviceType, String serviceName, int servicePort,
            Map<String, String> serviceProperties) {
        this.serviceType = serviceType;
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.serviceProperties = serviceProperties == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(serviceProperties);
    }

    /**
     * Builder for ServiceDescription
     *
     * @return a Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serviceType;
        private String serviceName;
        private int servicePort;
        private Map<String, String> serviceProperties;

        public Builder withType(String type) {
            this.serviceType = type;
            return this;
        }

        public Builder withName(String name) {
            this.serviceName = name;
            return this;
        }

        public Builder withPort(int port) {
            this.servicePort = port;
            return this;
        }

        public Builder withProperties(Map<String, String> props) {
            this.serviceProperties = props;
            return this;
        }

        public ServiceDescription build() {
            return new ServiceDescription(serviceType, serviceName, servicePort, serviceProperties);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceType, serviceName, servicePort);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ServiceDescription other))
            return false;
        return Objects.equals(serviceType, other.serviceType) && Objects.equals(serviceName, other.serviceName)
                && servicePort == other.servicePort;
    }

    @Override
    public String toString() {
        return "ServiceDescription [serviceType=" + serviceType + ", serviceName=" + serviceName + ", servicePort="
                + servicePort + "]";
    }
}
