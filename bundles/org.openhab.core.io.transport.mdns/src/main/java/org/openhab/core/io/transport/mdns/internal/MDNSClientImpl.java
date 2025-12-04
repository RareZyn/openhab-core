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
package org.openhab.core.io.transport.mdns.internal;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.openhab.core.io.transport.mdns.MDNSClient;
import org.openhab.core.io.transport.mdns.ServiceDescription;
import org.openhab.core.net.CidrAddress;
import org.openhab.core.net.NetworkAddressChangeListener;
import org.openhab.core.net.NetworkAddressService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class starts the JmDNS and implements interface to register and unregister services.
 *
 * @author Victor Belov - Initial contribution
 * @author Gary Tse - Add NetworkAddressChangeListener to handle interface changes
 */
@Component(immediate = true, service = MDNSClient.class)
public class MDNSClientImpl implements MDNSClient, NetworkAddressChangeListener {
    private final Logger logger = LoggerFactory.getLogger(MDNSClientImpl.class);

    private final ConcurrentMap<InetAddress, JmDNS> jmdnsInstances = new ConcurrentHashMap<>();

    // service that should be active (the Set is thread-safe)
    private final Set<ServiceDescription> activeServices = ConcurrentHashMap.newKeySet();

    private final NetworkAddressService networkAddressService;

