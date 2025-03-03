/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa.autoassign;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.PersistenceException;

import org.eclipse.hawkbit.ContextAware;
import org.eclipse.hawkbit.exception.AbstractServerRtException;
import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.TargetFilterQueryManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.jpa.configuration.Constants;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetFilterQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks if targets need a new distribution set (DS) based on the target filter
 * queries and assigns the new DS when necessary. First all target filter
 * queries are listed. For every target filter query (TFQ) the auto assign DS is
 * retrieved. All targets get listed per target filter query, that match the TFQ
 * and that don't have the auto assign DS in their action history.
 */
public class AutoAssignChecker extends AbstractAutoAssignExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoAssignChecker.class);

    private final TargetManagement targetManagement;

    /**
     * Instantiates a new auto assign checker
     *
     * @param targetFilterQueryManagement
     *            to get all target filter queries
     * @param targetManagement
     *            to get targets
     * @param deploymentManagement
     *            to assign distribution sets to targets
     * @param transactionManager
     *            to run transactions
     * @param contextAware
     *            to handle the context
     */
    public AutoAssignChecker(final TargetFilterQueryManagement targetFilterQueryManagement,
            final TargetManagement targetManagement, final DeploymentManagement deploymentManagement,
            final PlatformTransactionManager transactionManager, final ContextAware contextAware) {
        super(targetFilterQueryManagement, deploymentManagement, transactionManager, contextAware);
        this.targetManagement = targetManagement;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAllTargets() {
        LOGGER.debug("Auto assign check call for tenant {} started", getContextAware().getCurrentTenant());
        forEachFilterWithAutoAssignDS(this::checkByTargetFilterQueryAndAssignDS);
        LOGGER.debug("Auto assign check call for tenant {} finished", getContextAware().getCurrentTenant());
    }

    @Override
    public void checkSingleTarget(String controllerId) {
        LOGGER.debug("Auto assign check call for tenant {} and device {} started", getContextAware().getCurrentTenant(),
                controllerId);
        forEachFilterWithAutoAssignDS(filter -> checkForDevice(controllerId, filter));
        LOGGER.debug("Auto assign check call for tenant {} and device {} finished", getContextAware().getCurrentTenant(),
                controllerId);
    }

    /**
     * Fetches the distribution set, gets all controllerIds and assigns the DS to
     * them. Catches PersistenceException and own exceptions derived from
     * AbstractServerRtException
     *
     * @param targetFilterQuery
     *            the target filter query
     */
    private void checkByTargetFilterQueryAndAssignDS(final TargetFilterQuery targetFilterQuery) {
        LOGGER.debug("Auto assign check call for tenant {} and target filter query id {} started",
                getContextAware().getCurrentTenant(), targetFilterQuery.getId());
        try {
            int count;
            do {
                final List<String> controllerIds = targetManagement
                        .findByTargetFilterQueryAndNonDSAndCompatibleAndUpdatable(
                                PageRequest.of(0, Constants.MAX_ENTRIES_IN_STATEMENT),
                                targetFilterQuery.getAutoAssignDistributionSet().getId(), targetFilterQuery.getQuery())
                        .getContent().stream().map(Target::getControllerId).toList();
                LOGGER.debug(
                        "Retrieved {} auto assign targets for tenant {} and target filter query id {}, starting with assignment",
                        controllerIds.size(), getContextAware().getCurrentTenant(), targetFilterQuery.getId());

                count = runTransactionalAssignment(targetFilterQuery, controllerIds);
                LOGGER.debug(
                        "Assignment for {} auto assign targets for tenant {} and target filter query id {} finished",
                        controllerIds.size(), getContextAware().getCurrentTenant(), targetFilterQuery.getId());
            } while (count == Constants.MAX_ENTRIES_IN_STATEMENT);
        } catch (final PersistenceException | AbstractServerRtException e) {
            LOGGER.error("Error during auto assign check of target filter query id {}", targetFilterQuery.getId(), e);
        }
        LOGGER.debug("Auto assign check call for tenant {} and target filter query id {} finished",
                getContextAware().getCurrentTenant(), targetFilterQuery.getId());
    }

    private void checkForDevice(final String controllerId, final TargetFilterQuery targetFilterQuery) {
        LOGGER.debug("Auto assign check call for tenant {} and target filter query id {} for device {} started",
                getContextAware().getCurrentTenant(), targetFilterQuery.getId(), controllerId);
        try {
            final boolean controllerIdMatches = targetManagement.isTargetMatchingQueryAndDSNotAssignedAndCompatibleAndUpdatable(
                    controllerId, targetFilterQuery.getAutoAssignDistributionSet().getId(),
                    targetFilterQuery.getQuery());

            if (controllerIdMatches) {
                runTransactionalAssignment(targetFilterQuery, Collections.singletonList(controllerId));
            }

        } catch (final PersistenceException | AbstractServerRtException e) {
            LOGGER.error("Error during auto assign check of target filter query id {}", targetFilterQuery.getId(), e);
        }
        LOGGER.debug("Auto assign check call for tenant {} and target filter query id {} finished",
                getContextAware().getCurrentTenant(), targetFilterQuery.getId());
    }
}
