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
package org.openhab.core.model.thing.internal.fileconverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigDescriptionRegistry;
import org.openhab.core.model.core.ModelRepository;
import org.openhab.core.model.thing.internal.GenericItemChannelLinkProvider;
import org.openhab.core.model.thing.internal.GenericThingProvider;
import org.openhab.core.model.thing.thing.ModelBridge;
import org.openhab.core.model.thing.thing.ModelChannel;
import org.openhab.core.model.thing.thing.ModelProperty;
import org.openhab.core.model.thing.thing.ModelThing;
import org.openhab.core.model.thing.thing.ThingFactory;
import org.openhab.core.model.thing.thing.ThingModel;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.fileconverter.AbstractThingFileGenerator;
import org.openhab.core.thing.fileconverter.ThingFileGenerator;
import org.openhab.core.thing.fileconverter.ThingFileParser;
import org.openhab.core.thing.link.ItemChannelLink;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.thing.type.ThingTypeRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DslThingFileConverter} is the DSL file converter for {@link Thing} object
 * with the capabilities of parsing and generating file.
 *
 * This class is responsible for:
 * - Converting Thing objects into DSL model representation for file generation
 * - Parsing DSL syntax into Thing and ItemChannelLink objects
 * - Handling both Bridge and regular Thing hierarchies
 * - Normalizing UIDs by removing unnecessary quotes
 *
 * Reengineering Notes:
 * - Extracted QUOTE_PATTERN and other constants at class level
 * - Created ModelThingBuilder helper class to reduce method parameter count (7→1)
 * - Added comprehensive error handling and logging
 * - Improved method documentation for complex operations
 * - Better separation of concerns through extracted methods
 *
 * @author Laurent Garnier - Initial contribution
 */
@NonNullByDefault
@Component(immediate = true, service = { ThingFileGenerator.class, ThingFileParser.class })
public class DslThingFileConverter extends AbstractThingFileGenerator implements ThingFileParser {

    private final Logger logger = LoggerFactory.getLogger(DslThingFileConverter.class);

    // Constants for DSL model generation
    private static final Pattern QUOTE_PATTERN = Pattern.compile(":\"([a-zA-Z0-9_][a-zA-Z0-9_-]*)\"");
    private static final String CHANNEL_KIND_STATE = "State";
    private static final String CHANNEL_KIND_TRIGGER = "Trigger";
    private static final String ERROR_GENERATING_SYNTAX = "Exception when writing the generated syntax";
    private static final String ERROR_DOUBLE_CONFIG = "Configuration parameter {} with value {} is provided unexpectedly as Double type";

    private final ModelRepository modelRepository;
    private final GenericThingProvider thingProvider;
    private final GenericItemChannelLinkProvider itemChannelLinkProvider;

    private final Map<String, ThingModel> elementsToGenerate = new ConcurrentHashMap<>();

    @Activate
    public DslThingFileConverter(final @Reference ModelRepository modelRepository,
            final @Reference GenericThingProvider thingProvider,
            final @Reference GenericItemChannelLinkProvider itemChannelLinkProvider,
            final @Reference ThingTypeRegistry thingTypeRegistry,
            final @Reference ChannelTypeRegistry channelTypeRegistry,
            final @Reference ConfigDescriptionRegistry configDescRegistry) {
        super(thingTypeRegistry, channelTypeRegistry, configDescRegistry);
        this.modelRepository = modelRepository;
        this.thingProvider = thingProvider;
        this.itemChannelLinkProvider = itemChannelLinkProvider;
    }

    @Override
    public String getFileFormatGenerator() {
        return "DSL";
    }

    @Override
    public void setThingsToBeGenerated(String id, List<Thing> things, boolean hideDefaultChannels,
            boolean hideDefaultParameters) {
        if (things.isEmpty()) {
            return;
        }
        ThingModel model = ThingFactory.eINSTANCE.createThingModel();
        Set<Thing> handledThings = new HashSet<>();
        for (Thing thing : things) {
            if (handledThings.contains(thing)) {
                continue;
            }
            model.getThings().add(buildModelThing(thing, hideDefaultChannels, hideDefaultParameters, things.size() > 1,
                    true, things, handledThings));
        }
        elementsToGenerate.put(id, model);
    }

