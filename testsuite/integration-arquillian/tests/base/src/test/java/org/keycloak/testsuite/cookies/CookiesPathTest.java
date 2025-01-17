package org.keycloak.testsuite.cookies;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpCoreContext;
import org.hamcrest.Matchers;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.ActionURIUtils;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.util.RealmBuilder;
import org.keycloak.testsuite.util.UserBuilder;
import org.openqa.selenium.Cookie;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.keycloak.testsuite.util.ServerURLs.AUTH_SERVER_HOST;


/**
 * @author <a href="mailto:mkanis@redhat.com">Martin Kanis</a>
 */
public class CookiesPathTest extends AbstractKeycloakTest {
    @Page
    protected LoginPage loginPage;

    public static final String KC_RESTART = "KC_RESTART";

    private CloseableHttpClient httpClient = null;

    private static final List<String> KEYCLOAK_COOKIE_NAMES = Arrays.asList("KC_RESTART", "AUTH_SESSION_ID", "KEYCLOAK_IDENTITY", "KEYCLOAK_SESSION");

    @Before
    public void beforeCookiesPathTest() {
        createAppClientInRealm("foo");
        createAppClientInRealm("foobar");
    }

    @After
    public void afterCookiesPathTest() throws IOException {
        if (httpClient != null) httpClient.close();

        // Setting back default oauth values
        oauth.realm("test");
        oauth.redirectUri(oauth.APP_AUTH_ROOT);
    }

    @Test
    public void testCookiesPath() {
        // navigate to "/realms/foo/account" and them remove cookies in the browser for the current path
        // first access to the path means there are no cookies being sent
        // we are redirected to login page and Keycloak sets cookie's path to "/auth/realms/foo/"
        navigateToLoginPage("foo");
        driver.manage().deleteAllCookies();

        Assert.assertTrue("There shouldn't be any cookies sent!", driver.manage().getCookies().isEmpty());

        // refresh the page and cookies are sent within the request
        driver.navigate().refresh();

        Set<Cookie> cookies = driver.manage().getCookies();
        Assert.assertTrue("There should be cookies sent!", cookies.size() > 0);
        // check cookie's path, for some reason IE adds extra slash to the beginning of the path
        cookies.stream()
                .filter(cookie -> KEYCLOAK_COOKIE_NAMES.contains(cookie.getName()))
                .forEach(cookie -> assertThat(cookie.getPath(), Matchers.endsWith("/auth/realms/foo/")));

        // now navigate to realm which name overlaps the first realm and delete cookies for that realm (foobar)
        navigateToLoginPage("foobar");
        driver.manage().deleteAllCookies();

        // cookies shouldn't be sent for the first access to /realms/foobar/account
        // At this moment IE would sent cookies for /auth/realms/foo without the fix
        cookies = driver.manage().getCookies();
        Assert.assertTrue("There shouldn't be any cookies sent!", cookies.isEmpty());

        // navigate to account and check if correct cookies were sent
        driver.navigate().to(oauth.getLoginFormUrl());
        cookies = driver.manage().getCookies();

        Assert.assertTrue("There should be cookies sent!", cookies.size() > 0);
        // check cookie's path, for some reason IE adds extra slash to the beginning of the path
        cookies.stream()
                .filter(cookie -> KEYCLOAK_COOKIE_NAMES.contains(cookie.getName()))
                .forEach(cookie -> assertThat(cookie.getPath(), Matchers.endsWith("/auth/realms/foobar/")));

        // lets back to "/realms/foo/account" to test the cookies for "foo" realm are still there and haven't been (correctly) sent to "foobar"
        oauth.realm("foo");
        driver.navigate().to(oauth.getLoginFormUrl());

        cookies = driver.manage().getCookies();
        Assert.assertTrue("There should be cookies sent!", cookies.size() > 0);
        cookies.stream()
                .filter(cookie -> KEYCLOAK_COOKIE_NAMES.contains(cookie.getName()))
                .forEach(cookie -> assertThat(cookie.getPath(), Matchers.endsWith("/auth/realms/foo/")));
    }

