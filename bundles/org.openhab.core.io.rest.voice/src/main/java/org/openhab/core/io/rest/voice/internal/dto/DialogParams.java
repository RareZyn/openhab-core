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
package org.openhab.core.io.rest.voice.internal.dto;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 *
 *
 * @author Nuraiman Danial - Initial contribution
 */
@NonNullByDefault
public class DialogParams {

    @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE)
    @Nullable
    public String language;

    @QueryParam("sourceId")
    @Nullable
    public String sourceId;

    @QueryParam("sinkId")
    @Nullable
    public String sinkId;

    @QueryParam("sttId")
    @Nullable
    public String sttId;

    @QueryParam("ttsId")
    @Nullable
    public String ttsId;

    @QueryParam("voiceId")
    @Nullable
    public String voiceId;

    @QueryParam("hliIds")
    @Nullable
    public String hliIds; // comma-separated

    @QueryParam("listeningItem")
    @Nullable
    public String listeningItem;
}
