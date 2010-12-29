package ceid.netcins.exo;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.direct.DirectNodeHandle;
import rice.pastry.direct.DirectPastryNodeFactory;
import rice.pastry.direct.EuclideanNetwork;
import rice.pastry.direct.GenericNetwork;
import rice.pastry.direct.NetworkSimulator;
import rice.pastry.direct.SphereNetwork;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.nat.rendezvous.RendezvousSocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.persistence.LRUCache;
import rice.persistence.MemoryStorage;
import rice.persistence.PersistentStorage;
import rice.persistence.StorageManagerImpl;
import ceid.netcins.exo.content.ContentField;
import ceid.netcins.exo.content.ContentProfile;
import ceid.netcins.exo.content.TermField;
import ceid.netcins.exo.frontend.handlers.AcceptFriendRequestHandler;
import ceid.netcins.exo.frontend.handlers.GetContentHandler;
import ceid.netcins.exo.frontend.handlers.GetContentIDsHandler;
import ceid.netcins.exo.frontend.handlers.GetContentTagsHandler;
import ceid.netcins.exo.frontend.handlers.GetFriendRequestsHandler;
import ceid.netcins.exo.frontend.handlers.GetFriendUIDsHandler;
import ceid.netcins.exo.frontend.handlers.GetUserProfileHandler;
import ceid.netcins.exo.frontend.handlers.GetUserTagsHandler;
import ceid.netcins.exo.frontend.handlers.RejectFriendRequestHandler;
import ceid.netcins.exo.frontend.handlers.SearchContentDHTHandler;
import ceid.netcins.exo.frontend.handlers.SearchUserDHTHandler;
import ceid.netcins.exo.frontend.handlers.SendFriendRequestHandler;
import ceid.netcins.exo.frontend.handlers.SetContentTagsHandler;
import ceid.netcins.exo.frontend.handlers.SetUserProfileHandler;
import ceid.netcins.exo.frontend.handlers.ShareFileHandler;
import ceid.netcins.exo.frontend.json.Json;
import ceid.netcins.exo.user.User;
import ceid.netcins.exo.user.UserNodeIdFactory;

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
public class Frontend {
	public static final int REPLICATION_FACTOR = 3;
	public static final int LEASE_PERIOD = 10000; // 10 seconds
	public static final int TIME_TO_FIND_FAULTY = 15000; // 15 seconds
	public static final String INSTANCE = "CatalogFrontend";
	public static final String PROTOCOL_DIRECT = "direct";
	public static final String PROTOCOL_SOCKET = "socket";
	public static final String PROTOCOL_RENDEZVOUS = "rendezvous";
	public static final String SIMULATOR_SPHERE = "sphere";
	public static final String SIMULATOR_EUCLIDEAN = "euclidean";
	public static final String SIMULATOR_GT_ITM = "gt-itm";

	private InetSocketAddress bootstrapNodeAddress;
	private NodeHandle bootstrapNodeHandle = null;
	private int webServerPort = 8080;
	private Logger logger;

	private Hashtable<String, Vector<String>> queue = null;

	private String userName = null;
	private String resourceName = null;
	private boolean isBootstrap = false;
	private PastryNode node = null;
	private CatalogService catalogService = null;
	private Server server = null;
	private User user = null;
	private Environment environment = null;
	private IdFactory pastryIdFactory = null;
	private NetworkSimulator<DirectNodeHandle, RawMessage> simulator = null;

	private static Random reqIdGenerator = new Random(System.currentTimeMillis());

	public Frontend(Environment env, String userName, String resourceName, boolean isBootstrap) throws IOException {
		this(env, userName, resourceName, env.getParameters().getInt("exo_jetty_port"), isBootstrap);
	}

	public Frontend(Environment env, String userName, String resourceName, int jettyPort) throws IOException {
		this(env, userName, resourceName, jettyPort, false);
	}

	public Frontend(Environment env, String userName, String resourceName) throws IOException {
		this(env, userName, resourceName, env.getParameters().getInt("exo_jetty_port"), false);
	}

