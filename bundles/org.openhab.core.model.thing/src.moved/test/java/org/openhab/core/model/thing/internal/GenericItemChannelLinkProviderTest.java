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
package org.openhab.core.model.thing.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.model.item.BindingConfigParseException;
import org.openhab.core.thing.link.ItemChannelLink;
import org.openhab.core.thing.profiles.ProfileTypeUID;

/**
 * Unit tests for {@link GenericItemChannelLinkProvider}.
 *
 * Tests cover:
 * - Binding parsing with valid and invalid UIDs
 * - Transaction management (start/stop) for single and concurrent contexts
 * - Profile scope normalization
 * - Channel link creation and removal
 *
 * @author Test Author - Test contribution
 */
public class GenericItemChannelLinkProviderTest {

    private GenericItemChannelLinkProvider provider;

    @BeforeEach
    public void setUp() {
        provider = new GenericItemChannelLinkProvider();
    }

    /**
     * Test: Valid single Channel UID creates link
     */
    @Test
    public void testProcessValidSingleChannelUID() throws BindingConfigParseException {
        String context = "test.things";
        String itemName = "TestItem";
        String channelUID = "binding:thing:device:channel1";
        Configuration config = new Configuration();

        provider.processBindingConfiguration(context, "Number", itemName, channelUID, config);

        Collection<ItemChannelLink> links = provider.getAllFromContext(context);
        assertEquals(1, links.size());
        ItemChannelLink link = links.iterator().next();
        assertEquals(itemName, link.getItemName());
        assertEquals(channelUID, link.getLinkedUID().getAsString());
    }

    /**
     * Test: Multiple comma-separated Channel UIDs create multiple links
     */
    @Test
    public void testProcessMultipleChannelUIDs() throws BindingConfigParseException {
        String context = "test.things";
        String itemName = "MultiChannelItem";
        String bindingConfig = "binding:thing:device:channel1,binding:thing:device:channel2";
        Configuration config = new Configuration();

        provider.processBindingConfiguration(context, "Number", itemName, bindingConfig, config);

        Collection<ItemChannelLink> links = provider.getAllFromContext(context);
        assertEquals(2, links.size());
    }

    /**
     * Test: Empty binding config throws exception
     */
    @Test
    public void testProcessEmptyBindingConfigThrowsException() {
        String context = "test.things";
        String itemName = "TestItem";
        String bindingConfig = "";
        Configuration config = new Configuration();

        assertThrows(BindingConfigParseException.class, () -> {
            provider.processBindingConfiguration(context, "Number", itemName, bindingConfig, config);
        });
    }

    /**
     * Test: Binding config with only whitespace throws exception
     */
    @Test
    public void testProcessWhitespaceOnlyBindingConfigThrowsException() {
        String context = "test.things";
        String itemName = "TestItem";
        String bindingConfig = "  ,  , ";
        Configuration config = new Configuration();

        assertThrows(BindingConfigParseException.class, () -> {
            provider.processBindingConfiguration(context, "Number", itemName, bindingConfig, config);
        });
    }

    /**
     * Test: Valid UID with more than 4 parts still creates link
     */
    @Test
    public void testProcessChannelUIDWithExtraPartsCreatesLink() throws BindingConfigParseException {
        String context = "test.things";
        String itemName = "TestItem";
        String bindingConfig = "not:a:valid:channel:uid:format"; // 6 parts, but still parsed
        Configuration config = new Configuration();

        provider.processBindingConfiguration(context, "Number", itemName, bindingConfig, config);

        Collection<ItemChannelLink> links = provider.getAllFromContext(context);
        assertEquals(1, links.size());
        ItemChannelLink link = links.iterator().next();
        assertEquals(itemName, link.getItemName());
    }

    /**
     * Test: Profile without scope prefix is normalized
     */
    @Test
    public void testProfileScopeNormalization() throws BindingConfigParseException {
        String context = "test.things";
        String itemName = "TestItem";
        String channelUID = "binding:thing:device:channel1";
        Configuration config = new Configuration();
        config.put("profile", "default"); // No scope prefix

        provider.processBindingConfiguration(context, "Number", itemName, channelUID, config);

        Collection<ItemChannelLink> links = provider.getAllFromContext(context);
        ItemChannelLink link = links.iterator().next();
        String profile = (String) link.getConfiguration().get("profile");
        assertEquals(ProfileTypeUID.SYSTEM_SCOPE + ":default", profile);
    }

