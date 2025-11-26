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
package org.openhab.core.automation.module.script.rulesupport.shared;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.Condition;
import org.openhab.core.automation.Rule;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.rulesupport.internal.ScriptedCustomModuleHandlerFactory;
import org.openhab.core.automation.module.script.rulesupport.internal.ScriptedCustomModuleTypeProvider;
import org.openhab.core.automation.module.script.rulesupport.internal.ScriptedPrivateModuleHandlerFactory;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleActionHandler;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleConditionHandler;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRuleActionHandler;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRuleActionHandlerDelegate;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleTriggerHandler;
import org.openhab.core.automation.type.ActionType;
import org.openhab.core.automation.type.ConditionType;
import org.openhab.core.automation.type.TriggerType;
import org.openhab.core.automation.util.ActionBuilder;
import org.openhab.core.automation.util.ModuleBuilder;
import org.openhab.core.automation.util.RuleBuilder;
import org.openhab.core.config.core.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Registry is used for a single ScriptEngine instance. It allows the adding and removing of handlers.
 * It allows the removal of previously added modules on unload.
 *
 * <p>
 * The ScriptedAutomationManager serves as the central management point for script-defined automation components
 * including custom module types (Actions, Conditions, Triggers), their handlers, and rules. Each script execution
 * context has its own instance of this manager to isolate script-defined components.
 * </p>
 *
 * <p>
 * Key responsibilities:
 * </p>
 * <ul>
 * <li>Register and unregister custom module types (ActionType, ConditionType, TriggerType)</li>
 * <li>Manage handlers for custom modules (public and private handlers)</li>
 * <li>Add and track rules created by scripts</li>
 * <li>Clean up all script-created components when script is unloaded</li>
 * </ul>
 *
 * @author Simon Merschjohann - Initial contribution
 */
@NonNullByDefault
public class ScriptedAutomationManager {

    private final Logger logger = LoggerFactory.getLogger(ScriptedAutomationManager.class);

    private final RuleSupportRuleRegistryDelegate ruleRegistryDelegate;

    // Track all module types registered by this script
    private final Set<String> modules = new HashSet<>();
    // Track all public module handlers registered by this script
    private final Set<String> moduleHandlers = new HashSet<>();
    // Track all private handlers registered by this script
    private final Set<String> privateHandlers = new HashSet<>();

    private final ScriptedCustomModuleHandlerFactory scriptedCustomModuleHandlerFactory;
    private final ScriptedCustomModuleTypeProvider scriptedCustomModuleTypeProvider;
    private final ScriptedPrivateModuleHandlerFactory scriptedPrivateModuleHandlerFactory;

    /**
     * Creates a new ScriptedAutomationManager for managing script-defined automation components.
     *
     * @param ruleRegistryDelegate delegate for managing rules in the rule registry
     * @param scriptedCustomModuleHandlerFactory factory for managing public module handlers
     * @param scriptedCustomModuleTypeProvider provider for managing custom module types
     * @param scriptedPrivateModuleHandlerFactory factory for managing private (script-internal) handlers
     */
    public ScriptedAutomationManager(RuleSupportRuleRegistryDelegate ruleRegistryDelegate,
            ScriptedCustomModuleHandlerFactory scriptedCustomModuleHandlerFactory,
            ScriptedCustomModuleTypeProvider scriptedCustomModuleTypeProvider,
            ScriptedPrivateModuleHandlerFactory scriptedPrivateModuleHandlerFactory) {
        this.ruleRegistryDelegate = ruleRegistryDelegate;
        this.scriptedCustomModuleHandlerFactory = scriptedCustomModuleHandlerFactory;
        this.scriptedCustomModuleTypeProvider = scriptedCustomModuleTypeProvider;
        this.scriptedPrivateModuleHandlerFactory = scriptedPrivateModuleHandlerFactory;
    }

    public void removeModuleType(String uid) {
        if (modules.remove(uid)) {
            scriptedCustomModuleTypeProvider.removeModuleType(uid);
            removeHandler(uid);
        }
    }

    public void removeHandler(String typeUID) {
        if (moduleHandlers.remove(typeUID)) {
            scriptedCustomModuleHandlerFactory.removeModuleHandler(typeUID);
        }
    }

    public void removePrivateHandler(String privId) {
        if (privateHandlers.remove(privId)) {
            scriptedPrivateModuleHandlerFactory.removeHandler(privId);
        }
    }

    /**
     * Removes all components registered by this script.
     *
     * <p>
     * This method performs complete cleanup of all script-registered components in the following order:
     * </p>
     * <ol>
     * <li>Remove all custom module types</li>
     * <li>Remove all public module handlers</li>
     * <li>Remove all private handlers</li>
     * <li>Remove all rules added by the script</li>
     * </ol>
     *
     * <p>
     * This method is typically called when a script is unloaded or reloaded to ensure clean state.
     * </p>
     */
    public void removeAll() {
        // Create defensive copies to avoid ConcurrentModificationException
        Set<String> types = new HashSet<>(modules);
        for (String moduleType : types) {
            removeModuleType(moduleType);
        }

        Set<String> moduleHandlers = new HashSet<>(this.moduleHandlers);
        for (String uid : moduleHandlers) {
            removeHandler(uid);
        }

        Set<String> privateHandlers = new HashSet<>(this.privateHandlers);
        for (String privId : privateHandlers) {
            removePrivateHandler(privId);
        }

        ruleRegistryDelegate.removeAllAddedByScript();
    }

    /**
     * Adds a rule to the rule registry and tracks it for cleanup.
     *
     * <p>
     * This method processes the rule element, ensures all modules have proper IDs, and registers it with the rule
     * registry. The rule will be automatically removed when {@link #removeAll()} is called.
     * </p>
     *
     * @param element the rule to add (must have a unique UID)
     * @return the processed and registered rule
     */
    public Rule addRule(Rule element) {
        Rule rule = addUnmanagedRule(element);

        ruleRegistryDelegate.add(rule);

        return rule;
    }

