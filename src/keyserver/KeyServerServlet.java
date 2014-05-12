package keyserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.*;

import org.datanucleus.util.Base64;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;

import static keyserver.Constants.*;

// All constants are declared in keyserver.Constants.

/**
 * Copyright (C) 2014  Carlos Parés: carlosparespulido (at) gmail (dot) com
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@SuppressWarnings("serial")
public class KeyServerServlet extends HttpServlet {
	
//	private static final Logger log = Logger.getLogger(KeyServerServlet.class.getName());
	
	private String retrieveKey(DatastoreService datastore, Key ownerId) {
		Query query = new Query(DB_KIND_USER, ownerId);
		Entity identification = datastore.prepare(query).asSingleEntity();
		return (String)identification.getProperty(DB_USER_KEY);
	}
	
	protected class Rectangle  {
		int x0, xEnd, y0, yEnd;
		Rectangle(int x0, int xEnd, int y0, int yEnd)  {
			this.x0 = x0;
			this.xEnd = xEnd;
			this.y0 = y0;
			this.yEnd = yEnd;
		}
	}
	
	private User getCurrentGoogleUser()  {
		return UserServiceFactory.getUserService().getCurrentUser(); }
	
	private boolean isUserLoggedIn() { 
		return getCurrentGoogleUser() != null;  }
	
	private String createKeyForPermission(String userKey, Key picId, int x0, int xEnd, int y0, int yEnd) {
		String plaintextKey = userKey + ":" + KeyFactory.keyToString(picId) + ":" + x0 + ":" + xEnd + ":" + y0 + ":" + yEnd;
		MessageDigest md;
		String result = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
			md.update(plaintextKey.getBytes());
			byte[] hash = md.digest();
			result = new String(Base64.encode(hash));
		} catch (NoSuchAlgorithmException e) {
			// Not going to happen. Every implementation of Java is required 
			// to support SHA-256, please see here:
			// http://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html
		}
		return result;
	}

	private String createKeyForUser(User _user) {
		SecureRandom srand = new SecureRandom();
		byte[] iv = new byte[512];
		srand.nextBytes(iv);
		MessageDigest md = null;
		
		// This block initializes the MessageDigest
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			// Not going to happen. Every implementation of Java is required 
			// to support SHA-256, please see here:
			// http://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html
		}
		
		md.update(iv);
		byte[] hash = md.digest();
		return new String(Base64.encode(hash));
	}
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		
		if(!isUserLoggedIn())  {
			JSONArray error = createJsonLoginErrorObject("Please login with your Google account");
			resp.getWriter().print(error.toString());
		}  else  { // user is logged in
			String action = req.getParameter(QUERYSTRING_ACTION);
			if(action == null)  {
				// Error! A valid query will always declare an action
				JSONArray error = createJsonErrorObject("No action declared");
				resp.getWriter().print(error.toString());
			}  else if(action.equalsIgnoreCase(ACTION_READPERMISSIONS))  {
				//query for permissions
				processRequestForInformation(req, resp);
			} else if(action.equalsIgnoreCase(ACTION_MYPICTURES))  {
				// view all pictures uploaded by user
				viewMyPictures(req, resp);
			} else if (action.equalsIgnoreCase(ACTION_PICTUREDETAILS))  {
				// return all permissions for one picture
				viewPictureDetails(req, resp);
			}  else  {
				// unsupported operation
				JSONArray error = createJsonErrorObject("Operation " + action + " unknown");
				resp.getWriter().print(error.toString());
			}
		}
	}

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		resp.setContentType("application/json");
		if(!isUserLoggedIn())  {
			JSONArray error = createJsonLoginErrorObject("Please login with your Google account");
			resp.getWriter().print(error.toString());
		}  else  { // user is logged in
			String action = req.getParameter(QUERYSTRING_ACTION);
			if(action == null)  {
				// Error! A valid query will always declare an action
				JSONArray error = createJsonErrorObject("No action declared");
				resp.getWriter().print(error.toString());
			}  else if (action.equalsIgnoreCase(ACTION_ONESTEPUPLOAD)) {
				oneStepPictureUpload(req, resp);
			}  else if (action.equalsIgnoreCase(ACTION_UPDATE))  {
				performUpdate(req, resp);
			}  else  {
				// unsupported operation
				JSONArray error = createJsonErrorObject("Operation " + action + " unknown");
				resp.getWriter().print(error.toString());
			}
		}
	}
	

	
	private void oneStepPictureUpload(HttpServletRequest req,
			HttpServletResponse resp) throws IOException {
			
		User user = getCurrentGoogleUser();
		try  {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			
			Key userId = KeyFactory.createKey(DB_KIND_USER, user.getUserId());
			Query query = new Query(DB_KIND_USER, userId);
			Entity identification = datastore.prepare(query).asSingleEntity();
			
			String userKey;
			
			if(identification == null)  { // a new user (not in the DB)
				Entity newUser = new Entity(DB_KIND_USER, user.getUserId());
				userKey = createKeyForUser(user);
				newUser.setProperty(DB_USER_KEY, userKey);
				datastore.put(newUser);
				identification = newUser;
			}  else  { // user was already in the DB
				userKey = (String)identification.getProperty(DB_USER_KEY);
			}
			
			JSONArray array = new JSONArray( (new BufferedReader(new InputStreamReader(req.getInputStream()))).readLine() );
			JSONObject obj;
			obj = array.getJSONObject(0);
			Entity newPicture = new Entity(DB_KIND_PICTURE, identification.getKey());
			newPicture.setProperty(DB_PICTURE_CREATED, new SimpleDateFormat(
					MISC_DATE_FORMAT, Locale.US).format(new Date()));
			newPicture.setProperty(DB_PICTURE_FILENAME, obj.get(JSON_FILENAME));
			newPicture.setProperty(DB_PICTURE_HEIGHT, obj.get(JSON_IMGHEIGHT));
			newPicture.setProperty(DB_PICTURE_WIDTH, obj.get(JSON_IMGWIDTH));
			datastore.put(newPicture);

			JSONArray response = new JSONArray();
			Key picId = newPicture.getKey();
			
			response.put(new JSONObject(JSON_DEFAULTOK));
			response.put(new JSONObject("{\"" + JSON_PICTUREID + "\": \"" + KeyFactory.keyToString(picId) + "\"}"));
			
			Entity newLine;
			JSONArray usernames;
			String key;
			int x0, xEnd, y0, yEnd;
			for(int i = 1; i < array.length(); i++)  {
				obj = array.getJSONObject(i);
				x0 = obj.getInt(JSON_HSTART);
				xEnd = obj.getInt(JSON_HEND);
				y0 = obj.getInt(JSON_VSTART);
				yEnd = obj.getInt(JSON_VEND);
				usernames = obj.getJSONArray(JSON_USERNAME);
				
				for(int j = 0; j < usernames.length(); j++)  {
					newLine = new Entity(DB_KIND_PERMISSION, picId);
					newLine.setProperty(DB_PERMISSION_H_START, x0);
					newLine.setProperty(DB_PERMISSION_H_END, xEnd);
					newLine.setProperty(DB_PERMISSION_V_START, y0);
					newLine.setProperty(DB_PERMISSION_V_END, yEnd);
					newLine.setProperty(DB_PERMISSION_USERNAME, (String)usernames.get(j));
					datastore.put(newLine);
				}
				key = createKeyForPermission(userKey, picId, obj.getInt(JSON_HSTART), 
						obj.getInt(JSON_HEND), obj.getInt(JSON_VSTART), obj.getInt(JSON_VEND));
				response.put(new JSONObject("{\"" + JSON_KEY + "\": \"" + key + "\"}"));
			}
			resp.getWriter().print(response.toString());
		}  catch (JSONException jsonEx)  {
			JSONArray error = createJsonErrorObject("Malformed JSON object received");
			resp.getWriter().print(error);
		}
	}
	
	
	private void processRequestForInformation(HttpServletRequest req, 
			HttpServletResponse resp) throws IOException {
		String id = req.getParameter(QUERYSTRING_PICTUREID);
		User user = getCurrentGoogleUser();
		if(id == null)  {
			JSONArray error = createJsonErrorObject("No picture ID provided");
			resp.getWriter().print(error.toString());
		}  else  {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			try {
				Key key = KeyFactory.stringToKey(id);
				Query query = new Query(DB_KIND_PERMISSION, key);
		        Filter usernameFilter =
		        		new FilterPredicate(DB_PERMISSION_USERNAME, FilterOperator.EQUAL, user.getEmail().toLowerCase(Locale.ENGLISH));
				query.setFilter(usernameFilter);
				List<Entity> permissions = datastore.prepare(query)
						.asList(FetchOptions.Builder.withLimit(MAX_REGIONS_ALLOWED));
				PrintWriter response = resp.getWriter();
				JSONArray array = new JSONArray();
				JSONObject permission;
				int x0, xEnd, y0, yEnd;
				// first element in a JSON returned array will always be the result
				// of whether the call was successful or not
				array.put(new JSONObject(JSON_DEFAULTOK));
				for(Entity p : permissions)  {
					permission = new JSONObject();
					// properties are returned as Long, but int is expected. Direct conversion
					// from Long to int is unsupported, so we have to include an intermediate
					// casting from Long to long, which can be casted to int.
					x0 = (int)(long)p.getProperty(DB_PERMISSION_H_START);
					xEnd = (int)(long)p.getProperty(DB_PERMISSION_H_END);
					y0 = (int)(long)p.getProperty(DB_PERMISSION_V_START);
					yEnd = (int)(long)p.getProperty(DB_PERMISSION_V_END);
					Entity pic = datastore.get(key);
					permission.put(JSON_KEY, createKeyForPermission( 
							retrieveKey(datastore, pic.getParent()),
							pic.getKey(), x0, xEnd, y0, yEnd));
					permission.put(JSON_HSTART, x0);
					permission.put(JSON_HEND, xEnd);
					permission.put(JSON_VSTART, y0);
					permission.put(JSON_VEND, yEnd);
					array.put(permission);
				}
				response.print(array.toString());
			} catch (JSONException ex)  {
				// Exceptions should not happen here. The {"result": "ok"} JSON
				// will always be correctly formed, and the rest of the objects
				// come from the (validated on input) database.
				// Just in case, we notify of an error.
				JSONArray error = createJsonErrorObject("Serverside JSON problem: "
														  + ex.getMessage() );
				resp.getWriter().print(error.toString());
			} catch (IllegalArgumentException illArgExc)  {
				// Might be thrown if the queried ID does not match the
				// standard format.
				JSONArray error = createJsonErrorObject("Malformed or non-existing ID requested");
				resp.getWriter().print(error.toString());
			} catch (EntityNotFoundException e) {
				// In theory might be thrown if user is not in database.
				// Shouldn't happen in practice, but just in case: 
				JSONArray error = createJsonErrorObject("Picture owner no longer in database");
				resp.getWriter().print(error.toString());
			}
        }
	}
	
	private void viewMyPictures(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		User user = getCurrentGoogleUser();
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Key userId = KeyFactory.createKey(DB_KIND_USER, user.getUserId());
		// this custom query needs to be specified also in war/WEB-INF/datastore-indexes.xml
		Query query = new Query(DB_KIND_PICTURE).setAncestor(userId).addSort(DB_PICTURE_CREATED, SortDirection.DESCENDING);
		List<Entity> pictures = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
		JSONArray array = new JSONArray();
		try  {
			array.put(new JSONObject(JSON_DEFAULTOK));
			JSONObject obj;
			for(Entity entity: pictures)  {
				obj = new JSONObject();
				obj.put(JSON_FILENAME, entity.getProperty(DB_PICTURE_FILENAME));
				obj.put(JSON_DATECREATED, entity.getProperty(DB_PICTURE_CREATED));
				obj.put(JSON_PICTUREID, KeyFactory.keyToString(entity.getKey()));
				array.put(obj);
			}
			resp.getWriter().print(array);
			
		} catch (JSONException jsonex)  {
			// Should never happen, but just in case
			JSONArray error = createJsonErrorObject("Problem loading pictures from database");
			resp.getWriter().print(error);
		}
	}
	
	private void viewPictureDetails(HttpServletRequest req, 
			HttpServletResponse resp) throws IOException {
		User user = getCurrentGoogleUser();
		String id = req.getParameter(QUERYSTRING_PICTUREID);
		if(id == null)  {
			JSONArray error = createJsonErrorObject("No picture ID provided");
			resp.getWriter().print(error.toString());
		}  else  {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			try {
				Key key = KeyFactory.stringToKey(id);
				// if user is the owner of the picture
				if(KeyFactory.createKey(DB_KIND_USER, user.getUserId()).equals(key.getParent()))  {
					Query query = new Query(DB_KIND_PERMISSION, key);
					List<Entity> permissions = datastore.prepare(query)
							.asList(FetchOptions.Builder.withLimit(MAX_PERMISSIONS_RETURNED_PER_PICTURE));
					query = new Query(DB_KIND_PICTURE, key);
					Entity picture = datastore.prepare(query).asSingleEntity();
					PrintWriter response = resp.getWriter();
					JSONArray array = new JSONArray();
					JSONObject permission;
					int x0, xEnd, y0, yEnd;
					// first element in a JSON returned array will always be the result
					// of whether the call was successful or not
					JSONObject metadata = new JSONObject(JSON_DEFAULTOK);
					metadata.put(JSON_PICTUREID, id);
					metadata.put(JSON_IMGHEIGHT, picture.getProperty(DB_PICTURE_HEIGHT));
					metadata.put(JSON_IMGWIDTH, picture.getProperty(DB_PICTURE_WIDTH));
					array.put(metadata);
					for(Entity p : permissions)  {
						permission = new JSONObject();
						// properties are returned as Long, but int is expected. Direct conversion
						// from Long to int is unsupported, so we have to include an intermediate
						// casting from Long to long, which can be casted to int.
						x0 = (int)(long)p.getProperty(DB_PERMISSION_H_START);
						xEnd = (int)(long)p.getProperty(DB_PERMISSION_H_END);
						y0 = (int)(long)p.getProperty(DB_PERMISSION_V_START);
						yEnd = (int)(long)p.getProperty(DB_PERMISSION_V_END);
						permission.put(JSON_HSTART, x0);
						permission.put(JSON_VSTART, y0);
						permission.put(JSON_HEND, xEnd);
						permission.put(JSON_VEND, yEnd);
						permission.put(JSON_USERNAME, (String)p.getProperty(DB_PERMISSION_USERNAME));
						permission.put(JSON_PERMISSIONID, KeyFactory.keyToString(p.getKey()));
						array.put(permission);
					}
					response.print(array.toString());
				}
			} catch (JSONException ex)  {
				// Exceptions should not happen here. The {"result": "ok"} JSON
				// will always be correctly formed, and the rest of the objects
				// come from the (validated on input) database.
				// Just in case, we notify of an error.
				JSONArray error = createJsonErrorObject("Serverside JSON problem: "
														  + ex.getMessage() );
				resp.getWriter().print(error.toString());
			} catch (IllegalArgumentException illArgExc)  {
				// Might be thrown if the queried ID does not match the
				// standard format.
				JSONArray error = createJsonErrorObject("Malformed or non-existing ID requested");
				resp.getWriter().print(error.toString());
			}
        }
	}

	private void performUpdate(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		try  {
			User user = getCurrentGoogleUser();
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Key userId = KeyFactory.createKey(DB_KIND_USER, user.getUserId());
			Query query = new Query(DB_KIND_USER, userId);
			Entity identification = datastore.prepare(query).asSingleEntity();
			
			JSONArray array = new JSONArray( (new BufferedReader(new InputStreamReader(req.getInputStream()))).readLine() );
			JSONObject obj;
			
			obj = array.getJSONObject(0);
			Key pictureKey = KeyFactory.stringToKey(obj.getString(JSON_PICTUREID));
			if(pictureKey != null && pictureKey.getParent().equals(identification.getKey()))  {
				// if requester is the owner of the picture, otherwise the request is dismissed
				for(int i = 1; i < array.length(); i++)  {
					obj = array.getJSONObject(i);
					if(obj.getString(JSON_UPDATEACTION).equalsIgnoreCase(JSON_UPDATEACTION_ADD))  {
						Entity newPermission = new Entity(DB_KIND_PERMISSION, pictureKey);
						newPermission.setProperty(DB_PERMISSION_H_START, obj.getInt(JSON_HSTART));
						newPermission.setProperty(DB_PERMISSION_H_END, obj.getInt(JSON_HEND));
						newPermission.setProperty(DB_PERMISSION_V_START, obj.getInt(JSON_VSTART));
						newPermission.setProperty(DB_PERMISSION_V_END, obj.getInt(JSON_VEND));
						newPermission.setProperty(DB_PERMISSION_USERNAME, obj.getString(JSON_USERNAME));
						datastore.put(newPermission);
					}  else if (obj.getString(JSON_UPDATEACTION).equalsIgnoreCase(JSON_UPDATEACTION_DELETE))  {
						datastore.delete(KeyFactory.stringToKey(obj.getString(JSON_PERMISSIONID)));
					}
				}
			}
			JSONArray response = new JSONArray();
			response.put(new JSONObject(JSON_DEFAULTOK));
			resp.getWriter().print(response.toString());
		}  catch (JSONException jsonEx)  {
			JSONArray error = createJsonErrorObject("Malformed JSON object received");
			resp.getWriter().print(error);
		}  catch (IllegalArgumentException illargex)  {
			JSONArray error = createJsonErrorObject("Malformed or incomplete key received");
			resp.getWriter().print(error);
		}
	}

	private JSONArray createJsonErrorObject(String reason) {
		JSONArray array = null;
		JSONObject error = null;
		try {
			array = new JSONArray();
			error = new JSONObject();
			error.put(JSON_RESULT, JSON_RESULT_ERROR);
			error.put(JSON_REASON, reason);
			error.put(JSON_PROTOCOLVERSION, CURRENT_VERSION);
			error.put(JSON_ISAUTHERROR, false);
			array.put(error);
		} catch (JSONException jsonException)  {
			// Won't happen: exception is thrown only if the first argument
			// is null, and clearly it won't be.
		}
		return array;
	}
	
	private JSONArray createJsonLoginErrorObject(String reason) {
		JSONArray array = null;
		JSONObject error = null;
		try {
			array = new JSONArray();
			error = new JSONObject();
			error.put(JSON_RESULT, JSON_RESULT_ERROR);
			error.put(JSON_REASON, reason);
			error.put(JSON_PROTOCOLVERSION, CURRENT_VERSION);
			error.put(JSON_ISAUTHERROR, true);
			array.put(error);
		} catch (JSONException jsonException)  {
			// Won't happen: exception is thrown only if the first argument
			// is null, and clearly it won't be.
		}
		return array;
	}
}
