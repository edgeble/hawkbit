/**
 * Copyright (c) 2023 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa;

import java.util.List;
import java.util.concurrent.locks.Lock;

import org.eclipse.hawkbit.ContextAware;
import org.eclipse.hawkbit.repository.RolloutExecutor;
import org.eclipse.hawkbit.repository.RolloutHandler;
import org.eclipse.hawkbit.repository.RolloutManagement;
import org.eclipse.hawkbit.repository.jpa.utils.DeploymentHelper;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * JPA implementation of {@link RolloutHandler}.
 */
public class JpaRolloutHandler implements RolloutHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(JpaRolloutHandler.class);

    private final TenantAware tenantAware;
    private final RolloutManagement rolloutManagement;
    private final RolloutExecutor rolloutExecutor;
    private final LockRegistry lockRegistry;
    private final PlatformTransactionManager txManager;
    private final ContextAware contextAware;

    /**
     * Constructor
     * 
     * @param tenantAware
     *            the {@link TenantAware} bean holding the tenant information
     * @param rolloutManagement
     *            to fetch rollout related information from the datasource
     * @param rolloutExecutor
     *            to trigger executions for a specific rollout
     * @param lockRegistry
     *            to lock processes
     * @param txManager
     *            transaction manager interface
     */
    public JpaRolloutHandler(final TenantAware tenantAware, final RolloutManagement rolloutManagement,
            final RolloutExecutor rolloutExecutor, final LockRegistry lockRegistry,
            final PlatformTransactionManager txManager,
            final ContextAware contextAware) {
        this.tenantAware = tenantAware;
        this.rolloutManagement = rolloutManagement;
        this.rolloutExecutor = rolloutExecutor;
        this.lockRegistry = lockRegistry;
        this.txManager = txManager;
        this.contextAware = contextAware;
    }

    @Override
    public void handleAll() {
        final List<Long> rollouts = rolloutManagement.findActiveRollouts();

        if (rollouts.isEmpty()) {
            return;
        }

        final String handlerId = createRolloutLockKey(tenantAware.getCurrentTenant());
        final Lock lock = lockRegistry.obtain(handlerId);
        if (!lock.tryLock()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Could not perform lock {}", lock);
            }
            return;
        }

        try {
            LOGGER.trace("Trigger handling {} rollouts.", rollouts.size());
            rollouts.forEach(rolloutId -> handleRolloutInNewTransaction(rolloutId, handlerId));
        } finally {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Unlock lock {}", lock);
            }
            lock.unlock();
        }
    }

    private static String createRolloutLockKey(final String tenant) {
        return tenant + "-rollout";
    }

    // run in a tenant context, i.e. contextAware.getCurrentTenant() returns the tenant
    // the rollout is made for
    private void handleRolloutInNewTransaction(final long rolloutId, final String handlerId) {
        DeploymentHelper.runInNewTransaction(txManager, handlerId + "-" + rolloutId, status -> {
            rolloutManagement.get(rolloutId).ifPresentOrElse(
                    rollout -> {
                        rollout.getAccessControlContext().ifPresentOrElse(
                            context -> // has stored context - executes it with it
                                contextAware.runInContext(
                                    context,
                                    () -> rolloutExecutor.execute(rollout)),
                            () -> // has no stored context - executes it in the tenant & user scope
                                contextAware.runAsTenantAsUser(
                                    contextAware.getCurrentTenant(),
                                    rollout.getCreatedBy(), () -> {
                                        rolloutExecutor.execute(rollout);
                                        return null;
                                    })
                        );
                    },
                    () -> LOGGER.error("Could not retrieve rollout with id {}. Will not continue with execution.",
                            rolloutId));
            return 0L;
        });
    }
}
