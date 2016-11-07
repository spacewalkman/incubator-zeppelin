package org.apache.zeppelin.realm;

/**
 * 处理稻田REST验证接口中含有stateCode和retMessage，将UserProfile作为嵌套对象的问题
 */
public class RestAuthResponse {
  private int stateCode;
  private String retMessage;

  private UserProfile data;

  public int getStateCode() {
    return stateCode;
  }

  public void setStateCode(int stateCode) {
    this.stateCode = stateCode;
  }

  public String getRetMessage() {
    return retMessage;
  }

  public void setRetMessage(String retMessage) {
    this.retMessage = retMessage;
  }

  public UserProfile getData() {
    return data;
  }

  public void setData(UserProfile data) {
    this.data = data;
  }
}