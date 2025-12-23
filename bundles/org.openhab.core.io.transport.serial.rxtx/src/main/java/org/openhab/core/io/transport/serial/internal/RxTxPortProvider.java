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
package org.openhab.core.io.transport.serial.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.ProtocolType;
import org.openhab.core.io.transport.serial.ProtocolType.PathType;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortProvider;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;

/**
 * Serial port provider implementation using the RXTX library (gnu.io).
 * This provider supports local serial ports and uses the "rxtx" protocol scheme.
 * Includes workarounds for symbolic link resolution and system property handling.
 *
 * @author Matthias Steigenberger - Initial contribution
 * @author Wouter Born - Fix serial ports missing when ports are added to system property
 * @author Gwendal Roulleau - Workaround for long path issue by resolving symlink
 */
@NonNullByDefault
@Component(service = SerialPortProvider.class)
public class RxTxPortProvider implements SerialPortProvider {

    private final Logger logger = LoggerFactory.getLogger(RxTxPortProvider.class);

    /**
     * Gets a serial port identifier for the specified port URI.
     *
     * @param port the port URI, must not be null
     * @return the serial port identifier, or null if the port does not exist
     */
    @Override
    public @Nullable SerialPortIdentifier getPortIdentifier(URI port) {
        if (port == null) {
            logger.warn("Port URI is null, cannot get port identifier");
            return null;
        }
        String portPathAsString = port.getPath();
        if (portPathAsString == null || portPathAsString.isEmpty()) {
            logger.warn("Port URI path is null or empty: {}", port);
            return null;
        }
        try {
            // Resolving symbolic link is needed because of a bug with nrjavaserial
            // Until a new release with pull request #230 is included in openHAB,
            // we keep resolving symbolic link here
            Path portPath = Path.of(portPathAsString);
            if (Files.isSymbolicLink(portPath)) {
                portPathAsString = portPath.toRealPath().toString();
            }
            CommPortIdentifier ident = SerialPortUtil.getPortIdentifier(portPathAsString);
            return new SerialPortIdentifierImpl(ident);
        } catch (NoSuchPortException | IOException e) {
            logger.debug("No SerialPortIdentifier found for: {}", portPathAsString, e);
            return null;
        } catch (Exception e) {
            logger.warn("Error getting port identifier for: {}", portPathAsString, e);
            return null;
        }
    }

    /**
     * Gets the protocol types that this provider accepts.
     *
     * @return a stream containing the "rxtx" protocol type for local ports
     */

    @Override
    public Stream<ProtocolType> getAcceptedProtocols() {
        return Stream.of(new ProtocolType(PathType.LOCAL, "rxtx"));
    }

    /**
     * Gets all available serial port identifiers from the RXTX library.
     * Combines ports discovered by scanning and ports from system properties.
     *
     * @return a stream of serial port identifiers for all available serial ports
     */
    @Override
    public Stream<SerialPortIdentifier> getSerialPortIdentifiers() {
        Stream<CommPortIdentifier> scanIds = SerialPortUtil.getPortIdentifiersUsingScan();
        Stream<CommPortIdentifier> propIds = SerialPortUtil.getPortIdentifiersUsingProperty();

        return Stream.concat(scanIds, propIds).filter(distinctByKey(CommPortIdentifier::getName))
                .filter(id -> id.getPortType() == CommPortIdentifier.PORT_SERIAL)
                .map(sid -> new SerialPortIdentifierImpl(sid));
    }

    @SuppressWarnings("null")
    private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, String> seen = new ConcurrentHashMap<>();
        return t -> seen.put(keyExtractor.apply(t), "") == null;
    }
}
