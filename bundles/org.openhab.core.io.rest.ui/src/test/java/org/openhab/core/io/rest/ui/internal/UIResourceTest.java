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
package org.openhab.core.io.rest.ui.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.ui.components.RootUIComponent;
import org.openhab.core.ui.components.UIComponentRegistry;
import org.openhab.core.ui.components.UIComponentRegistryFactory;
import org.openhab.core.ui.tiles.TileProvider;

/**
 * Tests {@link UIResource}.
 *
 * @author Nuraiman Danial - Initial contribution
 */
class UIResourceTest {
    private UIComponentRegistryFactory factory;
    private UIComponentRegistry registry;
    private TileProvider tileProvider;
    private UIResource resource;

    @BeforeEach
    void setup() {
        factory = mock(UIComponentRegistryFactory.class);
        registry = mock(UIComponentRegistry.class);
        tileProvider = mock(TileProvider.class);

        when(factory.getRegistry("ns")).thenReturn(registry);

        resource = new UIResource(factory, tileProvider);
    }

    @Test
    void getAllComponents() {
        RootUIComponent component = mock(RootUIComponent.class);
        when(component.getUID()).thenReturn("uid-1");
        when(component.getType()).thenReturn("type-A");
        when(component.getTags()).thenReturn(Collections.singleton("tag-1"));
        when(component.getTimestamp()).thenReturn(new Date());

        when(registry.getAll()).thenReturn(List.of(component));

        Request request = mock(Request.class);
        Response response = resource.getAllComponents(request, "ns", Boolean.TRUE);
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    void getAllComponentsWhenCached() {
        when(registry.getAll()).thenReturn(Collections.emptyList());

        Request request = mock(Request.class);

        Response first = resource.getAllComponents(request, "ns", Boolean.FALSE);
        assertEquals(200, first.getStatus());
        assertNotNull(first.getEntity());

        Response.ResponseBuilder builder = Response.notModified();
        when(request.evaluatePreconditions(any(Date.class))).thenReturn(builder);

        Response second = resource.getAllComponents(request, "ns", Boolean.FALSE);
        assertEquals(304, second.getStatus());
    }
}
