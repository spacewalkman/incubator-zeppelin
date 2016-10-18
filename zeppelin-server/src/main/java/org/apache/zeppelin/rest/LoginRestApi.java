/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.rest;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.ShiroNotebookAuthorization;
import org.apache.zeppelin.realm.UserProfile;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.ticket.TicketUserNameToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 登录验证，使用稻田REST验证接口，zeppelin将成功验证的<UserProfile,Subject>缓存
 */
@Path("/login")
@Produces("application/json")
public class LoginRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(LoginRestApi.class);


  @XmlRootElement
  public static class LoginBean {
    @XmlElement
    public String ticket;
    @XmlElement
    public int serverIndex;
  }

  /**
   * Required by Swagger.
   */
  public LoginRestApi() {
    super();
  }


  /**
   * Post Login
   * Returns userName & password
   * for anonymous access, username is always anonymous.
   * After getting this ticket, access through websockets become safe
   *
   * @return 200 response
   */
  @POST
  @Consumes({MediaType.APPLICATION_JSON})
  @ZeppelinApi
  public Response postLogin(LoginBean loginBean) {
    JsonResponse response = null;

    Subject subject = TicketContainer.instance.getCachedSubject(loginBean.ticket);
    if (subject == null) {
      subject = org.apache.shiro.SecurityUtils.getSubject();
    }

    if (!subject.isAuthenticated()) {
      try {
        TicketUserNameToken token = new TicketUserNameToken(loginBean.ticket, loginBean.serverIndex);
        //token.setRememberMe(true);

        Date startTime = new Date();
        subject.login(token);
        Date endTime = new Date();
        LOG.debug("REST身份鉴别接口耗时:{}秒", (endTime.getTime() - startTime.getTime()) / 1000.0);

        PrincipalCollection principalCollection = subject.getPrincipals();
        UserProfile userProfile = (UserProfile) principalCollection.getPrimaryPrincipal();

        TicketContainer.instance.putSubject(userProfile.getTicket(), subject);

        //创建user_role,role_permission等，保证用户经过RestAuth验证通过的用户，授权能过
        try {
          //TODO:这里与zeppelinServer构造函数中实例化的NotebookAuthorizationAdaptor的子类保持一致，目前没有处理自动初始化子类的问题
          ShiroNotebookAuthorization notebookAuthorization = ShiroNotebookAuthorization.getInstance();
          notebookAuthorization.addGroup(userProfile.getTeam());//创建组
          if (userProfile.isLeader()) {
            notebookAuthorization.addGroupLeader(userProfile.getTeam(), userProfile.getUserName());//创建组长
          } else {
            notebookAuthorization.addGroupMember(userProfile.getTeam(), userProfile.getUserName());//创建组成员
          }
        } catch (PropertyVetoException e) {
          LOG.error("创建授权的DataSource失败", e);
        }

        response = buildOKResponse(userProfile);
        //if no exception, that's it, we're done!
      } catch (UnknownAccountException uae) {
        //username wasn't in the system, show them an error message?
        LOG.error("username and password doesn't match: ", uae);
      } catch (IncorrectCredentialsException ice) {
        //password didn't match, try again?
        LOG.error("username and password doesn't match: ", ice);
      } catch (LockedAccountException lae) {
        //account for that username is locked - can't login.  Show them a message?
        LOG.error("Exception in login: ", lae);
      } catch (AuthenticationException ae) {
        //unexpected condition - error?
        LOG.error("Exception in login: ", ae);
      }

      if (response == null) {
        response = new JsonResponse(Response.Status.FORBIDDEN, "未授权的用户", "");
      }
    } else {//TODO:如果zeppelin缓存命中，则直接通过验证，这里需要增加超时清理机制
      UserProfile userProfile = (UserProfile) (subject.getPrincipal());
      response = buildOKResponse(userProfile);
    }

    LOG.warn(response.toString());
    return response.build();
  }

  /**
   * 构造通过Authentication的response
   */
  private JsonResponse buildOKResponse(UserProfile userProfile) {
    Map<String, String> data = new HashMap<>();
    data.put("principal", userProfile.getUserName());
    data.put("ticket", userProfile.getTicket());
    data.put("group", userProfile.getTeam());
    data.put("projectId", userProfile.getProjectId()); //同一个人可以参加不同的队伍，参加多个比赛，此时用户应该根据"队伍"被分配不同的机器上

    JsonResponse response = new JsonResponse(Response.Status.OK, "", data);
    return response;
  }

  @POST
  @Path("logout")
  @ZeppelinApi
  public Response logout() {
    JsonResponse response;
    Subject currentUser = org.apache.shiro.SecurityUtils.getSubject();
    currentUser.logout();

    response = new JsonResponse(Response.Status.UNAUTHORIZED, "", "");
    LOG.warn(response.toString());
    return response.build();
  }

}
