package ceid.netcins.frontend;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import rice.Continuation;
import rice.pastry.Id;
import ceid.netcins.CatalogService;
import ceid.netcins.content.ContentProfile;
import ceid.netcins.json.ContentProfileJSONConvertor;
import ceid.netcins.json.Json;
import ceid.netcins.user.User;

public class SetUserProfileHandler extends CatalogFrontendAbstractHandler {

	private static final long serialVersionUID = 5124127253356875812L;

	public SetUserProfileHandler(CatalogService catalogService, Hashtable<String, Object> queue) {
		super(catalogService, queue);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
 		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);

		String param = request.getParameter(PostParamTag);
		if (param != null) {
			Object jsonParams = Json.parse(param);
			if (jsonParams instanceof Map) {
				Map jsonMap = (Map)jsonParams;
				if (((Map)jsonParams).containsKey(ReqIDTag)) {
					String reqID = (String)((Map)jsonParams).get(ReqIDTag);
					Vector<Object> res = (Vector<Object>)queue.get(reqID);
					if (res == null) {
						Map<String, String> ret = new HashMap<String, String>();
						ret.put(RequestStatusTag, RequestStatusUnknownTag);
						response.getWriter().write(Json.toString(ret));
						response.flushBuffer();
						return;
					} else if (res.get(0).equals(RequestStatusProcessingTag)) {
						Map<String, String> ret = new HashMap<String, String>();
						ret.put(RequestStatusTag, RequestStatusProcessingTag);
						response.getWriter().write(Json.toString(ret));
						response.flushBuffer();
						return;
					}
					response.getWriter().write(Json.toString(res.toArray()));
					response.flushBuffer();
					queue.remove(reqID);
					return;
				} else if (jsonMap.containsKey(UIDTag)) {
					final String reqID = Integer.toString(CatalogFrontend.nextReqID());
					Vector<Object> na = new Vector<Object>();
					na.add(RequestStatusProcessingTag);
					queue.put(reqID, na);
					Map<String, String> ret = new HashMap<String, String>();
					ret.put(ReqIDTag, reqID);
					response.getWriter().write(Json.toString(ret));
					String UID = (String)jsonMap.get(UIDTag);
					ContentProfileJSONConvertor cpj = new ContentProfileJSONConvertor();
					ContentProfile profile = (ContentProfile)cpj.fromJSON(jsonMap);
					try {
						catalogService.tagUser(Id.build(UID), profile, null, new Continuation<Object, Exception>() {

							@Override
							public void receiveResult(Object result) {
								HashMap<String, Object> resMap = (HashMap<String, Object>)result;
								if (!((Integer)resMap.get("status")).equals(CatalogService.SUCCESS))
									receiveException(new RuntimeException());

								Vector<Object> res = new Vector<Object>();
								res.add(RequestStatusSuccessTag);
								res.add(resMap.get("data"));
								queue.put(reqID, res);
							}

							@Override
							public void receiveException(Exception exception) {
								Vector<String> res = new Vector<String>();
								res.add(RequestStatusFailureTag);
								queue.put(reqID, res);
							}
						});
					} catch (Exception e) {
						Vector<String> res = new Vector<String>();
						e.printStackTrace();
						res.add(RequestStatusFailureTag);
						queue.put(reqID, res);
					}
					return;
				}

				ContentProfileJSONConvertor cc = new ContentProfileJSONConvertor();
				ContentProfile profile = (ContentProfile)cc.fromJSON(jsonMap);
				final User user = catalogService.getUser();
				ContentProfile oldProfile = new ContentProfile(user.getCompleteUserProfile());
				user.setUserProfile(profile);
				if (!oldProfile.equalsPublic(profile)) {
					final String reqID = Integer.toString(CatalogFrontend.nextReqID());
					Vector<String> na = new Vector<String>();
					na.add(RequestStatusProcessingTag);
					queue.put(reqID, na);
					Map<String, String> ret = new HashMap<String, String>();
					ret.put(ReqIDTag, reqID);
					response.getWriter().write(Json.toString(ret));
					// The public part has changed. We should reindex the user profile in the network
					catalogService.indexUser(new Continuation<Object, Exception>() {
						public void receiveResult(Object result) {
							System.out.println("SUPH: User : " + user.getUID()
									+ ", indexed successfully");
							// TODO : Check the replicas if are updated correctly!
							// run replica maintenance
							// runReplicaMaintence();
							int indexedNum = 0;
							Vector<String> res = new Vector<String>();
							res.add(RequestStatusSuccessTag);
							if (result instanceof Boolean[]) {
								Boolean[] results = (Boolean[]) result;
								if (results != null)
									for (Boolean isIndexedTerm : results) {
										if (isIndexedTerm)
											indexedNum++;
										res.add(Boolean.toString(isIndexedTerm));
									}
								System.out.println("Total " + indexedNum
										+ " terms indexed out of " + results.length
										+ "!");
							}
							queue.put(reqID, res);
						}

						public void receiveException(Exception result) {
							System.out.println("User : " + user.getUID()
									+ ", indexed with errors : "
									+ result.getMessage());
							Vector<String> res = new Vector<String>();
							res.add(RequestStatusFailureTag);
							queue.put(reqID, res);
						}
					});
				}
			}
		}
		Map<String, String> ret = new HashMap<String, String>();
		ret.put(RequestStatusTag, RequestStatusFailureTag);
		response.getWriter().write(Json.toString(ret));
	}
}