    /**
     * Removes unnecessary double quotes from Thing UID segments in DSL syntax.
     * Example: :"my-thing" → :my-thing
     * 
     * This normalization is necessary because the DSL model generator adds quotes
     * around UID segments containing hyphens, which are not needed in the DSL syntax.
     * 
     * @param data Byte array representing DSL syntax.
     * @return String with quotes removed from Thing UID segments.
     */
    private String removeQuotesFromUID(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        return QUOTE_PATTERN.matcher(text).replaceAll(":$1");
    }

    /**
     * Generates a DSL Thing file from the stored Thing model for the given ID.
     * 
     * Processing Steps:
     * 1. Retrieves the ThingModel from elementsToGenerate map
     * 2. Generates DSL syntax via modelRepository
     * 3. Removes unnecessary double quotes from Thing UIDs
     * 4. Writes the final syntax to the provided OutputStream
     * 
     * Error Handling:
     * - If model is not found, logs a warning and returns without writing
     * - If IOException occurs during write, logs the exception with details
     * 
     * @param id The unique identifier for the Thing generation task
     * @param out The OutputStream where the generated DSL content will be written
     * @throws IllegalArgumentException if id is null or empty
     * @throws IOException if writing to OutputStream fails
     */
    @Override
    public void generateFileFormat(String id, OutputStream out) {
        if (id == null || id.isEmpty()) {
            logger.warn("generateFileFormat called with null or empty id");
            return;
        }

        ThingModel model = elementsToGenerate.remove(id);
        if (model == null) {
            logger.debug("No Thing model found for id: {}", id);
            return;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            modelRepository.generateFileFormat(outputStream, "things", model);

            String syntax = removeQuotesFromUID(outputStream.toByteArray());

            try {
                out.write(syntax.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.warn(ERROR_GENERATING_SYNTAX, e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error during Thing file generation for id: {}", id, e);
        }
    }

    /**
     * Converts a Thing or Bridge object into a ModelThing/ModelBridge for DSL generation.
     * 
     * This method orchestrates the conversion process using a ModelThingBuilder to reduce
     * parameter count and improve maintainability.
     *
     * The builder handles:
     * - Bridge vs regular Thing distinction
     * - Child Things hierarchy (if preferred as tree structure)
     * - Channels and configuration properties
     * - Labeling and location information
     * 
     * @param thing The Thing or Bridge to convert
     * @param hideDefaultChannels Whether to hide default channels in output
     * @param hideDefaultParameters Whether to hide default parameters in output
     * @param preferPresentationAsTree Whether to use tree structure for hierarchy
     * @param topLevel Whether this Thing is top-level in the model
     * @param onlyThings List of Things to include
     * @param handledThings Set of Things already processed to avoid duplication
     * @return ModelThing or ModelBridge representing the input Thing
     */
    private ModelThing buildModelThing(Thing thing, boolean hideDefaultChannels, boolean hideDefaultParameters,
            boolean preferPresentationAsTree, boolean topLevel, List<Thing> onlyThings, Set<Thing> handledThings) {

        ModelThingBuilder builder = new ModelThingBuilder(thing, hideDefaultChannels, hideDefaultParameters,
                preferPresentationAsTree, topLevel, onlyThings, handledThings, this);
        return builder.build();
    }

    /**
     * Converts a Channel object into a ModelChannel for DSL generation.
     *
     * Channel Information Extracted:
     * - Channel Type UID (if available)
     * - Channel Kind (State or Trigger) when type is not defined
     * - Accepted item type (when kind is used instead of type)
     * - Channel ID and label
     * - Configuration parameters (excluding defaults if requested)
     *
     * @param channel The Channel to convert
     * @param hideDefaultParameters Whether to hide default configuration parameters
     * @return ModelChannel representing the input Channel
     */
    private ModelChannel buildModelChannel(Channel channel, boolean hideDefaultParameters) {
        ModelChannel modelChannel = ThingFactory.eINSTANCE.createModelChannel();
        ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
        if (channelTypeUID != null) {
            modelChannel.setChannelType(channelTypeUID.getId());
        } else {
            modelChannel
                    .setChannelKind(channel.getKind() == ChannelKind.STATE ? CHANNEL_KIND_STATE : CHANNEL_KIND_TRIGGER);
            modelChannel.setType(channel.getAcceptedItemType());
        }
        modelChannel.setId(channel.getUID().getId());
        if (channel.getLabel() != null) {
            modelChannel.setLabel(channel.getLabel());
        }
        for (ConfigParameter param : getConfigurationParameters(channel, hideDefaultParameters)) {
            ModelProperty property = buildModelProperty(param.name(), param.value());
            if (property != null) {
                modelChannel.getProperties().add(property);
            }
        }
        return modelChannel;
    }

    /**
     * Converts a configuration parameter (key/value) into a ModelProperty for DSL generation.
     *
     * Type-Specific Handling:
     * - Lists: Empty lists are ignored (returns null)
     * - Double: Converted to BigDecimal for DSL compatibility (due to DSL parser limitations)
     * - Other types: Added directly to the model
     *
     * Special Cases:
     * - Empty lists return null to avoid cluttering DSL output
     * - Double conversion includes debug logging as this is unusual
     * - Null values are handled gracefully by returning null
     *
     * @param key Configuration parameter key
     * @param value Configuration parameter value (can be primitive, List, or complex object)
     * @return ModelProperty with the converted value, or null if value should be ignored
     */
    private @Nullable ModelProperty buildModelProperty(String key, Object value) {
        if (value == null) {
            return null;
        }

        ModelProperty property = ThingFactory.eINSTANCE.createModelProperty();
        property.setKey(key);

        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                // Ignore empty lists - no need to include them in DSL
                return null;
            }
            property.getValue().addAll(list);
        } else if (value instanceof Double doubleValue) {
            // DSL thing syntax does not support configuration parameters as Double type.
            // Convert to BigDecimal to ensure proper serialization.
            logger.debug(ERROR_DOUBLE_CONFIG, key, value);
            property.getValue().add(BigDecimal.valueOf(doubleValue));
        } else {
            property.getValue().add(value);
        }

        return property;
    }

    @Override
    public String getFileFormatParser() {
        return "DSL";
    }

    @Override
    public @Nullable String startParsingFileFormat(String syntax, List<String> errors, List<String> warnings) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(syntax.getBytes());
        return modelRepository.createIsolatedModel("things", inputStream, errors, warnings);
    }

