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
package org.openhab.core.automation.module.script.internal.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.Module;
import org.openhab.core.automation.handler.BaseModuleHandler;
import org.openhab.core.automation.module.script.ScriptEngineContainer;
import org.openhab.core.automation.module.script.ScriptEngineManager;
import org.openhab.core.config.core.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an abstract class that can be used when implementing any module handler that handles scripts.
 * <p>
 * Remember to implement multi-thread synchronization in the concrete handler if the script engine is not thread-safe!
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Simon Merschjohann - Initial contribution
 * @author Florian Hotze - Add support for script pre-compilation
 *
 * @param <T> the type of module the concrete handler can handle
 */
@NonNullByDefault
public abstract class AbstractScriptModuleHandler<T extends Module> extends BaseModuleHandler<T> {

    private final Logger logger = LoggerFactory.getLogger(AbstractScriptModuleHandler.class);

    /** Constant defining the configuration parameter of modules that specifies the mime type of a script */
    public static final String CONFIG_SCRIPT_TYPE = "type";

    /** Constant defining the configuration parameter of modules that specifies the script itself */
    public static final String CONFIG_SCRIPT = "script";

    /**
     * Constant defining the context key of the module type id.
     */
    public static final String CONTEXT_KEY_MODULE_TYPE_ID = "oh.module-type-id";

    /**
     * Constant defining the context key for the rule UID variable accessible in scripts.
     */
    public static final String CONTEXT_KEY_RULE_UID = "ruleUID";

    /**
     * Constant defining the context key for the context map variable accessible in scripts.
     * This variable contains all context variables passed from the rule engine.
     */
    public static final String CONTEXT_KEY_CTX = "ctx";

    protected final ScriptEngineManager scriptEngineManager;

    private final String engineIdentifier;

    private Optional<ScriptEngine> scriptEngine = Optional.empty();
    private Optional<CompiledScript> compiledScript = Optional.empty();
    private final String type;
    protected final String script;

    protected final String ruleUID;

    protected AbstractScriptModuleHandler(T module, String ruleUID, ScriptEngineManager scriptEngineManager) {
        super(module);
        this.scriptEngineManager = scriptEngineManager;
        this.ruleUID = ruleUID;
        this.engineIdentifier = UUID.randomUUID().toString();

        this.type = getValidConfigParameter(CONFIG_SCRIPT_TYPE, module.getConfiguration(), module.getId(), false);
        this.script = getValidConfigParameter(CONFIG_SCRIPT, module.getConfiguration(), module.getId(), true);
    }

    private static String getValidConfigParameter(String parameter, Configuration config, String moduleId,
            boolean emptyAllowed) {
        Object value = config.get(parameter);
        if (value instanceof String string && (emptyAllowed || !string.trim().isEmpty())) {
            return string;
        } else {
            throw new IllegalStateException(String.format(
                    "Config parameter '%s' is missing in the configuration of module '%s'.", parameter, moduleId));
        }
    }

    /**
     * Creates the {@link ScriptEngine} and compiles the script if the {@link ScriptEngine} implements
     * {@link Compilable}.
     */
    protected void compileScript() throws ScriptException {
        if (compiledScript.isPresent() || script.isEmpty()) {
            return;
        }
        if (!scriptEngineManager.isSupported(type)) {
            logger.debug(
                    "ScriptEngine for language '{}' could not be found, skipping compilation of script for identifier: {}",
                    type, engineIdentifier);
            return;
        }
        Optional<ScriptEngine> engine = getScriptEngine();
        if (engine.isPresent()) {
            ScriptEngine scriptEngine = engine.get();
            if (scriptEngine instanceof Compilable compilable) {
                logger.debug("Pre-compiling script of rule with UID '{}'", ruleUID);
                compiledScript = Optional.ofNullable(compilable.compile(script));
            }
        }
    }

    @Override
    public void dispose() {
        scriptEngineManager.removeEngine(engineIdentifier);
    }

    /**
     * Reset the script engine to force a script reload
     */
    public synchronized void resetScriptEngine() {
        scriptEngineManager.removeEngine(engineIdentifier);
        scriptEngine = Optional.empty();
        compiledScript = Optional.empty();
    }

    /**
     * Gets the unique identifier of the rule this module handler is used for.
     *
     * @return the UID of the rule
     */
    public String getRuleUID() {
        return ruleUID;
    }

    /**
     * Gets the type identifier of this module handler
     * 
     * @return the type identifier
     */
    abstract public String getTypeId();

    /**
     * Gets the script engine identifier for this module
     *
     * @return the engine identifier string
     */
    public String getEngineIdentifier() {
        return engineIdentifier;
    }

    /**
     * Get the script engine instance used by this module handler.
     *
     * @return the script engine instance if available, otherwise Optional.empty()
     */
    protected Optional<ScriptEngine> getScriptEngine() {
        return scriptEngine.isPresent() ? scriptEngine : createScriptEngine();
    }