    private final NetworkInterfaceProvider nifProvider;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mdns-client-worker");
        t.setDaemon(true);
        return t;
    });

    @Activate
    public MDNSClientImpl(final @Reference NetworkAddressService networkAddressService,
            final @Reference(cardinality = ReferenceCardinality.OPTIONAL) NetworkInterfaceProvider nifProvider) {
        this.networkAddressService = Objects.requireNonNull(networkAddressService, "networkAddressService");
        this.nifProvider = nifProvider == null ? new DefaultNetworkInterfaceProvider() : nifProvider;
    }

    private Set<InetAddress> getAllInetAddresses() {
        final Set<InetAddress> addresses = new LinkedHashSet<>();
        Enumeration<NetworkInterface> itInterfaces;
        try {
            itInterfaces = nifProvider.getNetworkInterfaces();
        } catch (final SocketException e) {
            logger.warn("Unable to enumerate network interfaces: {}", e.getMessage(), e);
            return addresses;
        }
        while (itInterfaces.hasMoreElements()) {
            final NetworkInterface iface = itInterfaces.nextElement();
            try {
                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }
            } catch (final SocketException ex) {
                logger.debug("Unable to query interface state for {}: {}", iface, ex.getMessage(), ex);
                continue;
            }

            InetAddress primaryIPv4HostAddress = null;

            if (networkAddressService.isUseOnlyOneAddress()
                    && networkAddressService.getPrimaryIpv4HostAddress() != null) {
                final Enumeration<InetAddress> itAddresses = iface.getInetAddresses();
                while (itAddresses.hasMoreElements()) {
                    final InetAddress address = itAddresses.nextElement();
                    if (address.getHostAddress().equals(networkAddressService.getPrimaryIpv4HostAddress())) {
                        primaryIPv4HostAddress = address;
                        break;
                    }
                }
            }

            final Enumeration<InetAddress> itAddresses = iface.getInetAddresses();
            boolean ipv4addressAdded = false;
            boolean ipv6addressAdded = false;
            while (itAddresses.hasMoreElements()) {
                final InetAddress address = itAddresses.nextElement();
                if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || (!networkAddressService.isUseIPv6() && address instanceof Inet6Address)) {
                    continue;
                }
                if (networkAddressService.isUseOnlyOneAddress()) {
                    // add only one address per interface and family
                    if (address instanceof Inet4Address) {
                        if (!ipv4addressAdded) {
                            if (primaryIPv4HostAddress != null) {
                                // use configured primary address instead of first one
                                addresses.add(primaryIPv4HostAddress);
                            } else {
                                addresses.add(address);
                            }
                            ipv4addressAdded = true;
                        }
                    } else if (address instanceof Inet6Address) {
                        if (!ipv6addressAdded) {
                            addresses.add(address);
                            ipv6addressAdded = true;
                        }
                    }
                } else {
                    addresses.add(address);
                }
            }
        }
        return addresses;
    }

    @Override
    public Set<JmDNS> getClientInstances() {
        return new HashSet<>(jmdnsInstances.values());
    }

    @Activate
    protected void activate() {
        networkAddressService.addNetworkAddressChangeListener(this);
        // run startup on executor to avoid blocking the activation thread
        executor.execute(this::start);
    }

    private void start() {
        for (InetAddress address : getAllInetAddresses()) {
            createJmDNSByAddress(address);
        }
        for (ServiceDescription description : activeServices) {
            try {
                registerServiceInternal(description);
            } catch (IOException e) {
                logger.warn("Exception while registering service {}: {}", description, e.getMessage(), e);
            }
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            networkAddressService.removeNetworkAddressChangeListener(this);
        } catch (Exception e) {
            logger.debug("Error removing network address listener: {}", e.getMessage(), e);
        }

        close();
        activeServices.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void addServiceListener(String type, ServiceListener listener) {
        jmdnsInstances.values().forEach(jmdns -> jmdns.addServiceListener(type, listener));
    }

    @Override
    public void removeServiceListener(String type, ServiceListener listener) {
        jmdnsInstances.values().forEach(jmdns -> jmdns.removeServiceListener(type, listener));
    }

    @Override
    public void registerService(ServiceDescription description) throws IOException {
        activeServices.add(description);
        registerServiceInternal(description);
    }

    private void registerServiceInternal(ServiceDescription description) throws IOException {
        for (JmDNS instance : jmdnsInstances.values()) {
            try {
                logger.debug("Registering new service {} at {}:{} ({})", description.serviceType,
                        instance.getInetAddress().getHostAddress(), description.servicePort, instance.getName());
                // Create one ServiceInfo object for each JmDNS instance
                ServiceInfo serviceInfo = ServiceInfo.create(description.serviceType, description.serviceName,
                        description.servicePort, 0, 0, description.serviceProperties);
                instance.registerService(serviceInfo);
            } catch (IOException e) {
                logger.warn("Failed to register service {} on instance {}: {}", description, instance, e.getMessage(),
                        e);
            }
        }
    }

    @Override
    public void unregisterService(ServiceDescription description) {
        activeServices.remove(description);
        unregisterServiceInternal(description);
    }

    private void unregisterServiceInternal(ServiceDescription description) {
        for (JmDNS instance : jmdnsInstances.values()) {
            try {
                logger.debug("Unregistering service {} at {}:{} ({})", description.serviceType,
                        instance.getInetAddress().getHostAddress(), description.servicePort, instance.getName());
            } catch (IOException e) {
                logger.debug("Unregistering service {} ({})", description.serviceType, instance.getName());
            }
            try {
                ServiceInfo serviceInfo = ServiceInfo.create(description.serviceType, description.serviceName,
                        description.servicePort, 0, 0, description.serviceProperties);
                instance.unregisterService(serviceInfo);
            } catch (Exception e) {
                logger.debug("Failed to unregister {} on {}: {}", description, instance, e.getMessage(), e);
            }
        }
    }

    @Override
    public void unregisterAllServices() {
        activeServices.clear();
        for (JmDNS instance : jmdnsInstances.values()) {
            try {
                instance.unregisterAllServices();
            } catch (Exception e) {
                logger.debug("Error while unregistering all service on {}: {}", instance.getName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public ServiceInfo[] list(String type) {
        List<ServiceInfo> result = new ArrayList<>();
        for (JmDNS instance : jmdnsInstances.values()) {
            ServiceInfo[] services = instance.list(type);
            if (services != null && services.length > 0) {
                Collections.addAll(result, services);
            }
        }
        return result.toArray(new ServiceInfo[0]);
    }

    @Override
    public ServiceInfo[] list(String type, Duration timeout) {
        List<ServiceInfo> result = new ArrayList<>();
        for (JmDNS instance : jmdnsInstances.values()) {
            ServiceInfo[] services = instance.list(type, timeout.toMillis());
            if (services != null && services.length > 0) {
                Collections.addAll(result, services);
            }
        }
        return result.toArray(new ServiceInfo[0]);
    }

    @Override
    public void close() {
        List<JmDNS> instance = new ArrayList<>(jmdnsInstances.values());
        for (JmDNS jmdns : instance) {
            try {
                jmdns.close();
                logger.debug("mDNS service has been stopped ({})", jmdns.getName());
            } catch (Exception e) {
                logger.warn("Error closing JmDNS instance {}: {}", jmdns.getName(), e.getMessage(), e);
            }
        }
        jmdnsInstances.clear();
    }

    private void createJmDNSByAddress(InetAddress address) {
        try {
            JmDNS jmdns = JmDNS.create(address, null);
            jmdnsInstances.put(address, jmdns);
            logger.debug("mDNS service has been started ({} for IP {})", jmdns.getName(), address.getHostAddress());
        } catch (IOException e) {
            logger.debug("JmDNS instantiation failed ({})!", address.getHostAddress());
        }
    }

    @Override
    public void onChanged(List<CidrAddress> added, List<CidrAddress> removed) {
        logger.debug("ip address change: added {}, removed {}", added, removed);

        Set<InetAddress> filteredAddresses = getAllInetAddresses();

        // First check if there is really a jmdns instance to remove or add
        boolean changeRequired = false;
        for (InetAddress address : jmdnsInstances.keySet()) {
            if (!filteredAddresses.contains(address)) {
                changeRequired = true;
                break;
            }
        }
        if (!changeRequired) {
            for (InetAddress address : filteredAddresses) {
                if (!jmdnsInstances.containsKey(address)) {
                    changeRequired = true;
                    break;
                }
            }
        }
        if (!changeRequired) {
            logger.debug("mDNS services already OK for these ip addresses");
            return;
        }

        for (ServiceDescription description : activeServices) {
            unregisterServiceInternal(description);
        }
        Set<InetAddress> existingAddress = new HashSet<>(jmdnsInstances.keySet());
        for (InetAddress address : existingAddress) {
            if (!filteredAddresses.contains(address)) {
                JmDNS jmdns = jmdnsInstances.remove(address);
                if (jmdns != null) {
                    try {
                        jmdns.close();
                        logger.debug("mDNS service has been stopped ({} for IP {})", jmdns.getName(),
                                address.getHostAddress());
                    } catch (Exception e) {
                        logger.debug("Error closing JmDNS {}: {}", jmdns.getName(), e.getMessage(), e);
                    }
                }
            }
        }
        for (InetAddress address : filteredAddresses) {
            if (!jmdnsInstances.containsKey(address)) {
                createJmDNSByAddress(address);
            } else {
                JmDNS jmdns = jmdnsInstances.get(address);
                logger.debug("mDNS service was already started ({} for IP {})", jmdns.getName(),
                        address.getHostAddress());
            }
        }
        for (ServiceDescription description : activeServices) {
            try {
                registerServiceInternal(description);
            } catch (IOException e) {
                logger.warn("Exception while registering service {}: {}", description, e.getMessage(), e);
            }
        }
    }
}
