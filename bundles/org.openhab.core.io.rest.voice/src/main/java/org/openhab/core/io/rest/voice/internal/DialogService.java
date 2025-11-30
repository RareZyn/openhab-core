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
package org.openhab.core.io.rest.voice.internal;

import java.util.List;
import java.util.Locale;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.io.rest.JSONResponse;
import org.openhab.core.io.rest.LocaleService;
import org.openhab.core.io.rest.voice.internal.dto.DialogParams;
import org.openhab.core.io.rest.voice.internal.dto.DialogStartParams;
import org.openhab.core.io.rest.voice.internal.dto.ListenAndAnswerParams;
import org.openhab.core.voice.*;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.slf4j.Logger;

/**
 *
 *
 * @author Nuraiman Danial - Initial contribution
 */
@NonNullByDefault
public final class DialogService {
    private DialogService() {
    }

    public static Response startDialog(VoiceManager voiceManager, AudioManager audioManager,
            LocaleService localeService, DialogStartParams params, Logger logger) {

        Locale locale = localeService.getLocale(params.language);

        var builder = voiceManager.getDialogContextBuilder().withLocale(locale);
        applyCommonParams(builder, voiceManager, audioManager, params);

        // Keyword Spotter (optional for continuous dialog)
        if (params.ksId != null) {
            KSService ks = voiceManager.getKS(params.ksId);
            if (ks == null) {
                return error(Response.Status.NOT_FOUND, "Keyword spotter not found: " + params.ksId);
            }
            builder.withKS(ks);
        }

        if (params.keyword != null) {
            builder.withKeyword(params.keyword);
        }

        try {
            voiceManager.startDialog(builder.build());
            return Response.ok().build();
        } catch (IllegalStateException e) {
            logger.error("Failed to start dialog: {}", e.getMessage(), e);
            return error(Status.BAD_REQUEST, e.getMessage());
        }
    }

    public static Response listenAndAnswer(VoiceManager voiceManager, AudioManager audioManager,
            LocaleService localeService, ListenAndAnswerParams params, Logger logger) {

        Locale locale = localeService.getLocale(params.language);

        var builder = voiceManager.getDialogContextBuilder().withLocale(locale);
        applyCommonParams(builder, voiceManager, audioManager, params);

        try {
            voiceManager.listenAndAnswer(builder.build());
            return Response.ok().build();
        } catch (IllegalStateException e) {
            logger.error("Failed to execute listen and answer: {}", e.getMessage(), e);
            return error(Status.BAD_REQUEST, e.getMessage());
        }
    }

    public static Response stopDialog(AudioManager audioManager, VoiceManager voiceManager, @Nullable String sourceId,
            Logger logger) {

        AudioSource source = sourceId != null ? audioManager.getSource(sourceId) : null;

        if (sourceId != null && source == null) {
            return error(Status.NOT_FOUND, "Audio source not found: " + sourceId);
        }

        try {
            voiceManager.stopDialog(source);
            return Response.ok().build();
        } catch (IllegalStateException e) {
            logger.error("Error while stopping dialog: {}", sourceId, e);
            return error(Status.BAD_REQUEST, e.getMessage());
        }
    }

    private static void applyCommonParams(DialogContext.Builder builder, VoiceManager voiceManager,
            AudioManager audioManager, DialogParams params) {

        // Audio Source
        if (params.sourceId != null) {
            AudioSource source = audioManager.getSource(params.sourceId);
            if (source == null) {
                throw new IllegalArgumentException("Audio source not found: " + params.sourceId);
            }
            builder.withSource(source);
        }

        // STT
        if (params.sttId != null) {
            STTService stt = voiceManager.getSTT(params.sttId);
            if (stt == null) {
                throw new IllegalArgumentException("STT service not found: " + params.sttId);
            }
            builder.withSTT(stt);
        }

        // TTS
        if (params.ttsId != null) {
            TTSService tts = voiceManager.getTTS(params.ttsId);
            if (tts == null) {
                throw new IllegalArgumentException("TTS service not found: " + params.ttsId);
            }
            builder.withTTS(tts);
        }

        // Voice
        if (params.voiceId != null) {
            Voice voice = voiceManager.getAllVoices().stream().filter(v -> v.getUID().equals(params.voiceId))
                    .findFirst().orElse(null);
            if (voice == null) {
                throw new IllegalArgumentException("Voice not found: " + params.voiceId);
            }
            builder.withVoice(voice);
        }

        // Human Language Interpreters
        if (params.hliIds != null && !params.hliIds.isBlank()) {
            List<String> ids = List.of(params.hliIds.split("\\s*,\\s*"));
            List<HumanLanguageInterpreter> hlis = voiceManager.getHLIsByIds(ids);
            if (hlis.isEmpty()) {
                throw new IllegalArgumentException("No HLI found for IDs: " + params.hliIds);
            }
            builder.withHLIs(hlis);
        }

        // Audio Sink
        if (params.sinkId != null) {
            AudioSink sink = audioManager.getSink(params.sinkId);
            if (sink == null) {
                throw new IllegalArgumentException("Audio sink not found: " + params.sinkId);
            }
            builder.withSink(sink);
        }

        // Listening Item
        if (params.listeningItem != null) {
            builder.withListeningItem(params.listeningItem);
        }
    }

    private static Response error(Status status, @Nullable String message) {
        return JSONResponse.createErrorResponse(status, message);
    }
}
