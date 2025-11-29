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

import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.io.rest.LocaleService;

/**
 * Tests {@link AudioResource}.
 *
 * @author Nuraiman Danial - Initial contribution
 */
class AudioResourceTest {

    private AudioManager audioManager;
    private LocaleService localeService;
    private AudioResource resource;

    @BeforeEach
    void setup() {
        audioManager = mock(AudioManager.class);
        localeService = mock(LocaleService.class);

        when(localeService.getLocale(anyString())).thenReturn(Locale.ENGLISH);

        resource = new AudioResource(audioManager, localeService);
    }

    // Sources

    @Test
    void testGetSourcesReturnsList() {
        AudioSource source = mock(AudioSource.class);
        when(source.getId()).thenReturn("audioSource-1");
        when(source.getLabel(Locale.ENGLISH)).thenReturn("Microphone");

        when(audioManager.getAllSources()).thenReturn(Set.of(source));

        Response response = resource.getSources("en");
        assertEquals(200, response.getStatus());

        var list = (List<?>) response.getEntity();
        assertEquals(1, list.size());
    }

    @Test
    void testGetDefaultSourceFound() {
        AudioSource source = mock(AudioSource.class);
        when(source.getId()).thenReturn("audioSource-default");
        when(source.getLabel(Locale.ENGLISH)).thenReturn("Default Microphone");

        when(audioManager.getSource()).thenReturn(source);

        Response response = resource.getDefaultSource("en");
        assertEquals(200, response.getStatus());
    }

    @Test
    void testGetDefaultSourceNotFound() {
        when(audioManager.getSource()).thenReturn(null);

        Response response = resource.getDefaultSource("en");
        assertEquals(404, response.getStatus());
    }

    // Sinks

    @Test
    void testGetSinksReturnsList() {
        AudioSink sink = mock(AudioSink.class);
        when(sink.getId()).thenReturn("audioSink-1");
        when(sink.getLabel(Locale.ENGLISH)).thenReturn("Speaker");

        when(audioManager.getAllSinks()).thenReturn(Set.of(sink));

        Response response = resource.getSinks("en");
        assertEquals(200, response.getStatus());

        var list = (List<?>) response.getEntity();
        assertEquals(1, list.size());
    }

    @Test
    void testGetDefaultSinkFound() {
        AudioSink sink = mock(AudioSink.class);
        when(sink.getId()).thenReturn("audioSink-default");
        when(sink.getLabel(Locale.ENGLISH)).thenReturn("Default Speaker");

        when(audioManager.getSink()).thenReturn(sink);

        Response response = resource.getDefaultSink("en");
        assertEquals(200, response.getStatus());
    }

    @Test
    void testGetDefaultSinkNotFound() {
        when(audioManager.getSink()).thenReturn(null);

        Response response = resource.getDefaultSink("en");
        assertEquals(404, response.getStatus());
    }
}