    public Rule addUnmanagedRule(Rule element) {
        RuleBuilder builder = RuleBuilder.create(element.getUID());

        String name = element.getName();
        if (name == null || name.isEmpty()) {
            name = element.getClass().getSimpleName();
            if (name.contains("$")) {
                name = name.substring(0, name.indexOf('$'));
            }
        }

        builder.withName(name).withDescription(element.getDescription()).withTags(element.getTags());

        // used for numbering the modules of the rule
        int moduleIndex = 1;

        // Add conditions if present (conditions are optional in rules)
        try {
            List<Condition> conditions = new ArrayList<>();
            for (Condition cond : element.getConditions()) {
                Condition toAdd = cond;
                if (cond.getId().isEmpty()) {
                    toAdd = ModuleBuilder.createCondition().withId(Integer.toString(moduleIndex++))
                            .withTypeUID(cond.getTypeUID()).withConfiguration(cond.getConfiguration())
                            .withInputs(cond.getInputs()).build();
                }

                conditions.add(toAdd);
            }

            builder.withConditions(conditions);
        } catch (NullPointerException ex) {
            // No conditions defined - this is valid, conditions are optional
            logger.trace("Rule '{}' has no conditions (optional)", element.getUID());
        } catch (IllegalArgumentException ex) {
            // Invalid condition configuration
            logger.warn("Invalid condition configuration in rule '{}': {}", element.getUID(), ex.getMessage());
        } catch (Exception ex) {
            // Unexpected error while processing conditions
            logger.error("Unexpected error while adding conditions to rule '{}': {}", element.getUID(), ex.getMessage(),
                    ex);
        }

        // Add triggers if present (triggers are optional in rules, though typically required)
        try {
            List<Trigger> triggers = new ArrayList<>();
            for (Trigger trigger : element.getTriggers()) {
                Trigger toAdd = trigger;
                if (trigger.getId().isEmpty()) {
                    toAdd = ModuleBuilder.createTrigger().withId(Integer.toString(moduleIndex++))
                            .withTypeUID(trigger.getTypeUID()).withConfiguration(trigger.getConfiguration()).build();
                }

                triggers.add(toAdd);
            }

            builder.withTriggers(triggers);
        } catch (NullPointerException ex) {
            // No triggers defined - this may be valid for some rule types
            logger.trace("Rule '{}' has no triggers (optional)", element.getUID());
        } catch (IllegalArgumentException ex) {
            // Invalid trigger configuration
            logger.warn("Invalid trigger configuration in rule '{}': {}", element.getUID(), ex.getMessage());
        } catch (Exception ex) {
            // Unexpected error while processing triggers
            logger.error("Unexpected error while adding triggers to rule '{}': {}", element.getUID(), ex.getMessage(),
                    ex);
        }

        List<Action> actions = new ArrayList<>(element.getActions());

        if (element instanceof SimpleRuleActionHandler handler) {
            String privId = addPrivateActionHandler(new SimpleRuleActionHandlerDelegate(handler));

            Action scriptedAction = ActionBuilder.create().withId(Integer.toString(moduleIndex++))
                    .withTypeUID("jsr223.ScriptedAction").withConfiguration(new Configuration()).build();
            scriptedAction.getConfiguration().put("privId", privId);
            actions.add(scriptedAction);
        }

        builder.withConfiguration(element.getConfiguration());
        builder.withActions(actions);

        return builder.build();
    }

    public void addConditionType(ConditionType conditionType) {
        modules.add(conditionType.getUID());
        scriptedCustomModuleTypeProvider.addModuleType(conditionType);
    }

    public void addConditionHandler(String uid, ScriptedHandler conditionHandler) {
        moduleHandlers.add(uid);
        scriptedCustomModuleHandlerFactory.addModuleHandler(uid, conditionHandler);
        scriptedCustomModuleTypeProvider.updateModuleHandler(uid);
    }

    public String addPrivateConditionHandler(SimpleConditionHandler conditionHandler) {
        String uid = scriptedPrivateModuleHandlerFactory.addHandler(conditionHandler);
        privateHandlers.add(uid);
        return uid;
    }

    public void addActionType(ActionType actionType) {
        modules.add(actionType.getUID());
        scriptedCustomModuleTypeProvider.addModuleType(actionType);
    }

    public void addActionHandler(String uid, ScriptedHandler actionHandler) {
        moduleHandlers.add(uid);
        scriptedCustomModuleHandlerFactory.addModuleHandler(uid, actionHandler);
        scriptedCustomModuleTypeProvider.updateModuleHandler(uid);
    }

    public String addPrivateActionHandler(SimpleActionHandler actionHandler) {
        String uid = scriptedPrivateModuleHandlerFactory.addHandler(actionHandler);
        privateHandlers.add(uid);
        return uid;
    }

    public void addTriggerType(TriggerType triggerType) {
        modules.add(triggerType.getUID());
        scriptedCustomModuleTypeProvider.addModuleType(triggerType);
    }

    public void addTriggerHandler(String uid, ScriptedHandler triggerHandler) {
        moduleHandlers.add(uid);
        scriptedCustomModuleHandlerFactory.addModuleHandler(uid, triggerHandler);
        scriptedCustomModuleTypeProvider.updateModuleHandler(uid);
    }

    public String addPrivateTriggerHandler(SimpleTriggerHandler triggerHandler) {
        String uid = scriptedPrivateModuleHandlerFactory.addHandler(triggerHandler);
        privateHandlers.add(uid);
        return uid;
    }
}