    /**
     * Creates a new script engine for the type defined in the module configuration.
     *
     * @return the script engine if available, otherwise Optional.empty()
     */
    private Optional<ScriptEngine> createScriptEngine() {
        ScriptEngineContainer container = scriptEngineManager.createScriptEngine(type, engineIdentifier);

        if (container != null) {
            ScriptEngine engine = container.getScriptEngine();
            if (engine == null) {
                logger.error("ScriptEngine is null in container for type '{}', rule UID '{}'", type, ruleUID);
                return Optional.empty();
            }

            scriptEngine = Optional.of(engine);

            // Inject the module type id into the script context early, so engines can access it before script
            // invocation.
            ScriptContext scriptContext = engine.getContext();
            if (scriptContext == null) {
                logger.error(
                        "Script context is null for script engine '{}' of rule with UID '{}'. Please report this bug.",
                        engineIdentifier, ruleUID);
            } else {
                scriptContext.setAttribute(CONTEXT_KEY_MODULE_TYPE_ID, getTypeId(), ScriptContext.ENGINE_SCOPE);
            }
            return scriptEngine;
        } else {
            logger.debug("No engine available for script type '{}' in module '{}'.", type, module.getId());
            return Optional.empty();
        }
    }

    /**
     * Adds the passed context variables of the rule engine to the context scope of the ScriptEngine.
     * This should be done each time the module is executed to prevent leaking context to later executions.
     * <p>
     * <b>Context Variable Transformation:</b>
     * <ul>
     * <li>Rule engine passes context variables with prefixes (e.g., "event.itemName")</li>
     * <li>Prefixes are stripped to make variables more accessible in scripts (e.g., "itemName")</li>
     * <li>Variables are made available both individually and grouped in a "ctx" map</li>
     * <li>The rule UID is also injected for script debugging and logging purposes</li>
     * </ul>
     * <p>
     * <b>Example:</b>
     * 
     * <pre>
     * Input context: {"event.itemName": "Light", "event.itemState": "ON"}
     * Script access: itemName, itemState, ctx.itemName, ctx.itemState, ruleUID
     * </pre>
     *
     * @param engine the script engine that is used
     * @param context the variables and types to add to the execution context
     */
    protected void setExecutionContext(ScriptEngine engine, Map<String, ?> context) {
        ScriptContext executionContext = engine.getContext();

        // Create a new map to hold transformed context variables.
        // Note: We don't use "context" as the variable name because it doesn't work on all JVM versions!
        final Map<String, Object> contextNew = new HashMap<>();

        // Transform context variables: remove prefixes (everything before the first dot).
        // This allows scripts to access "itemName" instead of "event.itemName".
        for (Entry<String, ?> entry : context.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            int dotIndex = key.indexOf('.');
            if (dotIndex != -1) {
                // Strip prefix (e.g., "event.itemName" becomes "itemName")
                key = key.substring(dotIndex + 1);
            }
            contextNew.put(key, value);
        }

        // Add the rule's UID to the context for script debugging/logging
        contextNew.put(CONTEXT_KEY_RULE_UID, this.ruleUID);

        // Make all context variables available as a single "ctx" map
        executionContext.setAttribute(CONTEXT_KEY_CTX, contextNew, ScriptContext.ENGINE_SCOPE);

        // Also add each context variable individually to the script scope for convenience
        for (Entry<String, ?> entry : contextNew.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            executionContext.setAttribute(key, value, ScriptContext.ENGINE_SCOPE);
        }
    }

    /**
     * Removes passed context variables of the rule engine from the context scope of the ScriptEngine.
     * This should be called after each script execution to prevent context variables from leaking
     * between different rule executions.
     * <p>
     * <b>Note:</b> This method removes the transformed context variables (with prefixes stripped),
     * matching the transformation performed in {@link #setExecutionContext}.
     *
     * @param engine the script engine that is used
     * @param context the variables and types to remove from the execution context
     */
    protected void resetExecutionContext(ScriptEngine engine, Map<String, ?> context) {
        ScriptContext executionContext = engine.getContext();

        // Remove each context variable using the same key transformation as setExecutionContext
        for (Entry<String, ?> entry : context.entrySet()) {
            String key = entry.getKey();
            int dotIndex = key.indexOf('.');
            if (dotIndex != -1) {
                // Strip prefix to match the key used in setExecutionContext
                key = key.substring(dotIndex + 1);
            }
            executionContext.removeAttribute(key, ScriptContext.ENGINE_SCOPE);
        }
    }

    /**
     * Evaluates the script with the given script engine.
     * <p>
     * This method executes either a pre-compiled script (if available and the engine supports compilation)
     * or interprets the script source directly. Script execution errors are logged with contextual information.
     * <p>
     * <b>Return Value:</b> The method returns {@code null} in three cases:
     * <ul>
     * <li>The script is empty</li>
     * <li>The script explicitly returns null</li>
     * <li>The script execution fails (exception is logged)</li>
     * </ul>
     *
     * @param engine the script engine that is used
     * @return the value returned from the execution of the script, or null if the script is empty or fails
     */
    protected @Nullable Object eval(ScriptEngine engine) {
        if (script.isEmpty()) {
            return null;
        }
        try {
            if (compiledScript.isPresent()) {
                logger.debug("Executing pre-compiled script of rule with UID '{}'", ruleUID);
                return compiledScript.get().eval(engine.getContext());
            }
            logger.debug("Executing script of rule with UID '{}'", ruleUID);
            return engine.eval(script);
        } catch (ScriptException e) {
            // Enhanced error logging with script type and module information
            String scriptPreview = script.length() > 100 ? script.substring(0, 100) + "..." : script;
            logger.error("Script execution failed for rule '{}' (type: {}, module: {}): {}. Script preview: {}",
                    ruleUID, type, module.getId(), e.getMessage(), scriptPreview, logger.isDebugEnabled() ? e : null);
        }
        return null;
    }
}
