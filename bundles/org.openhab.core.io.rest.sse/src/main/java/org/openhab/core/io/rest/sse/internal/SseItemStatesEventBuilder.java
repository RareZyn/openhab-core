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
package org.openhab.core.io.rest.sse.internal;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Unit;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.OutboundSseEvent.Builder;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.io.rest.LocaleService;
import org.openhab.core.io.rest.sse.SseResource;
import org.openhab.core.io.rest.sse.internal.dto.StateDTO;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.service.StartLevelService;
import org.openhab.core.transform.TransformationHelper;
import org.openhab.core.transform.TransformationService;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.UnDefType;
import org.openhab.core.types.util.UnitUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SseItemStatesEventBuilder} builds {@link OutboundSseEvent} containing item state information. This
 * class is used by {@link SseResource} to push structured item state payloads over SSE.
 *
 * @author Yannick Schaus - Initial contribution
 * @author Wouter Born - Rework SSE item state sinks for dropping Glassfish
 */
@Component(service = SseItemStatesEventBuilder.class)
@NonNullByDefault
public class SseItemStatesEventBuilder {

    private static final Pattern EXTRACT_TRANSFORM_FUNCTION_PATTERN = Pattern.compile("(.*?)\\((.*)\\):(.*)");

    private final Logger logger = LoggerFactory.getLogger(SseItemStatesEventBuilder.class);

    private final ItemRegistry itemRegistry;
    private final LocaleService localeService;
    private final TimeZoneProvider timeZoneProvider;
    private final StartLevelService startLevelService;

    @Activate
    public SseItemStatesEventBuilder(final @Reference ItemRegistry itemRegistry,
            final @Reference LocaleService localeService, final @Reference TimeZoneProvider timeZoneProvider,
            final @Reference StartLevelService startLevelService) {

        this.itemRegistry = itemRegistry;
        this.localeService = localeService;
        this.timeZoneProvider = timeZoneProvider;
        this.startLevelService = startLevelService;
    }

    /**
     * Builds an SSE event containing the states of multiple items.
     *
     * @param eventBuilder an SSE builder instance
     * @param itemNames the set of item names to include
     * @return an SSE event or null if nothing could be built
     */
    public @Nullable OutboundSseEvent buildEvent(Builder eventBuilder, Set<String> itemNames) {
        Map<String, StateDTO> payload = new HashMap<>(itemNames.size());
        Locale locale = localeService.getLocale(null);

        for (String itemName : itemNames) {
            StateDTO stateDTO = buildStateDTO(itemName, locale);
            if (stateDTO != null) {
                payload.put(itemName, stateDTO);
            }
        }

        if (!payload.isEmpty()) {
            return eventBuilder.mediaType(MediaType.APPLICATION_JSON_TYPE).data(payload).build();
        }

        return null;
    }

    /**
     * Builds a {@link StateDTO} for a specific item.
     */
    private @Nullable StateDTO buildStateDTO(String itemName, Locale locale) {
        try {
            Item item = itemRegistry.getItem(itemName);
            State state = item.getState();

            StateDTO dto = new StateDTO();
            dto.state = state.toString();
            dto.type = getStateType(state);

            String displayState = getDisplayState(item, locale);

            if (displayState != null && !displayState.equals(dto.state)) {
                dto.displayState = displayState;
            }
            if (state instanceof DecimalType decimal) {
                dto.numericState = decimal.floatValue();
            }
            if (state instanceof QuantityType<?> quatity) {
                dto.numericState = quatity.floatValue();
                dto.unit = quatity.getUnit().toString();
            }

            return dto;
        } catch (ItemNotFoundException e) {
            if (startLevelService.getStartLevel() >= StartLevelService.STARTLEVEL_MODEL) {
                logger.warn("Attempting to send a state update for non-existing item: {}", itemName, e);
            }
            return null;
        }
    }

    /**
     * Computes a user-friendly display state for an item by applying pattern
     * options, transformations, or formatting rules.
     *
     * @return human-friendly state or raw state string if none applies
     */
    protected @Nullable String getDisplayState(Item item, Locale locale) {
        State state = item.getState();
        StateDescription stateDescription = item.getStateDescription(locale);
        String displayState = state.toString();

        if (stateDescription == null) {
            return displayState;
        }

        String pattern = stateDescription.getPattern();

        // Case 1: Transformation pattern
        Matcher matcher = pattern != null ? EXTRACT_TRANSFORM_FUNCTION_PATTERN.matcher(pattern) : null;

        if (matcher != null && matcher.find()) {
            return applyTransformationPattern(item, state, matcher, displayState);
        }

        // Case 2: NULL or UNDEF always fall back to raw transformation
        if (state instanceof UnDefType) {
            return displayState;
        }

        // Case 3: Matching item options
        Optional<String> optionMatch = matchStateOption(stateDescription, state, pattern, item.getName());
        if (optionMatch.isPresent()) {
            return optionMatch.get();
        }

        // Case 4: Formatting
        if (pattern != null) {
            return applyFormattingPattern(state, item, pattern, displayState);
        }

        return displayState;
    }

    private String applyTransformationPattern(Item item, State state, Matcher matcher, String fallback) {
        String type = matcher.group(1);
        String function = matcher.group(2);
        String rawFormat = matcher.group(3);

        TransformationService service = TransformationHelper.getTransformationService(type);
        if (service == null) {
            logger.warn("Transformation service '{}' unavailable for item '{}'", type, item.getName());
            return fallback;
        }

        String format = (state instanceof UnDefType) ? "%s" : rawFormat;

        try {
            String result = service.transform(function, state.format(format));
            return (result != null) ? result : fallback;
        } catch (Exception e) {
            logger.warn("Transformation failed for item '{}': '{}'", item.getName(), e.getMessage(), e);
            return fallback;
        }
    }

    private Optional<String> matchStateOption(StateDescription description, State state, @Nullable String pattern,
            String itemName) {
        return description.getOptions().stream().filter(option -> option.getValue().equals(state.toString()))
                .map(option -> {
                    String label = option.getLabel();
                    if (label == null) {
                        return state.toString();
                    }
                    if (pattern == null) {
                        return label;
                    }

                    try {
                        return String.format(pattern, label);
                    } catch (IllegalFormatException e) {
                        logger.debug("Bad format pattern '{}' for item {}: {}", pattern, itemName, e.getMessage(), e);
                        return label;
                    }
                }).findFirst();
    }

    private String applyFormattingPattern(State state, Item item, String pattern, String fallback) {
        if (state instanceof QuantityType<?> quantity) {
            Unit<?> patternUnit = UnitUtils.parseUnit(pattern);
            if (patternUnit != null && !quantity.getUnit().equals(patternUnit)) {
                try {
                    QuantityType<?> converted = quantity.toInvertibleUnit(patternUnit);
                    if (converted != null) {
                        state = converted;
                    }
                } catch (Exception e) {
                    //
                }
            }
        }

        try {
            if (state instanceof DateTimeType dateTimeType) {
                return dateTimeType.format(pattern, timeZoneProvider.getTimeZone());
            }
            return state.format(pattern);
        } catch (IllegalArgumentException e) {
            logger.debug("Formatting failed for item {} pattern '{}': {}", item.getName(), pattern, e.getMessage(), e);
            return fallback;
        }
    }

    /**
     * Extracts the base type name from a State class name.
     */
    // Taken from org.openhab.core.items.events.ItemEventFactory
    private static String getStateType(State state) {
        String stateClassName = state.getClass().getSimpleName();
        return stateClassName.endsWith("Type") ? stateClassName.substring(0, stateClassName.length() - 4)
                : stateClassName;
    }
}
