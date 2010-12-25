package ceid.netcins.frontend;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import rice.Continuation;
import ceid.netcins.CatalogService;
import ceid.netcins.content.ContentProfile;
import ceid.netcins.content.StoredField;
import ceid.netcins.content.TermField;
import ceid.netcins.frontend.json.ContentProfileJSONConvertor;

public class SetContentTagsHandler extends CatalogFrontendAbstractHandler {

	private static final long serialVersionUID = -565717952819033549L;

	public SetContentTagsHandler(CatalogService catalogService,
			Hashtable<String, Vector<Object>> queue) {
		super(catalogService, queue);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		if (prepare(request, response) == JobStatus.FINISHED)
			return;

		ContentProfileJSONConvertor cpj = new ContentProfileJSONConvertor();
		ContentProfile profile = (ContentProfile)cpj.fromJSON(jsonMap);

		if (cid == null || profile == null) {
			sendStatus(response, RequestFailure);
			return;
		}

		final String reqID = getNewReqID(response);

		profile.add(new StoredField("SHA-1", cid.toStringFull()));
		profile.add(new TermField("Identifier", cid.toStringFull()));

		if (uid == null) { // Local resource.
			// New tag-less content item
			catalogService.indexPseudoContent(profile, new Continuation<Object, Exception>() {
				@Override
				public void receiveResult(Object result) {
					if (!(result instanceof Boolean[]))
						receiveException(null);
					Boolean[] resBool = (Boolean[])result;
					boolean didit = false;
					for (int i = 0; i < resBool.length && !didit; i++)
						didit = resBool[i];

					Vector<Object> res = new Vector<Object>();
					res.add(didit ? RequestSuccess : RequestFailure);
					queue.put(reqID, res);
				}

				@Override
				public void receiveException(Exception exception) {
					Vector<Object> res = new Vector<Object>();
					res.add(RequestFailure);
					queue.put(reqID, res);
				}
			});
			response.flushBuffer();
			return;
		}

		// Search for it in the network
		try {
			catalogService.tagContent(uid, cid, profile, 
					new Continuation<Object, Exception>() {
				@Override
				public void receiveResult(Object result) {
					HashMap<String, Object> resMap = (HashMap<String, Object>)result;
					if (!((Integer)resMap.get("status")).equals(CatalogService.SUCCESS))
						receiveException(new RuntimeException());

					Vector<Object> res = new Vector<Object>();
					res.add(RequestSuccess);
					res.add(resMap.get("data"));
					queue.put(reqID, res);
				}

				@Override
				public void receiveException(Exception exception) {
					Vector<Object> res = new Vector<Object>();
					res.add(RequestFailure);
					queue.put(reqID, res);
				}
			});
		} catch (Exception e) {
			Vector<Object> res = new Vector<Object>();
			res.add(RequestFailure);
			queue.put(reqID, res);
		}
	}
}
