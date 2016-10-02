package org.apache.zeppelin.realm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.zeppelin.ticket.TicketUserNameToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * 用来通过REST向"稻田"平台请求验证token和userName是否一致的问题，期望"稻田"平台返回[userName,ticket,ip,group,projet]元组
 */
public class RestAuthRealm extends AuthenticatingRealm {
  private static final Logger LOG = LoggerFactory.getLogger(RestAuthRealm.class);

  private String authRestEndPoint;

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(
          AuthenticationToken token) throws AuthenticationException {
    TicketUserNameToken ticketUserNameToken = (TicketUserNameToken) token;
    if (ticketUserNameToken == null) {
      throw new IllegalStateException("token不是TicketUserNameToken实例");
    }

    UserProfile userProfile = null;
    try {
      userProfile = this.requestUserProfile(ticketUserNameToken.getTicket(), ticketUserNameToken.getUserName());
    } catch (IOException e) {
      LOG.error("稻田REST身份鉴别接口失效", e);
      throw new AuthenticationException("稻田REST身份鉴别接口失效", e);
    }

    //TODO：这里只有用户的principal，没有用户credentials
    //Object[] principals = {userProfile.getUserName(), userProfile.getTicket(), userProfile.getTeam(), userProfile.getIps(), userProfile.getProjectIds()};
    SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(userProfile, null, getName());
    return info;
  }

  @Override
  protected void assertCredentialsMatch(AuthenticationToken token,
                                        AuthenticationInfo info) throws AuthenticationException {
    return;
  }

  /**
   * 通过httpClient调用稻田提供的REST接口，传入ticket鉴定用户
   */
  public UserProfile requestUserProfile(String ticket, String userName) throws IOException {
    CloseableHttpClient httpclient = HttpClients.createDefault();
    URI uri = null;

    try {
      uri = new URIBuilder(authRestEndPoint).setParameter("ticket", ticket).setParameter("userName", userName).build();
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }

    HttpGet httpget = new HttpGet(uri);

    ResponseHandler<UserProfile> rh = new ResponseHandler<UserProfile>() {

      @Override
      public UserProfile handleResponse(
              final HttpResponse response) throws IOException {
        StatusLine statusLine = response.getStatusLine();
        HttpEntity entity = response.getEntity();
        if (statusLine.getStatusCode() >= 300) {
          throw new HttpResponseException(
                  statusLine.getStatusCode(),
                  statusLine.getReasonPhrase());
        }
        if (entity == null) {
          throw new ClientProtocolException("Response contains no content");
        }

        Gson gson = new GsonBuilder().create();
        ContentType contentType = ContentType.getOrDefault(entity);
        Charset charset = contentType.getCharset();
        Reader reader = new InputStreamReader(entity.getContent(), charset);
        return gson.fromJson(reader, UserProfile.class);
      }
    };

    UserProfile uerProfile = httpclient.execute(httpget, rh);
    httpclient.close();
    return uerProfile;
  }

  @Override
  public boolean supports(AuthenticationToken token) {
    if (token instanceof TicketUserNameToken) {
      return true;
    }

    return false;
  }

  /**
   * 稻田REST身份验证地址
   */
  public String getAuthRestEndPoint() {
    return authRestEndPoint;
  }

  public void setAuthRestEndPoint(String authRestEndPoint) {
    this.authRestEndPoint = authRestEndPoint;
  }
}
