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
package org.openhab.core.automation.rest.internal.dto;

import org.openhab.core.automation.RuleStatusInfo;
import org.openhab.core.automation.dto.RuleDTO;

/**
 * Data transfer object for rules enriched with runtime state and editability information.
 *
 * <p>
 * Extends {@link RuleDTO} with additional fields that reflect the current state
 * of a rule in the system:
 * <ul>
 * <li><b>status</b> - The current runtime status (IDLE, RUNNING, etc.) and detailed status info</li>
 * <li><b>editable</b> - Whether the rule can be modified (true for managed rules, false for file-based)</li>
 * </ul>
 * </p>
 *
 * <p>
 * This DTO is used for REST API responses that need to expose both the static
 * configuration and dynamic runtime state of rules.
 * </p>
 *
 * @author Kai Kreuzer - Initial contribution
 */
public class EnrichedRuleDTO extends RuleDTO {

    /**
     * The current runtime status of the rule, including state (IDLE, RUNNING, etc.),
     * status detail (NONE, DISABLED, etc.), and optional description.
     */
    public RuleStatusInfo status;

    /**
     * Indicates whether the rule can be modified through the API.
     * True for managed rules (stored in JSON DB), false for file-based rules.
     */
    public Boolean editable;
}