    /**
     * Add two realms which names are overlapping i.e foo and foobar
     * @param testRealms
     */
    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmBuilder foo = RealmBuilder.create().name("foo");
        foo.user(UserBuilder.create().username("foo").password("password"));
        testRealms.add(foo.build());

        RealmBuilder foobar = RealmBuilder.create().name("foobar");
        foo.user(UserBuilder.create().username("foobar").password("password"));
        testRealms.add(foobar.build());
    }

    // if the client is closed before the response is read, it throws 
    // org.apache.http.ConnectionClosedException: Premature end of Content-Length delimited message body
    // that's why the this.httpClient is introduced, the client is closed either here or after test method
    private CloseableHttpResponse sendRequest(HttpRequestBase request, CookieStore cookieStore, HttpCoreContext localContext) throws IOException {
        if (httpClient != null) httpClient.close();
        httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).setRedirectStrategy(new LaxRedirectStrategy()).build();
        return httpClient.execute(request, localContext);
    }

    private CookieStore getCorrectCookies(String uri) throws IOException {
        CookieStore cookieStore = new BasicCookieStore();

        HttpGet request = new HttpGet(uri);
        try (CloseableHttpResponse response = sendRequest(request, new BasicCookieStore(), new HttpCoreContext())) {
            for (org.apache.http.Header h: response.getHeaders("Set-Cookie")) {
                if (h.getValue().contains(AuthenticationSessionManager.AUTH_SESSION_ID)) {
                    cookieStore.addCookie(parseCookie(h.getValue(), AuthenticationSessionManager.AUTH_SESSION_ID));
                } else if (h.getValue().contains(KC_RESTART)) {
                    cookieStore.addCookie(parseCookie(h.getValue(), KC_RESTART));
                }
            }
        }

        return cookieStore;
    }

    private BasicClientCookie parseCookie(String line, String name) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);

        String path = "";
        String value = "";

        for (String s: line.split(";")) {
            if (s.contains(name)) {
                String[] split = s.split("=");
                value = split[1];
            } else if (s.contains("Path")) {
                String[] split = s.split("=");
                path = split[1];
            }
        }

        BasicClientCookie c = new BasicClientCookie(name, value);
        c.setExpiryDate(calendar.getTime());
        c.setDomain(AUTH_SERVER_HOST);
        c.setPath(path);

        return c;
    }

    private void login(String requestURI, CookieStore cookieStore) throws IOException {
        HttpCoreContext httpContext = new HttpCoreContext();
        HttpGet request = new HttpGet(requestURI);

        // send an initial request, we are redirected to login page
        String entityContent;
        try (CloseableHttpResponse response = sendRequest(request, cookieStore, httpContext)) {
            entityContent = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
        }

        // send credentials to login form
        HttpPost post = new HttpPost(ActionURIUtils.getActionURIFromPageSource(entityContent));
        List<NameValuePair> params = new LinkedList<>();
        params.add(new BasicNameValuePair("username", "foo"));
        params.add(new BasicNameValuePair("password", "password"));

        post.setHeader("Content-Type", "application/x-www-form-urlencoded");
        post.setEntity(new UrlEncodedFormEntity(params));

        try (CloseableHttpResponse response = sendRequest(post, cookieStore, httpContext)) {
            assertThat("Expected successful login.", response.getStatusLine().getStatusCode(), is(equalTo(200)));
        }
    }

    private void navigateToLoginPage(String realm) {
        setOAuthUri(realm);
        driver.navigate().to(oauth.getLoginFormUrl());
    }

    private void setOAuthUri(String realm) {
        oauth.realm(realm);
        oauth.redirectUri(oauth.AUTH_SERVER_ROOT + "/realms/" + realm + "/app/auth");
    }
}
