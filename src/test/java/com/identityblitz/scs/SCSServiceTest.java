package com.identityblitz.scs;

import com.identityblitz.scs.error.SCSException;
import junit.framework.Assert;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SCSServiceTest {

    @BeforeClass
    public static void setUp() throws Throwable {
        System.setProperty("com.identityblitz.scs.crypto.encodingKey", "30313233343536373839616263646566");
        System.setProperty("com.identityblitz.scs.crypto.hmacKey", "3031323334353637383930313233343536373839");
        System.setProperty("com.identityblitz.scs.sessionMaxAgeInSec", Long.toString(7 * 365 * 86400L));
        System.setProperty("com.identityblitz.scs.cookieDomain", "identityblitz.com");
    }

    @Test
    public void scsSCSServiceEmptyUpstreamTest() throws SCSException {
        HttpServletRequest requestMock = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(requestMock.getCookies()).andReturn(new Cookie[]{});
        EasyMock.replay(requestMock);

        final SCSService service = new SCSService();
        SCSession session = service.extractFromUpstream(requestMock);
        Assert.assertNull(session);
    }

    @Test
    public void scsSCSServiceNotEmptyUpstreamTest() throws SCSException {
        final String SESSION_STATE = "some session state";
        final Cookie cookie = new Cookie("SCS", new SCSService().encode(SESSION_STATE).asString());
        cookie.setDomain("identityblitz.com");
        cookie.setPath("/");

        HttpServletRequest requestMock = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(requestMock.getCookies()).andReturn(new Cookie[]{cookie});
        requestMock.setAttribute("com.identityblitz.scs.requestAttribute", "some session state");
        EasyMock.expectLastCall();
        EasyMock.replay(requestMock);

        final SCSService service = new SCSService();
        SCSession session = service.extractFromUpstream(requestMock);
        Assert.assertEquals(SESSION_STATE, session.getData());
    }

    @Test
    public void scsSCSServiceNotEmptyAttribute() throws SCSException {
        final String SESSION_STATE = "some session state";

        HttpServletRequest requestMock = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(requestMock.getAttribute("com.identityblitz.scs.requestAttribute")).andReturn("some session state");

        HttpServletResponse responseMock = EasyMock.createMock(HttpServletResponse.class);
        Capture<Cookie> capturedCookie = new Capture<Cookie>();
        responseMock.addCookie(EasyMock.capture(capturedCookie));
        EasyMock.expectLastCall();

        EasyMock.replay(requestMock, responseMock);

        final SCSService service = new SCSService();
        SCSession session = service.putIntoDownstream(responseMock, requestMock);
        Assert.assertNotNull(session);

        final Cookie cookie = capturedCookie.getValue();
        Assert.assertEquals("identityblitz.com", cookie.getDomain());
        Assert.assertEquals("/", cookie.getPath());
        Assert.assertTrue(cookie.isHttpOnly());
        Assert.assertEquals("SCS", cookie.getName());

        final SCSession cookieSCS = service.decode(cookie.getValue());
        Assert.assertEquals(SESSION_STATE, cookieSCS.getData());
    }

    @Test
    public void scsSCSServiceEmptyAttribute() throws SCSException {
        HttpServletRequest requestMock = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(requestMock.getAttribute("com.identityblitz.scs.requestAttribute")).andReturn(null);
        EasyMock.expect(requestMock.getCookies()).andReturn(new Cookie[]{});

        HttpServletResponse responseMock = EasyMock.createMock(HttpServletResponse.class);
        Capture<Cookie> capturedCookie = new Capture<Cookie>();
        responseMock.addCookie(EasyMock.capture(capturedCookie));
        EasyMock.expectLastCall();

        EasyMock.replay(requestMock, responseMock);

        final SCSService service = new SCSService();
        SCSession session = service.putIntoDownstream(responseMock, requestMock);
        Assert.assertNull(session);
        Assert.assertEquals(0, capturedCookie.getValue().getMaxAge());
        Assert.assertEquals("identityblitz.com", capturedCookie.getValue().getDomain());
        Assert.assertEquals("/", capturedCookie.getValue().getPath());
        Assert.assertTrue(capturedCookie.getValue().isHttpOnly());
        Assert.assertEquals("SCS", capturedCookie.getValue().getName());
    }

    @Test
    public void scsSCSServiceResetSessionState() throws SCSException {
        final String SESSION_STATE = "some session state";
        final Cookie cookie = new Cookie("SCS", new SCSService().encode(SESSION_STATE).asString());
        cookie.setDomain("identityblitz.com");
        cookie.setPath("/");

        HttpServletRequest requestMock = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(requestMock.getCookies()).andReturn(new Cookie[]{cookie});
        EasyMock.expect(requestMock.getAttribute("com.identityblitz.scs.requestAttribute")).andReturn(null);
        requestMock.setAttribute("com.identityblitz.scs.requestAttribute", SESSION_STATE);
        EasyMock.expectLastCall();

        HttpServletResponse responseMock = EasyMock.createMock(HttpServletResponse.class);
        Capture<Cookie> capturedCookie = new Capture<Cookie>();
        responseMock.addCookie(EasyMock.capture(capturedCookie));
        EasyMock.expectLastCall();

        EasyMock.replay(requestMock, responseMock);
        final SCSService service = new SCSService();
        SCSession session = service.putIntoDownstream(responseMock, requestMock);
        Assert.assertNull(session);
        Assert.assertEquals(0, capturedCookie.getValue().getMaxAge());
        Assert.assertEquals("identityblitz.com", capturedCookie.getValue().getDomain());
        Assert.assertEquals("/", capturedCookie.getValue().getPath());
        Assert.assertTrue(capturedCookie.getValue().isHttpOnly());
        Assert.assertEquals("SCS", capturedCookie.getValue().getName());
    }

}