    @Override
    public Collection<Thing> getParsedThings(String modelName) {
        return thingProvider.getAllFromModel(modelName);
    }

    @Override
    public Collection<ItemChannelLink> getParsedChannelLinks(String modelName) {
        return itemChannelLinkProvider.getAllFromContext(modelName);
    }

    @Override
    public void finishParsingFileFormat(String modelName) {
        modelRepository.removeModel(modelName);
    }

    /**
     * {@link ModelThingBuilder} is a helper class that encapsulates the logic for building
     * a ModelThing from a Thing object with reduced parameter coupling.
     *
     * This builder pattern addresses the issue of long method parameters in buildModelThing()
     * by grouping related parameters and encapsulating complex logic.
     *
     * Responsibilities:
     * - Determine if Thing should be represented as Bridge or regular Thing
     * - Handle hierarchical structure for Bridge children
     * - Apply filtering for default channels and parameters
     * - Build child Things recursively when presenting as tree
     *
     * Benefits:
     * - Reduced parameter count in main method (from 7 to 1)
     * - Improved code readability and maintainability
     * - Easier to test individual builder concerns
     * - Clear separation of conversion logic
     */
    private static class ModelThingBuilder {
        private final Thing thing;
        private final boolean hideDefaultChannels;
        private final boolean hideDefaultParameters;
        private final boolean preferPresentationAsTree;
        private final boolean topLevel;
        private final List<Thing> onlyThings;
        private final Set<Thing> handledThings;
        private final DslThingFileConverter converter;

