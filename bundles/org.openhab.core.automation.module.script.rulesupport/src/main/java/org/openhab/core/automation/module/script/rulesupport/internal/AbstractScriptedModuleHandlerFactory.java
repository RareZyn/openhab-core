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
package org.openhab.core.automation.module.script.rulesupport.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.Condition;
import org.openhab.core.automation.Module;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.handler.BaseModuleHandlerFactory;
import org.openhab.core.automation.handler.ModuleHandler;
import org.openhab.core.automation.module.script.rulesupport.internal.delegates.SimpleActionHandlerDelegate;
import org.openhab.core.automation.module.script.rulesupport.internal.delegates.SimpleConditionHandlerDelegate;
import org.openhab.core.automation.module.script.rulesupport.internal.delegates.SimpleTriggerHandlerDelegate;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedHandler;
import org.openhab.core.automation.module.script.rulesupport.shared.factories.ScriptedActionHandlerFactory;
import org.openhab.core.automation.module.script.rulesupport.shared.factories.ScriptedConditionHandlerFactory;
import org.openhab.core.automation.module.script.rulesupport.shared.factories.ScriptedTriggerHandlerFactory;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleActionHandler;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleConditionHandler;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleTriggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link AbstractScriptedModuleHandlerFactory} wraps ScriptedHandler instances and delegates them to the
 * appropriate ModuleHandler based on their type.
 *
 * <p>
 * This factory serves as a bridge between script-defined handlers and the openHAB automation engine. It inspects the
 * type of the provided ScriptedHandler and wraps it in a suitable delegate or factory-generated handler.
 * </p>
 *
 * <p>
 * Supported handler types:
 * </p>
 * <ul>
 * <li>SimpleActionHandler → wrapped in SimpleActionHandlerDelegate</li>
 * <li>SimpleConditionHandler → wrapped in SimpleConditionHandlerDelegate</li>
 * <li>SimpleTriggerHandler → wrapped in SimpleTriggerHandlerDelegate</li>
 * <li>ScriptedActionHandlerFactory → creates Action handlers dynamically</li>
 * <li>ScriptedConditionHandlerFactory → creates Condition handlers dynamically</li>
 * <li>ScriptedTriggerHandlerFactory → creates Trigger handlers dynamically</li>
 * </ul>
 *
 * @author Simon Merschjohann - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractScriptedModuleHandlerFactory extends BaseModuleHandlerFactory {
    Logger logger = LoggerFactory.getLogger(AbstractScriptedModuleHandlerFactory.class);

    /**
     * Creates a ModuleHandler from a ScriptedHandler by wrapping it in the appropriate delegate.
     *
     * <p>
     * This method performs type inspection on the provided scriptedHandler and returns the correct ModuleHandler
     * implementation:
     * </p>
     * <ul>
     * <li>For simple handlers (SimpleActionHandler, SimpleConditionHandler, SimpleTriggerHandler), creates a
     * corresponding delegate that bridges the script handler to the automation engine.</li>
     * <li>For factory handlers (ScriptedActionHandlerFactory, etc.), invokes the factory to create the handler.</li>
     * </ul>
     *
     * @param module the automation module (Action, Condition, or Trigger) to be handled
     * @param scriptedHandler the script-defined handler that implements the module logic, or null if not available
     * @return a ModuleHandler wrapping the scriptedHandler, or null if scriptedHandler is null or unsupported
     */
    protected @Nullable ModuleHandler getModuleHandler(Module module, @Nullable ScriptedHandler scriptedHandler) {
        ModuleHandler moduleHandler = null;

        if (scriptedHandler != null) {
            // Simple handler delegates - wrap individual script handlers
            if (scriptedHandler instanceof SimpleActionHandler handler) {
                moduleHandler = new SimpleActionHandlerDelegate((Action) module, handler);
            } else if (scriptedHandler instanceof SimpleConditionHandler handler) {
                moduleHandler = new SimpleConditionHandlerDelegate((Condition) module, handler);
            } else if (scriptedHandler instanceof SimpleTriggerHandler handler) {
                moduleHandler = new SimpleTriggerHandlerDelegate((Trigger) module, handler);
            }
            // Factory-based handlers - use factory to create handlers
            else if (scriptedHandler instanceof ScriptedActionHandlerFactory factory) {
                moduleHandler = factory.get((Action) module);
            } else if (scriptedHandler instanceof ScriptedTriggerHandlerFactory factory) {
                moduleHandler = factory.get((Trigger) module);
            } else if (scriptedHandler instanceof ScriptedConditionHandlerFactory factory) {
                moduleHandler = factory.get((Condition) module);
            }
            // Unsupported handler type
            else {
                logger.error("Not supported moduleHandler: {}", module.getTypeUID());
            }
        }

        return moduleHandler;
    }
}
