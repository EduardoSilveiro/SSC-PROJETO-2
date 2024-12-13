package tukano.srv;

import java.net.URI;
import java.util.UUID;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import tukano.api.rest.RestUsers;
import tukano.srv.auth.RequestCookies;
import tukano.api.azure.RedisCache;
import utils.Session;

@Path(RestUsers.PATH)
public class Authentication {
	static final String PATH = "login";
	String PWD = "pwd";
	String QUERY = "query";
	String USER_ID = "userId";
	static final String COOKIE_KEY = "scc:session";
	static final String LOGIN_PAGE = "login.html";
	private static final int MAX_COOKIE_AGE = 3600;
	static final String REDIRECT_TO_AFTER_LOGIN = "/ctrl/version";
	static RedisCache cache = RedisCache.getInstance();

	@Path("/{" + USER_ID+ "}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response login( @PathParam(USER_ID) String userId, @QueryParam( PWD ) String pwd) {
		System.out.println("user: " + user + " pwd:" + password);
		boolean pwdOk = true; // replace with code to check user password
		if (pwdOk) {
			String uid = UUID.randomUUID().toString();
			var cookie = new NewCookie.Builder(COOKIE_KEY)
					.value(uid).path("/")
					.comment("sessionid")
					.maxAge(MAX_COOKIE_AGE)
					.secure(false) // ideally, it should be true to only work for https requests
					.httpOnly(true)
					.build();

			cache.putSession(new Session(uid, user));

			return Response.seeOther(URI.create(REDIRECT_TO_AFTER_LOGIN))
					.cookie(cookie)
					.build();
		} else
			throw new NotAuthorizedException("Incorrect login");
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	public String login() {
		try {
			var in = getClass().getClassLoader().getResourceAsStream(LOGIN_PAGE);
			return new String(in.readAllBytes());
		} catch (Exception x) {
			throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
		}
	}

	static public Session validateSession(String userId) throws NotAuthorizedException {
		var cookies = RequestCookies.get();
		return validateSession(cookies.get(COOKIE_KEY), userId);
	}

	static public Session validateSession(Cookie cookie, String userId) throws NotAuthorizedException {

		if (cookie == null)
			throw new NotAuthorizedException("No session initialized");

		var session = cache.getInstance().getSession(cookie.getValue());
		if (session == null)
			throw new NotAuthorizedException("No valid session initialized");

		if (session.user() == null || session.user().length() == 0)
			throw new NotAuthorizedException("No valid session initialized");

		if (!session.user().equals(userId))
			throw new NotAuthorizedException("Invalid user : " + session.user());

		return session;
	}
}
