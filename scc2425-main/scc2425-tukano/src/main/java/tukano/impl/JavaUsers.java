package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import tukano.srv.Authentication;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static final boolean isCacheActive = Boolean.parseBoolean(System.getenv("CACHE_ACTIVE"));

	private static Users instance;

	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}
	
	private JavaUsers() {}
	
	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user)) {
			return error(BAD_REQUEST);
		}

		// Insert the user into the database
		Result<String> dbResult = errorOrValue(DB.insertOne(user), user.getUserId());
		if (!dbResult.isOK()) {
			return dbResult; // Return any error from the database operation
		}

		// If cache is active, log in the user
		if (isCacheActive) {
//			Authentication auth = new Authentication();
//			Response loginResponse = auth.login(user.getUserId(), user.getPwd());
//
//			if (loginResponse.getStatus() != Response.Status.SEE_OTHER.getStatusCode()) {
//				return error(BAD_REQUEST, "Failed to log in after user creation");
//			}
		}

		return ok(user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null) {
			return error(BAD_REQUEST);
		}

		Result<User> dbResult = DB.getOne(userId, User.class);
		if (!dbResult.isOK() || !dbResult.value().getPwd().equals(pwd)) {
			return error(FORBIDDEN);
		}

		// If cache is active, log in the user
		if (isCacheActive) {
//			Authentication auth = new Authentication();
//			Response loginResponse = auth.login(userId, pwd);
//
//			if (loginResponse.getStatus() != Response.Status.SEE_OTHER.getStatusCode()) {
//				return error(BAD_REQUEST, "Failed to log in after retrieving user");
//			}
		}

		return ok(dbResult.value());
	}
	@Override
	public Result<Object> login(String userId, String pwd) {
		Log.info(() -> format("login : userId = %s, pwd = %s\n", userId, pwd));

		// Validate input parameters
		if (userId == null || pwd == null) {
			return error(BAD_REQUEST );
		}

		// Check if user credentials are valid
		Result<User> userResult = DB.getOne(userId, User.class);
		if (!userResult.isOK() || !userResult.value().getPwd().equals(pwd)) {
			return error(FORBIDDEN );
		}
		if (isCacheActive) {
		// Perform login through the Authentication class
		Authentication auth = new Authentication();
		try {
			Response loginResponse = auth.login(userId, pwd);

			// Check if login was successful
			if (loginResponse.getStatus() == Response.Status.SEE_OTHER.getStatusCode()) {
				return ok(loginResponse);
			} else {
				return error(BAD_REQUEST );
			}
		} catch (Exception e) {
			Log.severe(() -> format("Error during login: %s", e.getMessage()));
			return error(BAD_REQUEST );
		}
		} else return error(FORBIDDEN );
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> DB.updateOne( user.updateFrom(other)));
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {

 			Executors.defaultThreadFactory().newThread( () -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();
			
			return DB.deleteOne( user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
