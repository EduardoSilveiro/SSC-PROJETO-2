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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;


import tukano.api.rest.RestUsers;
import tukano.srv.auth.RequestCookies;
import tukano.api.azure.RedisCache;
import utils.JSON;
import utils.Session;

import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.error;

@Path(Authentication.PATH)
public class Authentication {
	static final String PATH = "login";
	static final String PWD = "pwd";
	static final String QUERY = "query";
	static final String USER_ID = "userId";
	static final String COOKIE_KEY = "scc:session";
	static final String LOGIN_PAGE = "login.html";
	private static final int MAX_COOKIE_AGE = 3600;
	static final String REDIRECT_TO_AFTER_LOGIN = "/tukano-1/rest";
	static RedisCache cache = RedisCache.getInstance();
	private static final boolean isCacheActive = Boolean.parseBoolean(System.getenv("CACHE_ACTIVE"));

	@POST
	@Path("/{" + USER_ID+ "}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response login( @PathParam(USER_ID) String userId, @QueryParam( PWD ) String pwd) {
		if(isCacheActive) {
			boolean pwdOk = true;
			if (pwdOk) {
				String uid = UUID.randomUUID().toString();
				var cookie = new NewCookie.Builder(COOKIE_KEY)
						.value(uid)
						.path("/")
						.comment("sessionid")
						.maxAge(MAX_COOKIE_AGE)
						.secure(false)
						.httpOnly(true)
						.build();

				cache.putSession(new Session(uid, userId));

				return Response.seeOther(URI.create(REDIRECT_TO_AFTER_LOGIN))
						.cookie(cookie)
						.build();
			} else {
				throw new NotAuthorizedException("Incorrect login");
			}
		}
		throw new NotAuthorizedException("No session initializeddddd");
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

	@GET
	@Path("/{" + USER_ID+ "}")
	@Produces(MediaType.APPLICATION_JSON)
	public String validateLogin(String userId) {
		try {
			var session = validateSession(userId);
			return JSON.encode(userId);
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
