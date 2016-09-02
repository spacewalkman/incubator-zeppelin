package org.apache.zeppelin.ticket;

import org.apache.shiro.authc.UsernamePasswordToken;

/**
 * 带参赛队名字的Token
 */
public class UserPasswordGroupToken extends UsernamePasswordToken {

  private String group;

  public UserPasswordGroupToken(String userName, String password, String group) {
    super(userName, password);
    this.setGroup(group);
  }

  /**
   * 参赛队
   */
  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }
}