	public Frontend(Environment env, String userName, String resourceName, int jettyPort, boolean isBootstrap) throws IOException {
		this.logger = env.getLogManager().getLogger(getClass(),null);
		this.environment = env;
		pastryIdFactory = new PastryIdFactory(env);

		this.userName = userName;
		this.resourceName = resourceName;
		this.isBootstrap = isBootstrap;
		this.webServerPort = jettyPort;
		this.queue = new Hashtable<String, Vector<String>>();
		Parameters params = env.getParameters();
		int pastryNodePort = params.getInt("exo_pastry_port");
		String pastryNodeProtocol = params.getString("exo_pastry_protocol");
		String simulatorType = params.getString("direct_simulator_topology");

		UserNodeIdFactory nodeIdFactory = new UserNodeIdFactory(userName, resourceName);
		PastryNodeFactory nodeFactory = null;
		if (pastryNodeProtocol.equalsIgnoreCase(PROTOCOL_DIRECT)) {
			if (simulatorType.equalsIgnoreCase(SIMULATOR_SPHERE)) {
				simulator = new SphereNetwork<DirectNodeHandle, RawMessage>(env);
			} else if (simulatorType.equalsIgnoreCase(SIMULATOR_GT_ITM)){
				simulator = new GenericNetwork<DirectNodeHandle, RawMessage>(env);        
			} else {
				simulator = new EuclideanNetwork<DirectNodeHandle, RawMessage>(env);
			}
			nodeFactory = new DirectPastryNodeFactory(nodeIdFactory, simulator, env);
		} else if (pastryNodeProtocol.equalsIgnoreCase(PROTOCOL_SOCKET)) {
			InetSocketAddress address = params.getInetSocketAddress("exo_pastry_bootstrap");
			nodeFactory = new SocketPastryNodeFactory(nodeIdFactory, address.getAddress(), pastryNodePort, env);
		} else if (pastryNodeProtocol.equalsIgnoreCase(PROTOCOL_RENDEZVOUS)) {
			InetSocketAddress address = params.getInetSocketAddress("exo_pastry_bootstrap");
			nodeFactory = new RendezvousSocketPastryNodeFactory(nodeIdFactory, address.getAddress(), pastryNodePort, env, false);
		}

		if (nodeFactory == null)
			nodeFactory = DistPastryNodeFactory.getFactory(new RandomNodeIdFactory(environment),
					DistPastryNodeFactory.PROTOCOL_SOCKET, pastryNodePort, env);

		if (!isBootstrap && !pastryNodeProtocol.equalsIgnoreCase(PROTOCOL_DIRECT)) {
			try {
				bootstrapNodeAddress = params.getInetSocketAddress("exo_pastry_bootstrap");
			} catch (UnknownHostException uhe) {
				throw new RuntimeException(uhe); 
			}
			bootstrapNodeHandle =((SocketPastryNodeFactory)nodeFactory).getNodeHandle(bootstrapNodeAddress, pastryNodePort);
		}

		Id id = UserNodeIdFactory.generateNodeId(userName, resourceName);
		this.user = new User(id, userName, resourceName);
		try {
			node = nodeFactory.newNode((rice.pastry.Id)id);
		} catch (IOException e) {
			logger.logException("Unable to create pastry node", e);
			throw e;
		}
		System.err.println("User/Node ID: " + id.toStringFull());
	}

	public static int nextReqID() {
		return reqIdGenerator.nextInt();
	}

	private int startPastryNode() {
		if (isBootstrap)
			node.boot((NodeHandle)null);
		else
			node.boot(bootstrapNodeHandle);

		synchronized (node) {
			while (!node.isReady()) {
				try {
					node.wait(1000);
				} catch (InterruptedException ie) {
					logger.logException("Error booting pastry node", ie);
					return -1;
				}
				if (!node.isReady()) {
					System.err.println("Waiting...");
				}
			}
		}
		return 0;
	}

