package org.apache.zeppelin.rest.message;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * principal and roles send with REST message
 */
public abstract class PrincipalAndRoles {
  String principal;
  Set<String> roles;
  String ticket;

  public String getPrincipal() {
    return principal;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public String getTicket() {
    return ticket;
  }

  public Set<String> getUserAndRoles() {
    LinkedHashSet<String> userAnRoles = new LinkedHashSet<>();
    userAnRoles.add(principal);

    userAnRoles.addAll(getRoles());

    return userAnRoles;
  }
}
