/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.hawkbit.cache.DownloadIdCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * Extracts download or upload id from the request URI secruity token and set
 * the security context.
 * 
 *
 *
 */
public class HttpDownloadAuthenticationFilter extends AbstractPreAuthenticatedProcessingFilter {

    public static final String REQUEST_ID_REGEX_PATTERN = ".*\\/downloadId\\/.*";
    private static final Logger LOG = LoggerFactory.getLogger(HttpDownloadAuthenticationFilter.class);

    private final Pattern pattern;
    private final DownloadIdCache downloadIdCache;

    /**
     * Constructor.
     * 
     * @param downloadIdCache
     *            the cache
     */
    public HttpDownloadAuthenticationFilter(final DownloadIdCache downloadIdCache) {
        this.downloadIdCache = downloadIdCache;
        this.pattern = Pattern.compile(REQUEST_ID_REGEX_PATTERN);

    }

    private Object getDownloadByUri(final String requestURI) {
        final Matcher matcher = pattern.matcher(requestURI);
        if (!matcher.matches()) {
            return null;
        }
        LOG.debug("retrieving id from URI request {}", requestURI);
        final String[] groups = requestURI.split("\\/");
        final String id = groups[groups.length - 1];
        if (id == null) {
            return null;
        }
        return downloadIdCache.get(id);
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
        return getDownloadByUri(request.getRequestURI());
    }

    @Override
    protected Object getPreAuthenticatedCredentials(final HttpServletRequest request) {
        return getDownloadByUri(request.getRequestURI());
    }
}
