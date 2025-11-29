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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;

/**
 * Tests {@link AudioMapper}.
 *
 * @author Nuraiman Danial - Initial contribution
 */
@NonNullByDefault
class AudioMapperTest {

    @Test
    void testMapAudioSource() {
        AudioSource source = mock(AudioSource.class);
        when(source.getId()).thenReturn("audioSource-1");
        when(source.getLabel(Locale.ENGLISH)).thenReturn("Microphone");

        AudioSourceDTO dto = AudioMapper.map(source, Locale.ENGLISH);

        assertEquals("audioSource-1", dto.getId());
        assertEquals("Microphone", dto.getLabel());
    }

    @Test
    void testMapAudioSink() {
        AudioSink sink = mock(AudioSink.class);
        when(sink.getId()).thenReturn("audioSink-1");
        when(sink.getLabel(Locale.ENGLISH)).thenReturn("Speaker");

        AudioSinkDTO dto = AudioMapper.map(sink, Locale.ENGLISH);

        assertEquals("audioSink-1", dto.getId());
        assertEquals("Speaker", dto.getLabel());
    }
}
