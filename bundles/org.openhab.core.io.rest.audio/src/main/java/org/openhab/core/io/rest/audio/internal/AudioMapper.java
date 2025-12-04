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
package org.openhab.core.io.rest.audio.internal;

import java.util.Locale;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;

/**
 * Mapper class that maps {@link AudioSink} and {@link AudioSource} instanced to their respective DTOs
 * used by REST API responses.
 *
 * @author Laurent Garnier - Initial contribution
 */
@NonNullByDefault
public class AudioMapper {

    private AudioMapper() {
        // Utility class – prevent instantiation
    }

    /**
     * Maps an {@link AudioSource} to an {@link AudioSourceDTO}.
     *
     * @param source the audio source
     * @param locale the locale to use for the DTO
     *
     * @return the corresponding DTO
     */
    public static AudioSourceDTO map(AudioSource source, Locale locale) {
        Objects.requireNonNull(source, "AudioSource must not be null");
        Objects.requireNonNull(locale, "Locale must not be null");
        return new AudioSourceDTO(source.getId(), source.getLabel(locale));
    }

    /**
     * Maps an {@link AudioSink} to an {@link AudioSinkDTO}.
     *
     * @param sink the audio sink
     * @param locale the locale to use for the DTO
     *
     * @return the corresponding DTO
     */
    public static AudioSinkDTO map(AudioSink sink, Locale locale) {
        Objects.requireNonNull(sink, "AudioSink must not be null");
        Objects.requireNonNull(locale, "Locale must not be null");
        return new AudioSinkDTO(sink.getId(), sink.getLabel(locale));
    }
}
