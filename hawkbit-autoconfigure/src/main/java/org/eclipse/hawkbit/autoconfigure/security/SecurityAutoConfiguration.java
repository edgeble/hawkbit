/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.autoconfigure.security;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.ContextAware;
import org.eclipse.hawkbit.autoconfigure.security.MultiUserProperties.User;
import org.eclipse.hawkbit.im.authentication.PermissionService;
import org.eclipse.hawkbit.security.DdiSecurityProperties;
import org.eclipse.hawkbit.security.InMemoryUserAuthoritiesResolver;
import org.eclipse.hawkbit.security.HawkbitSecurityProperties;
import org.eclipse.hawkbit.security.SecurityContextSerializer;
import org.eclipse.hawkbit.security.SecurityContextTenantAware;
import org.eclipse.hawkbit.security.SecurityTokenGenerator;
import org.eclipse.hawkbit.security.SpringSecurityAuditorAware;
import org.eclipse.hawkbit.security.SystemSecurityContext;
import org.eclipse.hawkbit.tenancy.TenantAware;
import org.eclipse.hawkbit.tenancy.UserAuthoritiesResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.util.CollectionUtils;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for security.
 */
@Configuration
@EnableConfigurationProperties({ SecurityProperties.class, DdiSecurityProperties.class, HawkbitSecurityProperties.class,
        MultiUserProperties.class })
public class SecurityAutoConfiguration {

    /**
     * Creates a {@link ContextAware} (hence {@link TenantAware}) bean based on the given
     * {@link UserAuthoritiesResolver} and {@link SecurityContextSerializer}.
     *
     * @param authoritiesResolver
     *            The user authorities/roles resolver
     * @param securityContextSerializer
     *            The security context serializer.
     *
     * @return the {@link ContextAware} singleton bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public ContextAware contextAware(
            final UserAuthoritiesResolver authoritiesResolver,
            @Autowired(required = false) final SecurityContextSerializer securityContextSerializer) {
        return new SecurityContextTenantAware(authoritiesResolver, securityContextSerializer);
    }

    /**
     * Creates a {@link UserAuthoritiesResolver} bean that is responsible for
     * resolving user authorities/roles.
     *
     * @param securityProperties
     *            The Spring {@link SecurityProperties} for the security user
     * @param multiUserProperties
     *            The {@link MultiUserProperties} for the managed users
     *
     * @return an {@link InMemoryUserAuthoritiesResolver} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public UserAuthoritiesResolver inMemoryAuthoritiesResolver(final SecurityProperties securityProperties,
            final MultiUserProperties multiUserProperties) {
        final List<User> multiUsers = multiUserProperties.getUsers();
        final Map<String, List<String>> usersToPermissions;
        if (!CollectionUtils.isEmpty(multiUsers)) {
            usersToPermissions = multiUsers.stream().collect(Collectors.toMap(User::getUsername, User::getPermissions));
        } else {
            usersToPermissions = Collections.singletonMap(securityProperties.getUser().getName(),
                    securityProperties.getUser().getRoles());
        }
        return new InMemoryUserAuthoritiesResolver(usersToPermissions);
    }

    /**
     * @return permission service to check if current user has the necessary
     *         permissions.
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionService permissionService() {
        return new PermissionService();
    }

    /**
     * Creates the auditor aware.
     * 
     * @return the spring security auditor aware
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditorAware<String> auditorAware() {
        return new SpringSecurityAuditorAware();
    }

    /**
     * @param tenantAware
     *            singleton bean
     * @return tenantAware {@link SystemSecurityContext}
     */
    @Bean
    @ConditionalOnMissingBean
    public SystemSecurityContext systemSecurityContext(final TenantAware tenantAware) {
        return new SystemSecurityContext(tenantAware);
    }

    /**
     * @return {@link SecurityTokenGenerator} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityTokenGenerator securityTokenGenerator() {
        return new SecurityTokenGenerator();
    }

    /**
     * @return {@link AuthenticationSuccessHandler} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new SimpleUrlAuthenticationSuccessHandler();
    }

    /**
     * @return {@link LogoutHandler} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public LogoutHandler logoutHandler() {
        return new SecurityContextLogoutHandler();
    }

    /**
     * @return {@link LogoutSuccessHandler} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public LogoutSuccessHandler logoutSuccessHandler() {
        final SimpleUrlLogoutSuccessHandler simpleUrlLogoutSuccessHandler = new SimpleUrlLogoutSuccessHandler();
        simpleUrlLogoutSuccessHandler.setTargetUrlParameter("login");
        return simpleUrlLogoutSuccessHandler;
    }

}
