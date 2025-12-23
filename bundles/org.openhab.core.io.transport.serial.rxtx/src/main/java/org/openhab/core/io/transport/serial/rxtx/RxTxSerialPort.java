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
package org.openhab.core.io.transport.serial.rxtx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.io.transport.serial.internal.SerialPortEventImpl;

import gnu.io.SerialPortEvent;

/**
 * Specific serial port implementation.
 *
 * @author Markus Rathgeb - Initial contribution
 * @author Vita Tucek - added further methods
 */
@NonNullByDefault
public class RxTxSerialPort implements SerialPort {

    private final gnu.io.SerialPort sp;

    /**
     * Constructs a new RxTxSerialPort wrapper around an RXTX serial port.
     *
     * @param sp the underlying serial port implementation, must not be null
     * @throws IllegalArgumentException if sp is null
     */
    public RxTxSerialPort(final gnu.io.SerialPort sp) {
        if (sp == null) {
            throw new IllegalArgumentException("SerialPort cannot be null");
        }
        this.sp = sp;
    }

    @Override
    public void close() {
        sp.close();
    }

    /**
     * Sets the serial port parameters.
     *
     * @param baudrate the baud rate in bits per second
     * @param dataBits the number of data bits (5, 6, 7, or 8)
     * @param stopBits the number of stop bits (1, 2, or 3 for 1.5)
     * @param parity the parity setting (0=NONE, 1=ODD, 2=EVEN, 3=MARK, 4=SPACE)
     * @throws UnsupportedCommOperationException if the operation is not supported
     */
    @Override
    public void setSerialPortParams(int baudrate, int dataBits, int stopBits, int parity)
            throws UnsupportedCommOperationException {
        try {
            sp.setSerialPortParams(baudrate, dataBits, stopBits, parity);
        } catch (gnu.io.UnsupportedCommOperationException ex) {
            throw new UnsupportedCommOperationException(
                    String.format("Unsupported serial port parameters: baudrate=%d, dataBits=%d, stopBits=%d, parity=%d",
                            baudrate, dataBits, stopBits, parity),
                    ex);
        }
    }

    @Override
    public @Nullable InputStream getInputStream() throws IOException {
        return sp.getInputStream();
    }

    @Override
    public @Nullable OutputStream getOutputStream() throws IOException {
        return sp.getOutputStream();
    }

    @Override
    public void addEventListener(SerialPortEventListener listener) throws TooManyListenersException {
        sp.addEventListener(new gnu.io.SerialPortEventListener() {
            @Override
            public void serialEvent(final @Nullable SerialPortEvent event) {
                if (event == null) {
                    return;
                }
                listener.serialEvent(new SerialPortEventImpl(event));
            }
        });
    }

    @Override
    public void removeEventListener() {
        sp.removeEventListener();
    }

    @Override
    public void notifyOnDataAvailable(boolean enable) {
        sp.notifyOnDataAvailable(enable);
    }

    @Override
    public void notifyOnBreakInterrupt(boolean enable) {
        sp.notifyOnBreakInterrupt(enable);
    }

    @Override
    public void notifyOnFramingError(boolean enable) {
        sp.notifyOnFramingError(enable);
    }

    @Override
    public void notifyOnOverrunError(boolean enable) {
        sp.notifyOnOverrunError(enable);
    }

    @Override
    public void notifyOnParityError(boolean enable) {
        sp.notifyOnParityError(enable);
    }

    @Override
    public void setRTS(boolean enable) {
        sp.setRTS(enable);
    }

    /**
     * Enables the receive timeout with the specified timeout value.
     *
     * @param timeout the timeout in milliseconds, must be non-negative
     * @throws IllegalArgumentException if timeout is negative
     * @throws UnsupportedCommOperationException if the operation is not supported
     */
    @Override
    public void enableReceiveTimeout(int timeout) throws UnsupportedCommOperationException {
        if (timeout < 0) {
            throw new IllegalArgumentException(String.format("timeout must be non negative (is: %d)", timeout));
        }
        try {
            sp.enableReceiveTimeout(timeout);
        } catch (gnu.io.UnsupportedCommOperationException ex) {
            throw new UnsupportedCommOperationException(
                    String.format("Unsupported receive timeout operation: timeout=%d", timeout), ex);
        }
    }

    @Override
    public void disableReceiveTimeout() {
        sp.disableReceiveTimeout();
    }

    @Override
    public String getName() {
        return sp.getName();
    }

    /**
     * Sets the flow control mode for the serial port.
     *
     * @param flowcontrolRtsctsOut the flow control mode (combination of FLOWCONTROL_* constants)
     * @throws UnsupportedCommOperationException if the operation is not supported
     */
    @Override
    public void setFlowControlMode(int flowcontrolRtsctsOut) throws UnsupportedCommOperationException {
        try {
            sp.setFlowControlMode(flowcontrolRtsctsOut);
        } catch (gnu.io.UnsupportedCommOperationException e) {
            throw new UnsupportedCommOperationException(
                    String.format("Unsupported flow control mode: %d", flowcontrolRtsctsOut), e);
        }
    }

    /**
     * Enables the receive threshold with the specified threshold value.
     *
     * @param threshold the threshold value in bytes
     * @throws UnsupportedCommOperationException if the operation is not supported
     */
    @Override
    public void enableReceiveThreshold(int threshold) throws UnsupportedCommOperationException {
        try {
            sp.enableReceiveThreshold(threshold);
        } catch (gnu.io.UnsupportedCommOperationException e) {
            throw new UnsupportedCommOperationException(
                    String.format("Unsupported receive threshold operation: threshold=%d", threshold), e);
        }
    }

    @Override
    public int getBaudRate() {
        return sp.getBaudRate();
    }

    @Override
    public int getDataBits() {
        return sp.getDataBits();
    }

    @Override
    public int getStopBits() {
        return sp.getStopBits();
    }

    @Override
    public int getParity() {
        return sp.getParity();
    }

    @Override
    public void notifyOnOutputEmpty(boolean enable) {
        sp.notifyOnOutputEmpty(enable);
    }

    @Override
    public void notifyOnCTS(boolean enable) {
        sp.notifyOnCTS(enable);
    }

    @Override
    public void notifyOnDSR(boolean enable) {
        sp.notifyOnDSR(enable);
    }

    @Override
    public void notifyOnRingIndicator(boolean enable) {
        sp.notifyOnRingIndicator(enable);
    }

    @Override
    public void notifyOnCarrierDetect(boolean enable) {
        sp.notifyOnCarrierDetect(enable);
    }

    @Override
    public int getFlowControlMode() {
        return sp.getFlowControlMode();
    }

    @Override
    public boolean isRTS() {
        return sp.isRTS();
    }

    @Override
    public void setDTR(boolean state) {
        sp.setDTR(state);
    }

    @Override
    public boolean isDTR() {
        return sp.isDTR();
    }

    @Override
    public boolean isCTS() {
        return sp.isCTS();
    }

    @Override
    public boolean isDSR() {
        return sp.isDSR();
    }

    @Override
    public boolean isCD() {
        return sp.isCD();
    }

    @Override
    public boolean isRI() {
        return sp.isRI();
    }

    @Override
    public void sendBreak(int duration) {
        sp.sendBreak(duration);
    }
}
