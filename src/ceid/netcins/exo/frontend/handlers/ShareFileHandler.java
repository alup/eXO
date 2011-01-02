package ceid.netcins.exo.frontend.handlers;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import rice.Continuation;
import rice.environment.params.Parameters;
import rice.persistence.PersistentStorage;
import ceid.netcins.exo.CatalogService;

/**
 * 
 * @author <a href="mailto:loupasak@ceid.upatras.gr">Andreas Loupasakis</a>
 * @author <a href="mailto:ntarmos@cs.uoi.gr">Nikos Ntarmos</a>
 * @author <a href="mailto:peter@ceid.upatras.gr">Peter Triantafillou</a>
 * 
 * "eXO: Decentralized Autonomous Scalable Social Networking"
 * Proc. 5th Biennial Conf. on Innovative Data Systems Research (CIDR),
 * January 9-12, 2011, Asilomar, California, USA.
 * 
 */
public class ShareFileHandler extends AbstractHandler {
	private static final long serialVersionUID = 6460386943881811107L;
	private static final String FileDataTag = "FileData";
	private static final String UploadRepository = "uploads";
	private static final int FileSizeLimit = 10000000;

	private String uploadRepository = null;
	private Integer fileSizeLimit = 0;

	public ShareFileHandler(CatalogService catalogService,
			Hashtable<String, Hashtable<String, Object>> queue) {
		super(catalogService, queue);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		if (prepare(request, response) == RequestState.FINISHED)
			return;

		if (!ServletFileUpload.isMultipartContent(request)) {
			sendStatus(response, RequestStatus.FAILURE, null);
			return;
		}

		PersistentStorage ps = (PersistentStorage)catalogService.getStorageManager().getStorage();
		Parameters params = catalogService.getEnvironment().getParameters();
		// XXX : In a real-world implementation, this should be either sanitized or decoupled from user-supplied data
		uploadRepository =
			ps.getRoot() + File.separator +
			ps.getName() + File.separator +
			(params.contains("exo_uploads_repository") ?
					params.getString("exo_uploads_repository") :
					UploadRepository
			);

		fileSizeLimit = params.contains("exo_uploads_filesize_limit") ?
				catalogService.getEnvironment().getParameters().getInt("exo_uploads_filesize_limit") :
				FileSizeLimit;

		DiskFileItemFactory fileFactory = new DiskFileItemFactory();
		File uploadDir = new File(uploadRepository);
		if (!uploadDir.mkdirs() && !(uploadDir.exists() && uploadDir.isDirectory())) {
			sendStatus(response, RequestStatus.FAILURE, null);
			return;
		}
		fileFactory.setSizeThreshold(fileSizeLimit);
		fileFactory.setRepository(uploadDir);
		ServletFileUpload sfu = new ServletFileUpload(fileFactory);
		try {
			List<FileItem> items = sfu.parseRequest(request);
			Iterator<FileItem> itemsIter = items.iterator();
			while (itemsIter.hasNext()) {
				FileItem item = itemsIter.next();
				if (!item.isFormField() && item.getFieldName().equals(FileDataTag)) {
					String fileName = item.getName();

					File upload = new File(uploadRepository + File.separator + fileName);
					if (upload == null ||
							(upload.exists() && !upload.delete() && !upload.createNewFile()) ||
							(!upload.exists() && !upload.createNewFile()))
						throw new FileUploadException();

					item.write(upload);
					final String reqID = getNewReqID(response);
					doIndexContent(upload, reqID);
					return;
				}
			}
		} catch (FileUploadException e) {
			// Fall through
		} catch (IOException e) {
			// Fall through
		} catch (Exception e) {
			// Fall through
		}
		sendStatus(response, RequestStatus.FAILURE, null);
	}

	private void doIndexContent(final File upload, final String reqID) {
		catalogService.indexContent(upload, new Continuation<Object, Exception>() {
			@Override
			public void receiveResult(Object result) {
				if (!(result instanceof Boolean[])) {
					queueStatus(reqID, RequestStatus.FAILURE, null);
					return;
				}
				Boolean[] resBool = (Boolean[])result;
				boolean didit = false;
				for (int i = 0; i < resBool.length && !didit; i++)
					didit = resBool[i];
				queueStatus(reqID, didit ? RequestStatus.SUCCESS : RequestStatus.FAILURE, null);
			}

			@Override
			public void receiveException(Exception exception) {
				System.err.println("Received exception while trying to index file. Retrying...");
				doIndexContent(upload, reqID);
			}
		});
	}
}
