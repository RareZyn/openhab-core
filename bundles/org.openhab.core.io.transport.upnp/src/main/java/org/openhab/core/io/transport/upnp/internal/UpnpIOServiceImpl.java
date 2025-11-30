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
package org.openhab.core.io.transport.upnp.internal;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.jupnp.UpnpService;
import org.jupnp.controlpoint.ActionCallback;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.controlpoint.SubscriptionCallback;
import org.jupnp.model.action.ActionArgumentValue;
import org.jupnp.model.action.ActionException;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.gena.CancelReason;
import org.jupnp.model.gena.GENASubscription;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Action;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.state.StateVariableValue;
import org.jupnp.model.types.ServiceId;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.io.transport.upnp.UpnpIOParticipant;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpIOServiceImpl} is the implementation of the UpnpIOService
 * interface
 *
 * @author Karel Goderis - Initial contribution; added simple polling mechanism
 * @author Kai Kreuzer - added descriptor url retrieval
 * @author Markus Rathgeb - added NP checks in subscription ended callback
 * @author Andre Fuechsel - added methods to remove subscriptions
 * @author Ivan Iliev - made sure resubscribe is only done when subscription ended CancelReason was EXPIRED or
 *         RENEW_FAILED
 */
@SuppressWarnings("rawtypes")
@Component(immediate = true)
public class UpnpIOServiceImpl implements UpnpIOService, RegistryListener {

    private final Logger logger = LoggerFactory.getLogger(UpnpIOServiceImpl.class);

    private final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool(POOL_NAME);

    private static final int DEFAULT_POLLING_INTERVAL = 60;
    private static final String POOL_NAME = "upnp-io";

    private final UpnpService upnpService;

    final Set<UpnpIOParticipant> participants = new CopyOnWriteArraySet<>();
    final Map<UpnpIOParticipant, ScheduledFuture> pollingJobs = new ConcurrentHashMap<>();
    final Map<UpnpIOParticipant, Boolean> currentStates = new ConcurrentHashMap<>();
    final Map<Service, UpnpSubscriptionCallback> subscriptionCallbacks = new ConcurrentHashMap<>();

    public class UpnpSubscriptionCallback extends SubscriptionCallback {

        public UpnpSubscriptionCallback(Service service) {
            super(service);
        }

        public UpnpSubscriptionCallback(Service service, int requestedDurationSeconds) {
            super(service, requestedDurationSeconds);
        }

        @Override
        protected void ended(GENASubscription subscription, CancelReason reason, UpnpResponse response) {
            final Service service = subscription.getService();
            if (service != null) {
                final ServiceId serviceId = service.getServiceId();
                final Device device = service.getDevice();
                if (device != null) {
                    final Device deviceRoot = device.getRoot();
                    if (deviceRoot != null) {
                        final DeviceIdentity deviceRootIdentity = deviceRoot.getIdentity();
                        if (deviceRootIdentity != null) {
                            final UDN deviceRootUdn = deviceRootIdentity.getUdn();
                            logger.debug("A GENA subscription '{}' for device '{}' was ended", serviceId.getId(),
                                    deviceRootUdn);
                        }
                    }
                }

                if ((CancelReason.EXPIRED.equals(reason) || CancelReason.RENEWAL_FAILED.equals(reason))
                        && upnpService != null) {
                    final ControlPoint cp = upnpService.getControlPoint();
                    if (cp != null) {
                        final UpnpSubscriptionCallback callback = new UpnpSubscriptionCallback(service,
                                subscription.getActualDurationSeconds());
                        cp.execute(callback);
                    }
                }
            }
        }

