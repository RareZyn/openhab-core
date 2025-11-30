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

import javax.ws.rs.core.Response.Status;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.voice.VoiceManager;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.core.voice.text.InterpretationException;
import org.slf4j.Logger;

/**
 *
 *
 * @author Nuraiman Danial - Initial contribution
 */
@NonNullByDefault
public final class InterpretationService {

    private InterpretationService() {
    }

    public record InterpretationResult(boolean success, String answer, Status status, String errorMessage) {
        public static InterpretationResult ok(String answer) {
            return new InterpretationResult(true, answer, Status.OK, "");
        }

        public static InterpretationResult error(Status status, String msg) {
            return new InterpretationResult(false, "", status, msg);
        }
    }

    public static InterpretationResult interpret(VoiceManager voiceManager, List<String> ids, Locale locale,
            String text, Logger logger) {
        List<HumanLanguageInterpreter> hlis;

        if (ids.isEmpty()) {
            HumanLanguageInterpreter hli = voiceManager.getHLI();
            if (hli == null)
                return InterpretationResult.error(Status.NOT_FOUND, "No default interpreter available");
            hlis = List.of(hli);
        } else {
            hlis = voiceManager.getHLIsByIds(ids);
            if (hlis.isEmpty())
                return InterpretationResult.error(Status.NOT_FOUND, "No interpreter found for IDs: " + ids);
        }

        for (HumanLanguageInterpreter hli : hlis) {
            try {
                String result = hli.interpret(locale, text);
                logger.debug("HLI '{}' successfully interpreted: {}", hli.getId(), result);
                return InterpretationResult.ok(result);
            } catch (InterpretationException e) {
                logger.error("HLI '{}' failed: {}", hli.getId(), e.getMessage(), e);
            }
        }

        return InterpretationResult.error(Status.BAD_REQUEST, "All interpreters failed to process the command");
    }
}
