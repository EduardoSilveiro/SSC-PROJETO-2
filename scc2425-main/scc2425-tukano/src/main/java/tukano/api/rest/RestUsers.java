package tukano.api.rest;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import tukano.api.User;

@Path(RestUsers.PATH)
public interface RestUsers {

	String PATH = "/users";
	String LOGIN ="/login";
	String PWD = "pwd";
	String QUERY = "query";
	String USER_ID = "userId";

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	String createUser(@HeaderParam("isCacheActive") boolean isCacheActive,User user);
	
	
	@GET
	@Path("/{" + USER_ID+ "}")
	@Produces(MediaType.APPLICATION_JSON)
	User getUser(@HeaderParam("isCacheActive") boolean isCacheActive,@PathParam(USER_ID) String userId, @QueryParam( PWD ) String pwd);
	
	
	@PUT
	@Path("/{" + USER_ID+ "}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	User updateUser(@HeaderParam("isCacheActive") boolean isCacheActive,@PathParam( USER_ID ) String userId, @QueryParam( PWD ) String pwd, User user);
	
	
	@DELETE
	@Path("/{" + USER_ID+ "}")
	@Produces(MediaType.APPLICATION_JSON)
	User deleteUser(@HeaderParam("isCacheActive") boolean isCacheActive,@PathParam(USER_ID) String userId, @QueryParam(PWD) String pwd);
	
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	List<User> searchUsers(@HeaderParam("isCacheActive") boolean isCacheActive,@QueryParam(QUERY) String pattern);

	@POST
	@Path(LOGIN + "/{" + USER_ID+ "}" )
	@Consumes(MediaType.APPLICATION_JSON)
	public Response login(@PathParam(USER_ID) String userId, @QueryParam(PWD) String pwd);
}