        @Override
        protected void established(GENASubscription subscription) {
            Device deviceRoot = subscription.getService().getDevice().getRoot();
            String serviceId = subscription.getService().getServiceId().getId();

            logger.trace("A GENA subscription '{}' for device '{}' is established", serviceId,
                    deviceRoot.getIdentity().getUdn());

            for (UpnpIOParticipant participant : participants) {
                if (Objects.equals(getDevice(participant), deviceRoot)) {
                    try {
                        participant.onServiceSubscribed(serviceId, true);
                    } catch (Exception e) {
                        logger.error("Participant threw an exception onServiceSubscribed", e);
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void eventReceived(GENASubscription sub) {
            Map<String, StateVariableValue> values = sub.getCurrentValues();
            Device device = sub.getService().getDevice();
            String serviceId = sub.getService().getServiceId().getId();

            logger.trace("Receiving a GENA subscription '{}' response for device '{}'", serviceId,
                    device.getIdentity().getUdn());

            for (UpnpIOParticipant participant : participants) {
                Device participantDevice = getDevice(participant);
                if (Objects.equals(participantDevice, device) || Objects.equals(participantDevice, device.getRoot())) {
                    for (Entry<String, StateVariableValue> entry : values.entrySet()) {
                        Object value = entry.getValue().getValue();
                        if (value != null) {
                            try {
                                participant.onValueReceived(entry.getKey(), value.toString(), serviceId);
                            } catch (Exception e) {
                                logger.error("Participant threw an exception onValueReceived", e);
                            }
                        }
                    }
                    break;
                }
            }
        }

        @Override
        protected void eventsMissed(GENASubscription subscription, int numberOfMissedEvents) {
            logger.debug("A GENA subscription '{}' for device '{}' missed events",
                    subscription.getService().getServiceId(),
                    subscription.getService().getDevice().getRoot().getIdentity().getUdn());
        }

        @Override
        protected void failed(GENASubscription subscription, UpnpResponse response, Exception e, String defaultMsg) {
            Device deviceRoot = subscription.getService().getDevice().getRoot();
            String serviceId = subscription.getService().getServiceId().getId();

            logger.debug("A GENA subscription '{}' for device '{}' failed", serviceId,
                    deviceRoot.getIdentity().getUdn());

            for (UpnpIOParticipant participant : participants) {
                if (Objects.equals(getDevice(participant), deviceRoot)) {
                    try {
                        participant.onServiceSubscribed(serviceId, false);
                    } catch (Exception e2) {
                        logger.error("Participant threw an exception onServiceSubscribed", e2);
                    }
                }
            }
        }
    }

    @Activate
    public UpnpIOServiceImpl(final @Reference UpnpService upnpService) {
        this.upnpService = upnpService;
    }

    @Activate
    public void activate() {
        logger.debug("Starting UPnP IO service...");
        upnpService.getRegistry().getRemoteDevices().forEach(device -> informParticipants(device, true));
        upnpService.getRegistry().addListener(this);
    }

    @Deactivate
    public void deactivate() {
        logger.debug("Stopping UPnP IO service...");
        upnpService.getRegistry().removeListener(this);
    }

    private Device getDevice(UpnpIOParticipant participant) {
        return upnpService.getRegistry().getDevice(new UDN(participant.getUDN()), false);
    }

    /**
     * Subscribe to a GENA subscription for the specified participant and service.
     *
     * @param participant the participant to subscribe for, must not be null
     * @param serviceID the UPnP service to subscribe to, must not be null
     * @param duration the subscription duration in seconds, must be positive
     */
    @Override
    public void addSubscription(UpnpIOParticipant participant, String serviceID, int duration) {
        if (participant == null) {
            logger.warn("Cannot add subscription: participant is null");
            return;
        }
        if (serviceID == null || serviceID.isEmpty()) {
            logger.warn("Cannot add subscription: serviceID is null or empty for participant '{}'", participant.getUDN());
            return;
        }
        if (duration <= 0) {
            logger.warn("Cannot add subscription: duration must be positive (was: {}) for participant '{}'", duration,
                    participant.getUDN());
            return;
        }
        registerParticipant(participant);
        Device device = getDevice(participant);
        if (device != null) {
            Service subService = searchSubService(serviceID, device);
            if (subService != null) {
                logger.trace("Setting up a UPnP service subscription '{}' for participant '{}'", serviceID,
                        participant.getUDN());

                UpnpSubscriptionCallback callback = new UpnpSubscriptionCallback(subService, duration);
                subscriptionCallbacks.put(subService, callback);
                upnpService.getControlPoint().execute(callback);
            } else {
                logger.trace("Could not find service '{}' for device '{}'", serviceID,
                        device.getIdentity().getUdn());
            }
        } else {
            logger.trace("Could not find an upnp device for participant '{}'", participant.getUDN());
        }
    }

    private Service searchSubService(String serviceID, Device device) {
        Service subService = findService(device, null, serviceID);
        if (subService == null) {
            // service not on the root device, we search the embedded devices as well
            Device[] embedded = device.getEmbeddedDevices();
            if (embedded != null) {
                for (Device aDevice : embedded) {
                    subService = findService(aDevice, null, serviceID);
                    if (subService != null) {
                        break;
                    }
                }
            }
        }
        return subService;
    }

    /**
     * Unsubscribe from a GENA subscription for the specified participant and service.
     *
     * @param participant the participant to unsubscribe for, must not be null
     * @param serviceID the UPnP service to unsubscribe from, must not be null
     */
    @Override
    public void removeSubscription(UpnpIOParticipant participant, String serviceID) {
        if (participant == null) {
            logger.warn("Cannot remove subscription: participant is null");
            return;
        }
        if (serviceID == null || serviceID.isEmpty()) {
            logger.warn("Cannot remove subscription: serviceID is null or empty for participant '{}'",
                    participant.getUDN());
            return;
        }
        Device device = getDevice(participant);
        if (device != null) {
            Service subService = searchSubService(serviceID, device);
            if (subService != null) {
                logger.trace("Removing a UPnP service subscription '{}' for participant '{}'", serviceID,
                        participant.getUDN());

                UpnpSubscriptionCallback callback = subscriptionCallbacks.remove(subService);
                if (callback != null) {
                    callback.end();
                }
            } else {
                logger.trace("Could not find service '{}' for device '{}'", serviceID,
                        device.getIdentity().getUdn());
            }
        } else {
            logger.trace("Could not find an upnp device for participant '{}'", participant.getUDN());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> invokeAction(UpnpIOParticipant participant, String serviceID, String actionID,
            Map<String, String> inputs) {
        return invokeAction(participant, null, serviceID, actionID, inputs);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> invokeAction(UpnpIOParticipant participant, @Nullable String namespace, String serviceID,
            String actionID, Map<String, String> inputs) {
        Map<String, String> resultMap = new HashMap<>();

        if (serviceID != null && actionID != null && participant != null) {
            registerParticipant(participant);
            Device device = getDevice(participant);

            if (device != null) {
                Service service = findService(device, namespace, serviceID);
                if (service != null) {
                    Action action = service.getAction(actionID);
                    if (action != null) {
                        ActionInvocation invocation = new ActionInvocation(action);
                        if (inputs != null) {
                            for (Entry<String, String> entry : inputs.entrySet()) {
                                invocation.setInput(entry.getKey(), entry.getValue());
                            }
                        }

                        logger.trace("Invoking Action '{}' of service '{}' for participant '{}'", actionID, serviceID,
                                participant.getUDN());
                        new ActionCallback.Default(invocation, upnpService.getControlPoint()).run();

                        ActionException anException = invocation.getFailure();
                        if (anException != null && anException.getMessage() != null) {
                            logger.debug("{}", anException.getMessage());
                        }

                        Map<String, ActionArgumentValue> result = invocation.getOutputMap();
                        if (result != null) {
                            for (Entry<String, ActionArgumentValue> entry : result.entrySet()) {
                                String variable = entry.getKey();
                                final ActionArgumentValue newArgument;
                                try {
                                    newArgument = entry.getValue();
                                } catch (final Exception ex) {
                                    logger.debug("An exception '{}' occurred, cannot get argument for variable '{}'",
                                            ex.getMessage(), variable);
                                    continue;
                                }
                                try {
                                    if (newArgument.getValue() != null) {
                                        resultMap.put(variable, newArgument.getValue().toString());
                                    }
                                } catch (final Exception ex) {
                                    logger.debug(
                                            "An exception '{}' occurred processing ActionArgumentValue '{}' with value '{}'",
                                            ex.getMessage(), newArgument.getArgument().getName(),
                                            newArgument.getValue());
                                }
                            }
                        }
                    } else {
                        logger.debug("Could not find action '{}' for participant '{}'", actionID, participant.getUDN());
                    }
                } else {
                    logger.debug("Could not find service '{}' for participant '{}'", serviceID, participant.getUDN());
                }
            } else {
                logger.debug("Could not find an upnp device for participant '{}'", participant.getUDN());
            }
        }
        return resultMap;
    }

    @Override
    public boolean isRegistered(UpnpIOParticipant participant) {
        return upnpService.getRegistry().getDevice(new UDN(participant.getUDN()), false) != null;
    }

    @Override
    public void registerParticipant(UpnpIOParticipant participant) {
        if (participant != null) {
            participants.add(participant);
        }
    }

    @Override
    public void unregisterParticipant(UpnpIOParticipant participant) {
        if (participant != null) {
            stopPollingForParticipant(participant);
            pollingJobs.remove(participant);
            currentStates.remove(participant);
            participants.remove(participant);
        }
    }

    @Override
    public URL getDescriptorURL(UpnpIOParticipant participant) {
        RemoteDevice device = upnpService.getRegistry().getRemoteDevice(new UDN(participant.getUDN()), true);
        if (device != null) {
            return device.getIdentity().getDescriptorURL();
        } else {
            return null;
        }
    }

    private Service findService(Device device, @Nullable String namespace, String serviceID) {
        Service service;
        if (namespace == null) {
            namespace = device.getType().getNamespace();
        }
        if (UDAServiceId.DEFAULT_NAMESPACE.equals(namespace)
                || UDAServiceId.BROKEN_DEFAULT_NAMESPACE.equals(namespace)) {
            service = device.findService(new UDAServiceId(serviceID));
        } else {
            service = device.findService(new ServiceId(namespace, serviceID));
        }
        return service;
    }

    /**
     * Propagates a device status change to all participants
     *
     * @param device the device that has changed its status
     * @param status true, if device is reachable, false otherwise
     */
    private void informParticipants(RemoteDevice device, boolean status) {
        for (UpnpIOParticipant participant : participants) {
            if (participant.getUDN().equals(device.getIdentity().getUdn().getIdentifierString())) {
                setDeviceStatus(participant, status);
            }
        }
    }

    /**
     * Updates the device status for a participant and notifies if the status changed.
     *
     * @param participant the participant whose device status to update, must not be null
     * @param newStatus the new status (true = reachable, false = unreachable)
     */
    private void setDeviceStatus(UpnpIOParticipant participant, boolean newStatus) {
        if (participant == null) {
            return;
        }
        if (!Objects.equals(currentStates.get(participant), newStatus)) {
            currentStates.put(participant, newStatus);
            logger.debug("Device '{}' reachability status changed to '{}'", participant.getUDN(), newStatus);
            try {
                participant.onStatusChanged(newStatus);
            } catch (Exception e) {
                logger.error("Participant threw an exception onStatusChanged for device '{}'", participant.getUDN(), e);
            }
        }
    }

    private class UPNPPollingRunnable implements Runnable {

        private final UpnpIOParticipant participant;
        private final String serviceID;
        private final String actionID;

        public UPNPPollingRunnable(UpnpIOParticipant participant, String serviceID, String actionID) {
            this.participant = participant;
            this.serviceID = serviceID;
            this.actionID = actionID;
        }

        @Override
        public void run() {
            // It is assumed that during addStatusListener() a check is made whether the participant is correctly
            // registered
            try {
                Device device = getDevice(participant);
                if (device != null) {
                    Service service = findService(device, null, serviceID);
                    if (service != null) {
                        Action action = service.getAction(actionID);
                        if (action != null) {
                            @SuppressWarnings("unchecked")
                            ActionInvocation invocation = new ActionInvocation(action);
                            logger.debug("Polling participant '{}' through Action '{}' of Service '{}' ",
                                    participant.getUDN(), actionID, serviceID);
                            new ActionCallback.Default(invocation, upnpService.getControlPoint()).run();

                            ActionException anException = invocation.getFailure();
                            if (anException != null
                                    && anException.getMessage().contains("Connection error or no response received")) {
                                // The UDN is not reachable anymore
                                setDeviceStatus(participant, false);
                            } else {
                                // The UDN functions correctly
                                setDeviceStatus(participant, true);
                            }
                        } else {
                            logger.debug("Could not find action '{}' for participant '{}'", actionID,
                                    participant.getUDN());
                        }
                    } else {
                        logger.debug("Could not find service '{}' for participant '{}'", serviceID,
                                participant.getUDN());
                    }
                }
            } catch (Exception e) {
                logger.error("An exception occurred while polling an UPNP device: '{}'", e.getMessage(), e);
            }
        }
    }

    /**
     * Establish a polling mechanism to check the status of a specific UDN device.
     *
     * @param participant the participant to set up polling for, must not be null
     * @param serviceID the service to use for polling, must not be null
     * @param actionID the action to call, must not be null
     * @param interval the polling interval in seconds (0 uses default), must be non-negative
     */
    @Override
    public void addStatusListener(UpnpIOParticipant participant, String serviceID, String actionID, int interval) {
        if (participant == null) {
            logger.warn("Cannot add status listener: participant is null");
            return;
        }
        if (serviceID == null || serviceID.isEmpty()) {
            logger.warn("Cannot add status listener: serviceID is null or empty for participant '{}'",
                    participant.getUDN());
            return;
        }
        if (actionID == null || actionID.isEmpty()) {
            logger.warn("Cannot add status listener: actionID is null or empty for participant '{}'",
                    participant.getUDN());
            return;
        }
        if (interval < 0) {
            logger.warn("Cannot add status listener: interval must be non-negative (was: {}) for participant '{}'",
                    interval, participant.getUDN());
            return;
        }
        registerParticipant(participant);

        int pollingInterval = interval == 0 ? DEFAULT_POLLING_INTERVAL : interval;

        // remove the previous polling job, if any
        stopPollingForParticipant(participant);

        currentStates.put(participant, true);

        Runnable pollingRunnable = new UPNPPollingRunnable(participant, serviceID, actionID);
        pollingJobs.put(participant,
                scheduler.scheduleWithFixedDelay(pollingRunnable, 0, pollingInterval, TimeUnit.SECONDS));
    }

    private void stopPollingForParticipant(UpnpIOParticipant participant) {
        if (pollingJobs.containsKey(participant)) {
            ScheduledFuture<?> pollingJob = pollingJobs.get(participant);
            if (pollingJob != null) {
                pollingJob.cancel(true);
            }
        }
    }

    /**
     * Stops the polling mechanism for checking the status of a specific UDN device.
     *
     * @param participant the participant to remove polling for, must not be null
     */
    @Override
    public void removeStatusListener(UpnpIOParticipant participant) {
        if (participant == null) {
            logger.warn("Cannot remove status listener: participant is null");
            return;
        }
        unregisterParticipant(participant);
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        informParticipants(device, true);

        for (RemoteDevice childDevice : device.getEmbeddedDevices()) {
            informParticipants(childDevice, true);
        }
    }

    @Override
    public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
    }

    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
        informParticipants(device, false);

        for (RemoteDevice childDevice : device.getEmbeddedDevices()) {
            informParticipants(childDevice, false);
        }
    }

    @Override
    public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
    }

    @Override
    public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
    }

    @Override
    public void localDeviceAdded(Registry registry, LocalDevice device) {
    }

    @Override
    public void localDeviceRemoved(Registry registry, LocalDevice device) {
    }

    @Override
    public void beforeShutdown(Registry registry) {
    }

    @Override
    public void afterShutdown() {
    }
}
