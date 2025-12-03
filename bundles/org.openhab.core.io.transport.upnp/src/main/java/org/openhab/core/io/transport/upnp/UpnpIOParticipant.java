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

/**
 * The {@link UpnpIOParticipant} is an interface that needs to
 * be implemented by classes that want to participate in
 * UPnP communication.
 * Participants receive notifications about device status changes, service subscriptions, and state variable updates.
 *
 * @author Karel Goderis - Initial contribution
 */
public interface UpnpIOParticipant {

    /**
     * Get the UDN (Unique Device Name) of the participant.
     *
     * @return the UDN string that uniquely identifies the UPnP device, must not be null
     */
    String getUDN();

    /**
     * Called when the UPnP IO service receives a {variable,value} tuple for the given UPnP service.
     *
     * @param variable the state variable name, must not be null
     * @param value the state variable value, must not be null
     * @param service the UPnP service ID that provided the value, must not be null
     */
    void onValueReceived(String variable, String value, String service);

    /**
     * Called to notify if a GENA (Generic Event Notification Architecture) subscription succeeded or failed.
     *
     * @param service the UPnP service that was subscribed to, must not be null
     * @param succeeded true if the subscription succeeded; false if failed
     */
    void onServiceSubscribed(String service, boolean succeeded);

    /**
     * Called when the UPnP IO service detects a change in the reachability status of the participant's device,
     * given that an addStatusListener is registered.
     *
     * @param status false if the device is no longer reachable (poll fails when previously successful);
     *            true if the device becomes reachable (poll succeeds when previously failing)
     */
    void onStatusChanged(boolean status);
}