    /**
     * Test: Profile with existing scope prefix is not modified
     */
    @Test
    public void testProfileWithScopeIsNotModified() throws BindingConfigParseException {
        String context = "test.things";
        String itemName = "TestItem";
        String channelUID = "binding:thing:device:channel1";
        Configuration config = new Configuration();
        config.put("profile", "custom:myprofile"); // Already has scope

        provider.processBindingConfiguration(context, "Number", itemName, channelUID, config);

        Collection<ItemChannelLink> links = provider.getAllFromContext(context);
        ItemChannelLink link = links.iterator().next();
        String profile = (String) link.getConfiguration().get("profile");
        assertEquals("custom:myprofile", profile);
    }

    /**
     * Test: Start/stop configuration update for single context removes deleted items
     */
    @Test
    public void testStartStopConfigurationUpdateRemovesDeletedItems() throws BindingConfigParseException {
        String context = "test.things";

        // Add an initial item
        provider.processBindingConfiguration(context, "Number", "Item1", "binding:thing:device:channel1",
                new Configuration());
        assertEquals(1, provider.getAllFromContext(context).size());

        // Start update and process only a different item (simulating deletion)
        provider.startConfigurationUpdate(context);
        provider.processBindingConfiguration(context, "Number", "Item2", "binding:thing:device:channel2",
                new Configuration());
        provider.stopConfigurationUpdate(context);

        // Item1 should be removed, Item2 should remain
        Collection<ItemChannelLink> links = provider.getAllFromContext(context);
        assertEquals(1, links.size());
        ItemChannelLink link = links.iterator().next();
        assertEquals("Item2", link.getItemName());
    }

    /**
     * Test: Concurrent updates on different contexts do not interfere
     * This is a critical test for the per-context transaction fix.
     */
    @Test
    public void testConcurrentContextUpdatesDoNotInterfere() throws BindingConfigParseException {
        String context1 = "test1.things";
        String context2 = "test2.things";

        // Setup initial items in both contexts
        provider.processBindingConfiguration(context1, "Number", "Item1", "binding:thing:device:channel1",
                new Configuration());
        provider.processBindingConfiguration(context2, "Number", "ItemA", "binding:thing:device:channelA",
                new Configuration());

        // Start update on context1
        provider.startConfigurationUpdate(context1);
        provider.processBindingConfiguration(context1, "Number", "Item2", "binding:thing:device:channel2",
                new Configuration());

        // Start update on context2 (should not affect context1's transaction)
        provider.startConfigurationUpdate(context2);
        provider.processBindingConfiguration(context2, "Number", "ItemB", "binding:thing:device:channelB",
                new Configuration());

        // Stop context1: Item1 should be removed, Item2 should remain
        provider.stopConfigurationUpdate(context1);

        // Stop context2: ItemA should be removed, ItemB should remain
        provider.stopConfigurationUpdate(context2);

        Collection<ItemChannelLink> links1 = provider.getAllFromContext(context1);
        Collection<ItemChannelLink> links2 = provider.getAllFromContext(context2);

        // Verify context1 has only Item2
        assertEquals(1, links1.size());
        assertEquals("Item2", links1.iterator().next().getItemName());

        // Verify context2 has only ItemB (no cross-contamination)
        assertEquals(1, links2.size());
        assertEquals("ItemB", links2.iterator().next().getItemName());
    }

    /**
     * Test: Removed channels are cleaned up during update
     */
    @Test
    public void testRemovedChannelsAreCleanedUpDuringUpdate() throws BindingConfigParseException {
        String context = "test.things";
        String itemName = "MultiChannelItem";

        // Add multiple channels to an item
        provider.startConfigurationUpdate(context);
        provider.processBindingConfiguration(context, "Number", itemName,
                "binding:thing:device:channel1,binding:thing:device:channel2", new Configuration());
        provider.stopConfigurationUpdate(context);

        assertEquals(2, provider.getAllFromContext(context).size());

        // Remove one channel by processing a different binding
        provider.startConfigurationUpdate(context);
        provider.processBindingConfiguration(context, "Number", itemName, "binding:thing:device:channel1",
                new Configuration());
        provider.stopConfigurationUpdate(context);

        // Only one channel should remain
        assertEquals(1, provider.getAllFromContext(context).size());
        ItemChannelLink link = provider.getAllFromContext(context).iterator().next();
        assertEquals("binding:thing:device:channel1", link.getLinkedUID().getAsString());
    }
}
