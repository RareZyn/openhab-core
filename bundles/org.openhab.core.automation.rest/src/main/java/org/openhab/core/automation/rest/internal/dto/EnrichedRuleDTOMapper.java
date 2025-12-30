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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.automation.ManagedRuleProvider;
import org.openhab.core.automation.Rule;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.dto.RuleDTOMapper;

/**
 * Utility class for converting Rule domain objects to EnrichedRuleDTO data transfer objects.
 *
 * <p>
 * This mapper extends {@link RuleDTOMapper} to populate additional runtime information:
 * <ul>
 * <li>Current rule status from the rule engine</li>
 * <li>Editability flag based on whether the rule is managed or file-based</li>
 * </ul>
 * </p>
 *
 * @author Markus Rathgeb - Initial contribution
 * @author Kai Kreuzer - added editable field
 */
@NonNullByDefault
public class EnrichedRuleDTOMapper extends RuleDTOMapper {

    /**
     * Converts a Rule to an EnrichedRuleDTO with runtime state.
     *
     * <p>
     * This method:
     * <ol>
     * <li>Copies all base rule properties (UID, name, triggers, etc.)</li>
     * <li>Queries the rule engine for current status information</li>
     * <li>Checks if the rule is managed (editable) or file-based (read-only)</li>
     * </ol>
     * </p>
     *
     * @param rule the rule to convert
     * @param ruleEngine the rule manager to query for status
     * @param managedRuleProvider the provider to check if rule is managed
     * @return enriched DTO with base properties and runtime state
     */
    public static EnrichedRuleDTO map(final Rule rule, final RuleManager ruleEngine,
            final ManagedRuleProvider managedRuleProvider) {
        final EnrichedRuleDTO enrichedRuleDto = new EnrichedRuleDTO();
        fillProperties(rule, enrichedRuleDto);
        enrichedRuleDto.status = ruleEngine.getStatusInfo(rule.getUID());
        enrichedRuleDto.editable = managedRuleProvider.get(rule.getUID()) != null;
        return enrichedRuleDto;
    }
}
