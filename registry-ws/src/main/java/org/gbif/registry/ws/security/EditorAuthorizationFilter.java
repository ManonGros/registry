package org.gbif.registry.ws.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gbif.registry.ws.security.SecurityContextCheck.checkIsNotAdmin;
import static org.gbif.registry.ws.security.SecurityContextCheck.checkIsNotEditor;

/**
 * For requests authenticated with a REGISTRY_EDITOR role two levels of authorization need to be passed.
 * First of all any resource method is required to have the role included in the RolesAllowed annotation.
 * Secondly this request filter needs to be passed for POST/PUT/DELETE requests that act on existing and UUID identified
 * main registry entities such as dataset, organization, node, installation and network.
 * In order to do authorization the key of these entities is extracted from the requested path.
 * An exception to this is the create method for those main entities themselves.
 * This is covered by the BaseNetworkEntityResource.create() method directly.
 */
public class EditorAuthorizationFilter implements ContainerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(EditorAuthorizationFilter.class);

  private static final String ENTITY_KEY = "^/?%s/([a-f0-9-]+).*";
  private static final Pattern NODE_NETWORK_PATTERN = Pattern.compile(String.format(ENTITY_KEY, "(?:network|node)"));
  private static final Pattern ORGANIZATION_PATTERN = Pattern.compile(String.format(ENTITY_KEY, "organization"));
  private static final Pattern DATASET_PATTERN = Pattern.compile(String.format(ENTITY_KEY, "dataset"));
  private static final Pattern INSTALLATION_PATTERN = Pattern.compile(String.format(ENTITY_KEY, "installation"));

  private final EditorAuthorizationService userAuthService;

  @Context
  private SecurityContext secContext;

  @Inject
  public EditorAuthorizationFilter(EditorAuthorizationService userAuthService) {
    this.userAuthService = userAuthService;
  }

  @Override
  public ContainerRequest filter(ContainerRequest request) {
    // only verify non GET methods with an authenticated REGISTRY_EDITOR
    // all other roles are taken care by simple JSR250 annotations on the resource methods
    Principal user = secContext.getUserPrincipal();

    String path = request.getPath().toLowerCase();

    if (isNotGetOrOptionsRequest(request) && checkRequestRequiresEditorValidation(path)) {
      // user must be present in security context
      if (user == null || user.getName() == null) {
        throw new WebApplicationException(Response.Status.FORBIDDEN);
      }

      // admin is allowed to perform operation, just skip block
      if (checkIsNotAdmin(secContext)) {
        // user must have editor rights
        if (checkIsNotEditor(secContext)) {
          throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        // check rights on specific entities
        try {
          checkOrganization(user, path);
          checkDataset(user, path);
          checkInstallation(user, path);
          checkNodeNetwork(user, path);
        } catch (IllegalArgumentException e) {
          // no valid UUID, do nothing as it should not be a valid request anyway
        }
      }
    }

    return request;
  }

  private void checkNodeNetwork(Principal user, String path) {
    Matcher m = NODE_NETWORK_PATTERN.matcher(path);
    if (m.find()) {
      if (!userAuthService.allowedToModifyEntity(user, UUID.fromString(m.group(1)))) {
        LOG.warn("User {} is not allowed to modify node/network {}", user.getName(), m.group(1));
        throw new WebApplicationException(Response.Status.FORBIDDEN);
      } else {
        LOG.debug("User {} is allowed to modify node/network {}", user.getName(), m.group(1));
      }
    }
  }

  private void checkInstallation(Principal user, String path) {
    Matcher m = INSTALLATION_PATTERN.matcher(path);
    if (m.find()) {
      if (!userAuthService.allowedToModifyInstallation(user, UUID.fromString(m.group(1)))) {
        LOG.warn("User {} is not allowed to modify installation {}", user.getName(), m.group(1));
        throw new WebApplicationException(Response.Status.FORBIDDEN);
      } else {
        LOG.debug("User {} is allowed to modify installation {}", user.getName(), m.group(1));
      }
    }
  }

  private void checkDataset(Principal user, String path) {
    Matcher m = DATASET_PATTERN.matcher(path);
    if (m.find()) {
      if (!userAuthService.allowedToModifyDataset(user, UUID.fromString(m.group(1)))) {
        LOG.warn("User {} is not allowed to modify dataset {}", user.getName(), m.group(1));
        throw new WebApplicationException(Response.Status.FORBIDDEN);
      } else {
        LOG.debug("User {} is allowed to modify dataset {}", user.getName(), m.group(1));
      }
    }
  }

  private void checkOrganization(Principal user, String path) {
    Matcher m = ORGANIZATION_PATTERN.matcher(path);
    if (m.find()) {
      if (!userAuthService.allowedToModifyOrganization(user, UUID.fromString(m.group(1)))) {
        LOG.warn("User {} is not allowed to modify organization {}", user.getName(), m.group(1));
        throw new WebApplicationException(Response.Status.FORBIDDEN);
      } else {
        LOG.debug("User {} is allowed to modify organization {}", user.getName(), m.group(1));
      }
    }
  }

  private boolean checkRequestRequiresEditorValidation(String path) {
    boolean isBaseNetworkEntityResource = ORGANIZATION_PATTERN.matcher(path).matches()
      || DATASET_PATTERN.matcher(path).matches()
      || INSTALLATION_PATTERN.matcher(path).matches()
      || NODE_NETWORK_PATTERN.matcher(path).matches();

    // exclude endorsement and machine tag from validation
    boolean isNotEndorsement = !path.contains("endorsement");
    boolean isNotMachineTag = !path.contains("machineTag");

    return isBaseNetworkEntityResource && isNotEndorsement && isNotMachineTag;
  }

  private boolean isNotGetOrOptionsRequest(ContainerRequest request) {
    return !"GET".equals(request.getMethod())
      && !"OPTIONS".equals(request.getMethod());
  }

  @VisibleForTesting
  protected void setSecContext(SecurityContext secContext) {
    this.secContext = secContext;
  }
}