	private int startCatalogService() {
		StorageManagerImpl storage = null;
		try {
			storage = new StorageManagerImpl(pastryIdFactory,
					new PersistentStorage(pastryIdFactory, "eXO_Storage_Root", -1, environment), new LRUCache(new MemoryStorage(pastryIdFactory), 100000, environment));
		} catch (IOException e) {
			logger.logException("Error initializing storage manager", e);
			return -1;
		}

		catalogService = new CatalogService(node, storage, REPLICATION_FACTOR, INSTANCE, user);
		catalogService.start();

		ArrayList<ContentField> tags = new ArrayList<ContentField>();
		tags.add(new TermField("Username", userName, true));
		tags.add(new TermField("Resource", resourceName, true));
		catalogService.setUserProfile(new ContentProfile(tags),
				new Continuation<Object, Exception>() {
			public void receiveResult(Object result) {
				// TODO : Check the replicas if are updated correctly!
				// run replica maintenance
				// runReplicaMaintence();
				int indexedNum = 0;
				Boolean[] results = null;
				if (result instanceof Boolean[]) {
					results = (Boolean[]) result;
					if (results != null)
						for (Boolean isIndexedTerm : results) {
							if (isIndexedTerm)
								indexedNum++;
						}
				}
				if (indexedNum < 2)
					receiveException(new RuntimeException("Unable to index basic user attributes"));
				System.err.println("Basic user attributes indexed successfully");
			}

			public void receiveException(Exception result) {
				result.printStackTrace();
				System.exit(1);
			}
		});
		// TODO: Remove the following two lines when out of the RnD phase
		catalogService.getUser().addSharedContentProfile(catalogService.getUser().getUID(), "Test 1", new ContentProfile(tags));
		catalogService.getUser().addSharedContentProfile(rice.pastry.Id.makeRandomId(reqIdGenerator), "Test 2", new ContentProfile(tags));

		return 0;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void addServletToContext(Class handlerClass, ServletContextHandler context) {
		Constructor constructor = null;
		Class[] params = new Class[] { CatalogService.class, Hashtable.class };
		try {
			constructor = handlerClass.getConstructor(params);
		} catch (Exception e) {
			logger.logException("Unable to find constructor", e);
			return;
		}
		try {
			context.addServlet(new ServletHolder((HttpServlet)constructor.newInstance(catalogService, queue)),  "/" + handlerClass.getSimpleName().replace("Handler", "/"));
		} catch (Exception e) {
			logger.logException("Unable to instantiate new handler", e);
			return;
		}
	}

	private ContextHandler mountFileRoute(String url, String templateName) {
		ContextHandler plainFileContext = new ContextHandler();
		plainFileContext.setContextPath(url);
		ResourceHandler plainFileHandler = new ResourceHandler();
		plainFileHandler.setDirectoriesListed(false);
		plainFileHandler.setWelcomeFiles(new String[] { templateName});
		plainFileHandler.setResourceBase(System.getProperty("jetty.home", "/"));
		plainFileContext.setHandler(plainFileHandler);
		return plainFileContext;
	}
	
	@SuppressWarnings("rawtypes")
	private int startWebServer() {
		String rootDir = environment.getParameters().getString("exo_jetty_root");
		if (rootDir != null)
			System.setProperty("jetty.home", rootDir);
			
		server = new Server();

		SelectChannelConnector connector = new SelectChannelConnector();
		connector.setPort(webServerPort);
		server.setConnectors(new Connector[] { connector });

		ContextHandlerCollection handlersList = new ContextHandlerCollection();

		/*
		 * Placeholder for Wicket integration
		 */
		/*
		// Needs slf4j jars
		ServletContextHandler root = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
		FilterHolder filterHolder = new FilterHolder(WicketFilter.class);
		filterHolder.setInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM,
				WICKET_WEBAPP_CLASS_NAME);
		root.addFilter(filterHolder, "/*", 1);
		root.addServlet(DefaultServlet.class, "/*");
		handlersList.addHandler(root);
		*/

		// XXX: Watch out! Handlers are scanned in-order until baseRequest.handled = true, and matched on a String.startsWith() basis
		Class[] handlerClasses = new Class[] {
				ShareFileHandler.class,
				SetUserProfileHandler.class,
				SetContentTagsHandler.class,
				GetUserProfileHandler.class,
				GetUserTagsHandler.class,
				GetFriendRequestsHandler.class,
				GetFriendUIDsHandler.class,
				GetContentTagsHandler.class,
				GetContentIDsHandler.class,
				GetContentHandler.class,
				SendFriendRequestHandler.class,
				AcceptFriendRequestHandler.class,
				RejectFriendRequestHandler.class,
				SearchUserDHTHandler.class,
				SearchContentDHTHandler.class
		};
		ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		servletContextHandler.setContextPath("/servlet");
		servletContextHandler.setResourceBase(System.getProperty("jetty.home", "/"));
		servletContextHandler.setClassLoader(Thread.currentThread().getContextClassLoader());
		servletContextHandler.setAllowNullPathInfo(true);
		for (Class<ContextHandler> handlerClass : handlerClasses) {
			addServletToContext(handlerClass, servletContextHandler);
		}
		handlersList.addHandler(servletContextHandler);

		/*
		 * File URL routes deployment  
		 */		
		handlersList.addHandler(mountFileRoute("/", "index.html"));
		handlersList.addHandler(mountFileRoute("/search", "search.html"));

		handlersList.addHandler(new DefaultHandler());

		server.setHandler(handlersList);
		Json.init(); // Make sure Json singleton is instantiated

		try {
			server.start();
			server.join();
		} catch (Exception e) {
			logger.logException("Error starting web server", e);
			return -1;
		}
		return 0;
	}

	public void run() {
		Thread.currentThread().setName("eXO main thread");

		if (startPastryNode() == -1 || startCatalogService() == -1 || startWebServer() == -1)
			return;
	}

	public static void main(String[] args) {
		if (args.length != 3 && args.length != 2) {
			System.err.println("Usage: java ... ceid.netcins.CatalogFrontend <username> <resource> [port]");
			return;
		}
		String userName = args[0], resourceName = args[1];
		int jettyPort = 0;
		if (args.length == 3) {
			try {
				jettyPort = Integer.parseInt(args[2]);
				if (jettyPort < 1 || jettyPort > 65535)
					throw new NumberFormatException();
			} catch (NumberFormatException e) {
				System.err.println("Port should be an integer in [1, 65535]");
				return;
			}
		}

		Environment env = new Environment(new String[] { "freepastry", "eXO" }, null);
		Frontend cf = null;
		try {
			cf = (args.length == 3) ?
					new Frontend(env, userName, resourceName, jettyPort, true) :
					new Frontend(env, userName, resourceName, true);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		cf.run();
	}
}