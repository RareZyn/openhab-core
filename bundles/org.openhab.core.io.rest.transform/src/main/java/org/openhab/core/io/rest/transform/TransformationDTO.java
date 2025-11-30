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
package org.openhab.core.io.rest.transform;

import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.transform.Transformation;

/**
 * The {@link TransformationDTO} wraps a {@link Transformation}
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class TransformationDTO {
    private String uid;
    private String label;
    private String type;
    private Map<String, String> configuration;
    private boolean editable = false;

    /**
     * Build DTO from domain Transformation object.
     *
     * @param transformation the transformation to wrap
     */
    public TransformationDTO(Transformation transformation) {
        this.uid = transformation.getUID();
        this.label = transformation.getLabel();
        this.type = transformation.getType();
        this.configuration = transformation.getConfiguration();
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, label, type, configuration, editable);
    }
}