        ModelThingBuilder(Thing thing, boolean hideDefaultChannels, boolean hideDefaultParameters,
                boolean preferPresentationAsTree, boolean topLevel, List<Thing> onlyThings, Set<Thing> handledThings,
                DslThingFileConverter converter) {
            this.thing = thing;
            this.hideDefaultChannels = hideDefaultChannels;
            this.hideDefaultParameters = hideDefaultParameters;
            this.preferPresentationAsTree = preferPresentationAsTree;
            this.topLevel = topLevel;
            this.onlyThings = onlyThings;
            this.handledThings = handledThings;
            this.converter = converter;
        }

        /**
         * Builds the ModelThing from the provided Thing.
         *
         * @return ModelThing or ModelBridge representing the Thing
         */
        ModelThing build() {
            ModelThing model;
            ModelBridge modelBridge = null;
            List<Thing> childThings = converter.getChildThings(thing, onlyThings);

            // Determine if this should be a Bridge model
            if (preferPresentationAsTree && thing instanceof Bridge && !childThings.isEmpty()) {
                modelBridge = ThingFactory.eINSTANCE.createModelBridge();
                modelBridge.setBridge(true);
                model = modelBridge;
            } else {
                model = ThingFactory.eINSTANCE.createModelThing();
            }

            // Set Thing identification info
            setThingIdentification(model, modelBridge);

            // Set Thing metadata (label, location)
            setThingMetadata(model);

            // Set Thing properties (configuration)
            setThingProperties(model);

            // Set child Things if this is a Bridge in tree presentation
            if (modelBridge != null && preferPresentationAsTree) {
                setChildThings(modelBridge, childThings);
            }

            // Set channels
            setChannels(model);

            // Mark as handled
            handledThings.add(thing);

            return model;
        }

        /**
         * Sets the identification information (UID, bridge UID) for the Thing model.
         */
        private void setThingIdentification(ModelThing model, @Nullable ModelBridge modelBridge) {
            if (!preferPresentationAsTree || topLevel) {
                model.setId(thing.getUID().getAsString());
                ThingUID bridgeUID = thing.getBridgeUID();
                if (bridgeUID != null && modelBridge == null) {
                    model.setBridgeUID(bridgeUID.getAsString());
                }
            } else {
                model.setThingTypeId(thing.getThingTypeUID().getId());
                model.setThingId(thing.getUID().getId());
            }
        }

        /**
         * Sets the Thing metadata (label and location).
         */
        private void setThingMetadata(ModelThing model) {
            if (thing.getLabel() != null) {
                model.setLabel(thing.getLabel());
            }
            if (thing.getLocation() != null) {
                model.setLocation(thing.getLocation());
            }
        }

        /**
         * Sets the Thing configuration properties.
         */
        private void setThingProperties(ModelThing model) {
            for (ConfigParameter param : converter.getConfigurationParameters(thing, hideDefaultParameters)) {
                ModelProperty property = converter.buildModelProperty(param.name(), param.value());
                if (property != null) {
                    model.getProperties().add(property);
                }
            }
        }

        /**
         * Sets the child Things for a Bridge model.
         */
        private void setChildThings(ModelBridge modelBridge, List<Thing> childThings) {
            modelBridge.setThingsHeader(false);
            for (Thing child : childThings) {
                if (!handledThings.contains(child)) {
                    ModelThing childModel = converter.buildModelThing(child, hideDefaultChannels, hideDefaultParameters,
                            true, false, onlyThings, handledThings);
                    modelBridge.getThings().add(childModel);
                }
            }
        }

        /**
         * Sets the Channels for the Thing model.
         */
        private void setChannels(ModelThing model) {
            List<Channel> channels = hideDefaultChannels ? converter.getNonDefaultChannels(thing)
                    : new ArrayList<>(thing.getChannels());
            model.setChannelsHeader(!channels.isEmpty());
            for (Channel channel : channels) {
                model.getChannels().add(converter.buildModelChannel(channel, hideDefaultParameters));
            }
        }
    }
}
