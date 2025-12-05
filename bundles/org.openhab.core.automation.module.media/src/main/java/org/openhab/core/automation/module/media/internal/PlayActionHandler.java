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
package org.openhab.core.automation.module.media.internal;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.handler.BaseActionModuleHandler;
import org.openhab.core.library.types.PercentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a ModuleHandler implementation for Actions that play a sound file from the file system.
 *
 * <p>
 * This handler executes the "media.PlayAction" automation action type, which plays audio files from the configured
 * sounds directory through the openHAB audio system.
 * </p>
 *
 * <h3>Configuration Parameters:</h3>
 * <ul>
 * <li><b>sound</b> (required): The filename of the sound to play (e.g., "doorbell.mp3")</li>
 * <li><b>sink</b> (optional): The audio sink ID to use for playback. If null, uses the default sink.</li>
 * <li><b>volume</b> (optional): Playback volume as a percentage (0-100). If null, uses current sink volume.</li>
 * </ul>
 *
 * <h3>Behavior:</h3>
 * <ul>
 * <li>Validates that the 'sound' parameter is provided during construction</li>
 * <li>Logs errors if playback fails but does not throw exceptions</li>
 * <li>Returns null as this action does not produce output values</li>
 * </ul>
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Christoph Weitkamp - Added parameter volume
 */
@NonNullByDefault
public class PlayActionHandler extends BaseActionModuleHandler {

    public static final String TYPE_ID = "media.PlayAction";
    public static final String PARAM_SOUND = "sound";
    public static final String PARAM_SINK = "sink";
    public static final String PARAM_VOLUME = "volume";

    private final Logger logger = LoggerFactory.getLogger(PlayActionHandler.class);

    private final AudioManager audioManager;

    private final String sound;
    private final @Nullable String sink;
    private final @Nullable PercentType volume;

    public PlayActionHandler(Action module, AudioManager audioManager) {
        super(module);
        this.audioManager = audioManager;

        // Validate required configuration parameter
        Object soundParam = module.getConfiguration().get(PARAM_SOUND);
        if (soundParam == null) {
            throw new IllegalArgumentException(
                    "Configuration parameter '" + PARAM_SOUND + "' is required for PlayAction but was not provided");
        }
        this.sound = soundParam.toString();

        Object sinkParam = module.getConfiguration().get(PARAM_SINK);
        this.sink = sinkParam != null ? sinkParam.toString() : null;

        Object volumeParam = module.getConfiguration().get(PARAM_VOLUME);
        this.volume = volumeParam instanceof BigDecimal bd ? new PercentType(bd) : null;
    }

    @Override
    public @Nullable Map<String, @Nullable Object> execute(Map<String, Object> context) {
        try {
            audioManager.playFile(sound, sink, volume);
        } catch (AudioException e) {
            logger.error("Error playing sound '{}': {}", sound, e.getMessage());
        }
        return null;
    }
}
