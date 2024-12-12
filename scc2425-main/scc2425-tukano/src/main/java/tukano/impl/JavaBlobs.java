package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
 import static tukano.srv.Authentication.validateSession;

import tukano.srv.Authentication;
import java.util.logging.Logger;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import utils.Hash;
import utils.Hex;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import utils.Session;

public class JavaBlobs implements Blobs {
	
	private static Blobs instance;


	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
	private static final boolean isCacheActive = Boolean.parseBoolean(System.getenv("CACHE_ACTIVE"));
	public String baseURI;
	private BlobStorage storage;
	
	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}
	
	private JavaBlobs() {
		storage = new FilesystemStorage();
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
	}


	
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token ) {
		Log.info(() -> format("upload : blobId = %s, token = %s, isCacheActive = %b\n", blobId, token ));

		// Validate session if cache is active
//		if (isCacheActive) {
//			Authentication.validateSession();
//		}

		// Validate blob ID and token
		if (!validBlobId(blobId, token)) {
			return error(FORBIDDEN);
		}

		return storage.write(toPath(blobId), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath( blobId ) );
	}

	@Override
	public Result<Void> delete(String blobId, String token  ) {
		Log.info(() -> format("delete : blobId = %s, token = %s, isCacheActive = %b\n", blobId, token, isCacheActive));

		// Validate session if cache is active
		if (isCacheActive) {
			validateSession(  blobId);
		}

		// Validate blob ID and token
		if (!validBlobId(blobId, token)) {
			return error(FORBIDDEN);
		}

		return storage.delete(toPath(blobId));
	}


	@Override
	public Result<Void> deleteAllBlobs(String userId, String token  ) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token = %s, isCacheActive = %b\n", userId, token, isCacheActive));

		// Validate session if cache is active
		if (isCacheActive) {
			//Session session = validateSession(sessionCookie, userId);

		}

		// Validate token
		if (!Token.isValid(token, userId)) {
			return error(FORBIDDEN);
		}

		return storage.delete(toPath(userId));
	}
	
	private boolean validBlobId(String blobId, String token) {		
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
}
