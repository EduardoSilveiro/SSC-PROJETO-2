package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.srv.Authentication.validateSession;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.azure.RedisCache;
import tukano.srv.Authentication;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.Session;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static final boolean isCacheActive = Boolean.parseBoolean(System.getenv("CACHE_ACTIVE"));

	private static Users instance;
	static RedisCache cache = RedisCache.getInstance();

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
			Log.warning("Invalid user information provided.");
			return error(BAD_REQUEST);
		}

		Result<String> dbResult = errorOrValue(DB.insertOne(user), user.getUserId());
		if (!dbResult.isOK()) {
			Log.warning(() -> format("Failed to create user in DB: %s", dbResult.error()));
			return dbResult;
		}

		Log.info(() -> format("User created in DB: %s", user.getUserId()));

		if (isCacheActive) {
			var cacheKey = "users:" + user.getUserId();
			cache.setValue(cacheKey, user);
			Log.info(() -> format("User cached with key: %s", cacheKey));

			var cachedValue = cache.getValue(cacheKey, User.class);
			if (cachedValue != null) {
				Log.info(() -> format("User retrieved from cache: %s", cachedValue));
			} else {
				Log.warning(() -> format("Failed to retrieve user from cache: %s", cacheKey));
			}
		}

		return ok(user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null) {
			Log.warning("Invalid input: userId or password is null.");
			return error(BAD_REQUEST);
		}

		if (!isCacheActive) {
			Result<User> dbResult = DB.getOne(userId, User.class);
			if (!dbResult.isOK()) {
				Log.warning(() -> format("User not found in DB: %s", userId));
				return error(FORBIDDEN);
			}

			User dbUser = dbResult.value();
			if (!dbUser.getPwd().equals(pwd)) {
				Log.warning(() -> format("Password mismatch for user: %s", userId));
				return error(FORBIDDEN);
			}

			Log.info(() -> format("User found in DB: %s", userId));
			return ok(dbUser);
		} else {

			var cacheKey = "users:" + userId;
			User cachedUser = cache.getValue(cacheKey, User.class);

			if (cachedUser != null) {
				Log.info(() -> format("User found in cache: %s", userId));
				if (cachedUser.getPwd().equals(pwd)) {
					return ok(cachedUser);
				} else {
					Log.warning(() -> format("Password mismatch for cached user: %s", userId));
					return error(FORBIDDEN);
				}
			}

			Result<User> dbResult = DB.getOne(userId, User.class);
			if (!dbResult.isOK()) {
				Log.warning(() -> format("User not found in DB: %s", userId));
				return error(FORBIDDEN);
			}

			User dbUser = dbResult.value();
			if (!dbUser.getPwd().equals(pwd)) {
				Log.warning(() -> format("Password mismatch for user: %s", userId));
				return error(FORBIDDEN);
			}

			return ok(dbUser);
		}
	}
	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other)) {
			Log.warning("Invalid input for updateUser: userId, pwd, or other is invalid.");
			return error(BAD_REQUEST);
		}

		if (!isCacheActive) {
			return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
				Result<User> updateResult = DB.updateOne(user.updateFrom(other));
				if (updateResult.isOK()) {
					Log.info(() -> format("User updated in DB: %s", userId));
				} else {
					Log.warning(() -> format("Failed to update user in DB: %s", userId));
				}
				return updateResult;
			});
		} else {
			var cacheKey = "users:" + userId;
			User cachedUser = cache.getValue(cacheKey, User.class);

			if (cachedUser != null) {
				Log.info(() -> format("User found in cache for update: %s", userId));
				if (!cachedUser.getPwd().equals(pwd)) {
					Log.warning(() -> format("Password mismatch for cached user: %s", userId));
					return error(FORBIDDEN);
				}

				cachedUser.updateFrom(other);
				cache.setValue(cacheKey, cachedUser);
				Log.info(() -> format("User updated in cache: %s", userId));
				Log.info(() -> format("New User Info from cache: %s", cachedUser));

				return  Result.ok(DB.updateOne(cachedUser.updateFrom(other)).value());
			}

			return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
				Result<User> updateResult = DB.updateOne(user.updateFrom(other));
				if (updateResult.isOK()) {
					cache.setValue(cacheKey, updateResult.value());
					Log.info(() -> format("User updated in DB and cached: %s", userId));
				} else {
					Log.warning(() -> format("Failed to update user in DB: %s", userId));
				}
				return updateResult;
			});
		}
	}


	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null) {
			Log.warning("Invalid input: userId or pwd is null");
			return error(BAD_REQUEST);
		}

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
			var cacheKey = "users:" + userId;

			if (isCacheActive) {
				Log.info(() -> format("Removing user %s from cache.", userId));
				cache.delete(cacheKey);
			}

			Executors.defaultThreadFactory().newThread(() -> {
				Log.info(() -> format("Starting deletion for user: %s", userId));
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
				Log.info(() -> format("Completed deletion for user: %s", userId));
			}).start();

			Log.info(() -> format("Deleting user %s from the database.", userId));
			return DB.deleteOne(user);
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
