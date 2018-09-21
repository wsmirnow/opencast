/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.security.util;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.opencastproject.security.api.SecurityConstants.GLOBAL_ADMIN_ROLE;
import static org.opencastproject.security.api.SecurityConstants.GLOBAL_ANONYMOUS_USERNAME;
import static org.opencastproject.security.api.SecurityConstants.GLOBAL_CAPTURE_AGENT_ROLE;
import static org.opencastproject.util.data.Option.none;
import static org.opencastproject.util.data.Option.option;
import static org.opencastproject.util.data.Option.some;
import static org.opencastproject.util.data.Tuple.tuple;

import org.opencastproject.security.api.JaxbOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityConstants;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.util.ConfigurationException;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Function;
import org.opencastproject.util.data.Function0;
import org.opencastproject.util.data.Option;
import org.opencastproject.util.data.Tuple;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.ComponentContext;

import java.net.URL;
import java.util.regex.Pattern;

/** Opencast security helpers. */
public final class SecurityUtil {
  private static final Pattern SANITIZING_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");

  private SecurityUtil() {
  }

  /** The name of the key used to store the name of the system user in the global config. */
  public static final String PROPERTY_KEY_SYS_USER = "org.opencastproject.security.digest.user";

  /**
   * Run function <code>f</code> in the context described by the given organization and user.
   *
   * @return the function's outcome.
   */
  public static <A> A runAs(SecurityService sec, Organization org, User user, Function0<A> f) {
    final Organization prevOrg = sec.getOrganization();
    final User prevUser = prevOrg != null ? sec.getUser() : null;
    sec.setOrganization(org);
    sec.setUser(user);
    try {
      return f.apply();
    } finally {
      sec.setOrganization(prevOrg);
      sec.setUser(prevUser);
    }
  }

  /**
   * Create a system user for the given organization with global and organization local admin role. Get the
   * <code>systemUserName</code> from the global config where it is stored under {@link #PROPERTY_KEY_SYS_USER}. In an
   * OSGi environment this is typically done calling
   * <code>componentContext.getBundleContext().getProperty(PROPERTY_KEY_SYS_USER)</code>.
   *
   * @see #createSystemUser(org.osgi.service.component.ComponentContext, org.opencastproject.security.api.Organization)
   */
  public static User createSystemUser(String systemUserName, Organization org) {
    JaxbOrganization jaxbOrganization = JaxbOrganization.fromOrganization(org);
    return new JaxbUser(systemUserName, null, jaxbOrganization, new JaxbRole(GLOBAL_ADMIN_ROLE, jaxbOrganization),
            new JaxbRole(org.getAdminRole(), jaxbOrganization));
  }

  /**
   * Create the global anonymous user with the given organization.
   *
   * @param org
   *          the organization
   * @return the global anonymous user
   */
  public static User createAnonymousUser(Organization org) {
    JaxbOrganization jaxbOrganization = JaxbOrganization.fromOrganization(org);
    return new JaxbUser(GLOBAL_ANONYMOUS_USERNAME, null, jaxbOrganization, new JaxbRole(
            jaxbOrganization.getAnonymousRole(), jaxbOrganization));
  }

  /**
   * Create a system user for the given organization with global admin role. The system user name is fetched from the
   * global OSGi config.
   *
   * @see #createSystemUser(String, org.opencastproject.security.api.Organization)
   */
  public static User createSystemUser(ComponentContext cc, Organization org) {
    final String systemUserName = cc.getBundleContext().getProperty(PROPERTY_KEY_SYS_USER);
    return createSystemUser(systemUserName, org);
  }

  /**
   * Fetch the system user name from the configuration.
   *
   * @see #PROPERTY_KEY_SYS_USER
   */
  public static String getSystemUserName(ComponentContext cc) {
    final String systemUserName = cc.getBundleContext().getProperty(PROPERTY_KEY_SYS_USER);
    if (systemUserName != null) {
      return systemUserName;
    } else {
      throw new ConfigurationException(
              "An Opencast installation always needs a system user name. Please configure one under the key "
                      + PROPERTY_KEY_SYS_USER);
    }
  }

  /** Get the organization <code>orgId</code>. */
  public static Option<Organization> getOrganization(OrganizationDirectoryService orgDir, String orgId) {
    try {
      return some(orgDir.getOrganization(orgId));
    } catch (NotFoundException e) {
      return none();
    }
  }

  /** Get a user of a certain organization by its ID. */
  public static Option<User> getUserOfOrganization(SecurityService sec, OrganizationDirectoryService orgDir,
          String orgId, UserDirectoryService userDir, String userId) {
    final Organization prevOrg = sec.getOrganization();
    try {
      final Organization org = orgDir.getOrganization(orgId);
      sec.setOrganization(org);
      return option(userDir.loadUser(userId));
    } catch (NotFoundException e) {
      return none();
    } finally {
      sec.setOrganization(prevOrg);
    }
  }

  /**
   * Get a user and an organization. Only returns something if both elements can be determined.
   */
  public static Option<Tuple<User, Organization>> getUserAndOrganization(SecurityService sec,
          OrganizationDirectoryService orgDir, String orgId, UserDirectoryService userDir, String userId) {
    final Organization prevOrg = sec.getOrganization();
    try {
      final Organization org = orgDir.getOrganization(orgId);
      sec.setOrganization(org);
      return option(userDir.loadUser(userId)).fmap(new Function<User, Tuple<User, Organization>>() {
        @Override
        public Tuple<User, Organization> apply(User user) {
          return tuple(user, org);
        }
      });
    } catch (NotFoundException e) {
      return none();
    } finally {
      sec.setOrganization(prevOrg);
    }
  }

  /** Extract hostname and port number from a URL. */
  public static Tuple<String, Integer> hostAndPort(URL url) {
    return tuple(StringUtils.strip(url.getHost(), "/"), url.getPort());
  }

  /**
   * Check if the current user has access to the capture agent with the given id.
   * @param agentId
   *           The agent id to check.
   * @throws UnauthorizedException
   *           If the user doesn't have access.
   */
  public static void checkAgentAccess(final SecurityService securityService, final String agentId)
      throws UnauthorizedException {
    if (isBlank(agentId)) {
      return;
    }
    final User user = securityService.getUser();
    if (user.hasRole(SecurityConstants.GLOBAL_ADMIN_ROLE) || user.hasRole(user.getOrganization().getAdminRole())) {
      return;
    }
    if (!user.hasRole(SecurityUtil.getCaptureAgentRole(agentId))) {
      throw new UnauthorizedException(user, "schedule");
    }
  }

  private static String sanitizeCaName(final String ca) {
    return SANITIZING_PATTERN.matcher(ca).replaceAll("").toUpperCase();
  }

  /**
   * Get the role name of the role required to access the capture agent with the given agent id.
   *
   * @param
   *     agentId The id of the agent to get the role for.
   * @return
   *     The role name.
   */
  public static String getCaptureAgentRole(final String agentId) {
    return GLOBAL_CAPTURE_AGENT_ROLE + "_" + sanitizeCaName(agentId);
  }
}
