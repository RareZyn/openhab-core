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
package org.openhab.core.io.transport.upnp;

import java.net.URL;
import java.util.Map;

/**
 * The {@link UpnpIOService} is an interface that describes the
 * UPnP IO Service for interacting with UPnP devices.
 * Provides methods for invoking actions, managing subscriptions, and polling device status.
 *
 * @author Karel Goderis - Initial contribution
 * @author Kai Kreuzer - added descriptor url retrieval
 */
public interface UpnpIOService {

    /**
     * Invoke a UPnP Action using the device default namespace and serviceID.
     *
     * @param participant the participant to invoke the action for, must not be null
     * @param serviceID the UPnP service to invoke the action upon, must not be null
     * @param actionID the Action to invoke, must not be null
     * @param inputs a map of {variable,values} to parameterize the Action that will be invoked, may be null
     * @return a map of output variable names to their values, or an empty map if the action fails
     */
    Map<String, String> invokeAction(UpnpIOParticipant participant, String serviceID, String actionID,
            Map<String, String> inputs);

    /**
     * Invoke a UPnP Action using the specified namespace and serviceID.
     *
     * @param participant the participant to invoke the action for, must not be null
     * @param namespace the namespace of the service to invoke the action upon, may be null to use device default
     * @param serviceID the UPnP service to invoke the action upon, must not be null
     * @param actionID the Action to invoke, must not be null
     * @param inputs a map of {variable,values} to parameterize the Action that will be invoked, may be null
     * @return a map of output variable names to their values, or an empty map if the action fails
     */
    Map<String, String> invokeAction(UpnpIOParticipant participant, String namespace, String serviceID, String actionID,
            Map<String, String> inputs);

    /**
     * Subscribe to a GENA (Generic Event Notification Architecture) subscription.
     *
     * @param participant the participant the subscription is for, must not be null
     * @param serviceID the UPnP service to subscribe to, must not be null
     * @param duration the duration of the subscription in seconds, must be positive
     */
    void addSubscription(UpnpIOParticipant participant, String serviceID, int duration);

    /**
     * Unsubscribe from a GENA (Generic Event Notification Architecture) subscription.
     *
     * @param participant the participant of the subscription, must not be null
     * @param serviceID the UPnP service to unsubscribe from, must not be null
     */
    void removeSubscription(UpnpIOParticipant participant, String serviceID);

    /**
     * Verify if a participant is registered with the UPnP IO Service.
     *
     * @param participant the participant whose registration status to verify, must not be null
     * @return true if the participant is registered with the UpnpIOService, false otherwise
     */
    boolean isRegistered(UpnpIOParticipant participant);

    /**
     * Register a participant with the UPnP IO Service.
     *
     * @param participant the participant to register, must not be null
     */
    void registerParticipant(UpnpIOParticipant participant);

    /**
     * Unregister a participant from the UPnP IO Service.
     *
     * @param participant the participant to unregister, must not be null
     */
    void unregisterParticipant(UpnpIOParticipant participant);

    /**
     * Retrieves the descriptor URL for the participant's UPnP device.
     *
     * @param participant the participant whose descriptor URL is requested, must not be null
     * @return the URL of the device descriptor as provided by the UPnP device, or null if not available
     */
    URL getDescriptorURL(UpnpIOParticipant participant);

    /**
     * Establish a polling mechanism to check the status of a specific UDN device.
     * The polling mechanism works by invoking the actionID on serviceID every interval.
     * It is assumed that the actionID does not take/have to take any {variable,value} input set.
     *
     * @param participant the participant for whom to set up polling, must not be null
     * @param serviceID the service to use for polling, must not be null
     * @param actionID the action to call, must not be null
     * @param interval the polling interval in seconds (0 uses default interval), must be non-negative
     */
    void addStatusListener(UpnpIOParticipant participant, String serviceID, String actionID, int interval);

    /**
     * Stops the polling mechanism for checking the status of a specific UDN device.
     *
     * @param participant the participant for whom to remove the polling, must not be null
     */
    void removeStatusListener(UpnpIOParticipant participant);
}
