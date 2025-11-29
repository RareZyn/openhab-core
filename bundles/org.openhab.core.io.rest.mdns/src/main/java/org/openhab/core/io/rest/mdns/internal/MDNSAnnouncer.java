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
package org.openhab.core.io.rest.mdns.internal;

import java.util.Hashtable;
import java.util.Map;

import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.transport.mdns.MDNSService;
import org.openhab.core.io.transport.mdns.ServiceDescription;
import org.openhab.core.net.HttpServiceUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Announces the openHAB REST API over mDNS so that clients can automatically
 * discover it the REST endpoints (HTTP and HTTPS).
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Markus Rathgeb - Use HTTP service utility functions
 */
@Component(immediate = true, configurationPid = "org.openhab.mdns", property = {
        Constants.SERVICE_PID + "=org.openhab.mdns" //
})
public class MDNSAnnouncer {

    private final Logger logger = LoggerFactory.getLogger(MDNSAnnouncer.class);

    private MDNSService mdnsService;

    private int httpSSLPort;
    private int httpPort;
    private String mdnsName;

    private ServiceDescription httpServiceDescription;
    private ServiceDescription httpsServiceDescription;

    @Reference(policy = ReferencePolicy.DYNAMIC)
    public void setMDNSService(MDNSService mdnsService) {
        this.mdnsService = mdnsService;
    }

    public void unsetMDNSService(MDNSService mdnsService) {
        this.mdnsService = null;
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> config) {
        if (isDisabled(config)) {
            logger.info("mDNS announcement is disabled via configuration.");
            return;
        }

        if (mdnsService == null) {
            logger.warn("mDNS service is not available. REST endpoints will not be announced.");
            return;
        }

        mdnsName = bundleContext.getProperty("mdnsName");
        if (mdnsName == null || mdnsName.isBlank()) {
            mdnsName = "openhab";
        }

        logger.info("Activating MDNSAnnouncer using name '{}'", mdnsName);

        readPorts(bundleContext);
        buildServiceDescriptions();

        registerIfValid(httpServiceDescription);
        registerIfValid(httpsServiceDescription);
    }

    @Deactivate
    public void deactivate() {
        if (mdnsService == null) {
            return;
        }

        unregisterSafe(httpServiceDescription);
        unregisterSafe(httpsServiceDescription);

        logger.info("MDNSAnnouncer deactivated.");
    }

    // Helper methods

    private boolean isDisabled(Map<String, Object> config) {
        Object enabled = config.get("enabled");
        return enabled instanceof String && "false".equalsIgnoreCase((String) enabled);
    }

    private void readPorts(BundleContext context) {
        try {
            httpPort = HttpServiceUtil.getHttpServicePort(context);
            logger.debug("HTTP port resolved: {}", httpPort);
        } catch (NumberFormatException e) {
            logger.error("Invalid HTTP port in configuration", e);
        }

        try {
            httpSSLPort = HttpServiceUtil.getHttpServicePortSecure(context);
            logger.debug("HTTPS port resolved: {}", httpSSLPort);
        } catch (NumberFormatException e) {
            logger.error("Invalid HTTPS port in configuration", e);
        }
    }

    private void buildServiceDescriptions() {
        if (httpPort > 0) {
            Hashtable<String, String> props = new Hashtable<>();
            props.put("uri", RESTConstants.REST_URI);

            httpServiceDescription = new ServiceDescription("_" + mdnsName + "-server._tcp.local.", mdnsName, httpPort,
                    props);
        }

        if (httpSSLPort > 0) {
            Hashtable<String, String> props = new Hashtable<>();
            props.put("uri", RESTConstants.REST_URI);

            httpsServiceDescription = new ServiceDescription("_" + mdnsName + "-server-ssl._tcp.local.", mdnsName,
                    httpSSLPort, props);
        }
    }

    private void registerIfValid(ServiceDescription description) {
        if (description == null) {
            return;
        }

        try {
            mdnsService.registerService(description);
            logger.info("Registered mDNS service: {} on port {}", description.serviceType, description.servicePort);
        } catch (Exception e) {
            logger.error("Failed to register mDNS service {}", description.serviceName, e);
        }
    }

    private void unregisterSafe(ServiceDescription description) {
        if (description == null) {
            return;
        }

        try {
            mdnsService.unregisterService(description);
            logger.info("Unregistered mDNS service: {}", description.serviceName);
        } catch (Exception e) {
            logger.error("Error while unregistering mDNS service {}", description.serviceName, e);
        }
    }
}
