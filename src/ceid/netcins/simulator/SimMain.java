package ceid.netcins.simulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import rice.environment.Environment;
import ceid.netcins.FriendsRequest;
import ceid.netcins.IndexContentRequest;
import ceid.netcins.IndexPseudoContentRequest;
import ceid.netcins.IndexURLRequest;
import ceid.netcins.IndexUserRequest;
import ceid.netcins.RandomQueriesRequest;
import ceid.netcins.Request;
import ceid.netcins.RetrieveContRequest;
import ceid.netcins.ScenarioRequest;
import ceid.netcins.SearchContentRequest;
import ceid.netcins.SearchSocialTagsRequest;
import ceid.netcins.SearchURLRequest;
import ceid.netcins.SearchUserRequest;
import ceid.netcins.StatsRequest;
import ceid.netcins.TagContentRequest;
import ceid.netcins.messages.QueryPDU;

/**
 * 
 * @author Andreas Loupasakis
 */
public class SimMain {

	// Convenience for the delimiter of terms(e.g. in query sentence)
	// TODO : this should be replaced with a more proffesional way to give the param
	public static final String DELIMITER = "::";

	// Basic Simulator Commands' numcodes

	public static final int INDEXCONTENT = 1;
	public static final int SEARCHCONTENT = 2;
	public static final int SEARCHUSER = 3;
	public static final int CREATENODES = 4;
	public static final int IMPORTTEST = 5;
	public static final int STOPTEST = 6;
	public static final int NODE = 7;
	public static final int HELP = 8;
	public static final int EXIT = 9;
	public static final int FRIENDS = 10;
	public static final int INDEXURL = 11;
	public static final int SEARCHURL = 12;
	public static final int TAGCONTENT = 13;
	public static final int SEARCHSOCIALTAGS = 14;
	public static final int RETRIEVECONTENT = 15;
	public static final int INDEXPSEUDOCONTENT = 16;
	public static final int STATS = 17;
	public static final int CLEARSTATS = 18;
	public static final int RANDOMQUERIES = 19;

	/** Creates a new instance of SimMain */
	public SimMain() {
	}

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		SimMain frontend = null;
		try {
			Thread.currentThread().setName("FrontendThread");

			frontend = new SimMain();

			frontend.preTest();
			frontend.triggerTest(args);
			frontend.postTest();

		} catch (Exception e) {
			System.out.println("Error occured!\n");
			e.printStackTrace();
		}
	}

	/**
	 * timeSource Here all the pre-testing issues must be covered.
	 */
	public void preTest() {

		// Deletes all the files in the FreePastry-Storage-Root dir
		LinkedList<File> delme = new LinkedList<File>();
		delme.add(new File("FreePastry-Storage-Root"));
		while (!delme.isEmpty()) {
			File f = (File) delme.removeFirst();
			if (f.isDirectory()) {
				File[] subs = f.listFiles();
				if (subs.length == 0) {
					f.delete();
				} else {
					delme.addAll(Arrays.asList(subs));
					delme.addLast(f);
				}
			} else {
				f.delete();
			}
		}
	}

	/**
	 * Triggers the test through the driver object.
	 */
	public void triggerTest(String[] args) throws Exception {
		env = parseArgs(args);

		// Allocate Memory for the SimDriver in order both Threads to be
		// able to set important simulation variables
		driver = new SimDriver(env);

		// Debugging cpf = new ContentProfileFactory();

		// hsh = new HttpServerHandler(driver,null);
		// hsh.startUIServer();
		// Use cli if the user has requested it!
		if (cliEnv) {
			in = null;
			in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
			if (in != null)
				cli(in);
		} else {
			parseXMLFileSAX(new File(lastarg));
		}
	}

	/**
	 * Here must be put all the post-testing processing.
	 */
	public void postTest() {

	}

	void cli(BufferedReader in) {
		// CLI for user commands!
		while (true) {
			try {

				System.out.println("< :-) >"); // prompt the user

				String line = in.readLine();

				if (line == null || line.length() == -1)
					break;

				line = line.trim();

				switch (parseLine(line)) {

				case CREATENODES:
					// The first time this command is called,
					// we create the "requestDispather" Thread to monitor!
					createRequestDispatcher();

					// Debugging only
					// hsh.setDispatcher(requestDispatcher);

					break;

				case INDEXPSEUDOCONTENT:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexURLRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 4); // it includes
																// -s
						sourceNum = Integer.parseInt(sargs[1]);
						Map<String, String> zero_tags = new HashMap<String, String>();
						zero_tags.put("ZeroTags", sargs[3].trim());
						zero_tags.put("Identifier", sargs[2]);
						// Add the Request for indexing the file
						this.driver.execRequests
								.add(new IndexPseudoContentRequest(sargs[2],
										zero_tags, sourceNum));
					} else {
						int sourceNum = IndexURLRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 2);
						Map<String, String> zero_tags = new HashMap<String, String>();
						zero_tags.put("ZeroTags", sargs[1].trim());
						zero_tags.put("Identifier", sargs[0]);
						// Add the Request for indexing the file
						this.driver.execRequests
								.add(new IndexPseudoContentRequest(sargs[0],
										zero_tags, sourceNum));
					}

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case INDEXCONTENT:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					// For debugging
					// Map<File,ContentProfile> wholecpf = cpf.buildFromDir(new
					// File(lastarg));
					// Iterator<File> it = wholecpf.keySet().iterator();
					// while(it.hasNext()){
					// File f = it.next();
					// System.out.println("\n\n For File ---> "+f+" <---\n"+wholecpf.get(f).toString());
					// }

					// For debugging
					// Iterator i = tfv.keySet().iterator();
					// while(i.hasNext()){
					// String str = (String) i.next();
					// System.out.println("Term "+str+" has tf="+tfv.get(str));
					// }

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						sourceNum = Integer.parseInt(sargs[1]);
						File f = new File(sargs[2]);
						if (f.isDirectory()) {
							directoryIndexing(f, sourceNum);
						} else
							// Add the Request for indexing the file
							this.driver.execRequests
									.add(new IndexContentRequest(sargs[2],
											sourceNum));
					} else {
						File f = new File(lastarg);
						if (f.isDirectory()) {
							directoryIndexing(f);
						} else
							// Add the Request for indexing the file
							this.driver.execRequests
									.add(new IndexContentRequest(lastarg));
					}

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case INDEXURL:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexURLRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 4); // it includes
																// -s
						sourceNum = Integer.parseInt(sargs[1]);
						URL url = new URL(sargs[2]);
						Map<String, String> tags = new HashMap<String, String>();
						tags.put("Tags", sargs[3].trim());
						// Add the Request for indexing the file
						this.driver.execRequests.add(new IndexURLRequest(url,
								tags, sourceNum));
					} else {
						int sourceNum = IndexURLRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 2);
						URL url = new URL(sargs[0]);
						Map<String, String> tags = new HashMap<String, String>();
						tags.put("Tags", sargs[1].trim());
						// Add the Request for indexing the file
						this.driver.execRequests.add(new IndexURLRequest(url,
								tags, sourceNum));
					}

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case TAGCONTENT:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = TagContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 5); // it includes
																// -s
						sourceNum = Integer.parseInt(sargs[1]);
						int uid = Integer.parseInt(sargs[3]);
						// Add the Request for indexing the file
						this.driver.execRequests.add(new TagContentRequest(
								sargs[4].trim(), sargs[2].trim(), uid,
								sourceNum));
					} else {
						int sourceNum = TagContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						int uid = Integer.parseInt(sargs[1]);
						// Add the Request for indexing the file
						this.driver.execRequests.add(new TagContentRequest(
								sargs[2].trim(), sargs[0].trim(), uid,
								sourceNum));
					}

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case RETRIEVECONTENT:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = TagContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 4); // it includes
																// -s
						sourceNum = Integer.parseInt(sargs[1]);
						int uid = Integer.parseInt(sargs[3]);
						// Add the Request for retrieving the file
						this.driver.execRequests.add(new RetrieveContRequest(
								true, sargs[2].trim(), uid, sourceNum));
					} else {
						int sourceNum = TagContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 2);
						int uid = Integer.parseInt(sargs[1]);
						// Add the Request for indexing the file
						this.driver.execRequests.add(new RetrieveContRequest(
								true, sargs[0].trim(), uid, sourceNum));
					}

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case SEARCHCONTENT:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						sourceNum = Integer.parseInt(sargs[1]);
						// Add the Request for search
						if (sargs[2].startsWith("-t")) {
							String targs[] = sargs[2].split(" ", 3);
							if (targs[2].startsWith("-k")) {
								String kargs[] = targs[2].split(" ", 3);
								this.driver.execRequests
										.add(new SearchContentRequest(kargs[2],
												Integer.parseInt(targs[1]),
												sourceNum, Integer
														.parseInt(kargs[1]))); // Feed
																				// the
																				// whole
																				// query!
							} else {
								this.driver.execRequests
										.add(new SearchContentRequest(targs[2],
												Integer.parseInt(targs[1]),
												sourceNum)); // Feed the whole
																// query!
							}
						} else {
							this.driver.execRequests
									.add(new SearchContentRequest(sargs[2],
											SearchContentRequest.SIMPLE,
											sourceNum)); // Feed the whole
															// query!
						}
					} else {
						// Add the Request for search
						if (lastarg.startsWith("-t")) {
							String sargs[] = lastarg.split(" ", 3);
							this.driver.execRequests
									.add(new SearchContentRequest(sargs[2],
											Integer.parseInt(sargs[1]))); // Feed
																			// the
																			// whole
																			// query!
						} else {
							this.driver.execRequests
									.add(new SearchContentRequest(lastarg)); // Feed
																				// the
																				// whole
																				// query!
						}
					}

					doNotify();
					break;

				case SEARCHURL:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						sourceNum = Integer.parseInt(sargs[1]);
						// Add the Request for search
						this.driver.execRequests.add(new SearchURLRequest(
								sargs[2], sourceNum));
					} else {
						// Add the Request for search
						this.driver.execRequests.add(new SearchURLRequest(
								lastarg)); // Feed the whole query!
					}

					doNotify();
					break;

				case SEARCHUSER:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						sourceNum = Integer.parseInt(sargs[1]);
						// Add the Request for search
						if (sargs[2].startsWith("-t")) {
							String targs[] = sargs[2].split(" ", 3);
							if (targs[2].startsWith("-k")) {
								String kargs[] = targs[2].split(" ", 3);
								this.driver.execRequests
										.add(new SearchUserRequest(kargs[2],
												Integer.parseInt(targs[1]),
												sourceNum, Integer
														.parseInt(kargs[1]))); // Feed
																				// the
																				// whole
																				// query!
							} else {
								this.driver.execRequests
										.add(new SearchUserRequest(targs[2],
												Integer.parseInt(targs[1]),
												sourceNum)); // Feed the whole
																// query!
							}
						} else {
							this.driver.execRequests.add(new SearchUserRequest(
									sargs[2], SearchUserRequest.SIMPLE,
									sourceNum)); // Feed the whole query!
						}
					} else {
						// Add the Request for search
						if (lastarg.startsWith("-t")) {
							String sargs[] = lastarg.split(" ", 3);
							this.driver.execRequests.add(new SearchUserRequest(
									sargs[2], Integer.parseInt(sargs[1]))); // Feed
																			// the
																			// whole
																			// query!
						} else {
							this.driver.execRequests.add(new SearchUserRequest(
									lastarg)); // Feed the whole query!
						}
					}

					doNotify();
					break;

				case SEARCHSOCIALTAGS:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg.startsWith("-s")) {
						int sourceNum = IndexContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 4);
						sourceNum = Integer.parseInt(sargs[1]);
						// TODO : Remove the "" strings !
						String[] userList = sargs[2].trim().split(",");
						try {
							// Trick: if it is not integer then we go to catch
							Integer.parseInt(userList[0]);
							int[] nodeNums = new int[userList.length];
							for (int i = 0; i < nodeNums.length; i++) {
								nodeNums[i] = Integer.parseInt(userList[i]);
							}
							// Add the Request for indexing the file
							this.driver.execRequests
									.add(new SearchSocialTagsRequest(sargs[3]
											.trim(), nodeNums, sourceNum));
						} catch (NumberFormatException e) {
							// Add the Request for indexing the file
							this.driver.execRequests
									.add(new SearchSocialTagsRequest(sargs[3]
											.trim(), userList, sourceNum));
						}

					} else {
						int sourceNum = TagContentRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 2);
						// TODO : Remove the "" strings !
						String[] userList = sargs[0].trim().split(",");
						try {
							// Trick: if it is not integer then we go to catch
							Integer.parseInt(userList[0]);
							int[] nodeNums = new int[userList.length];
							for (int i = 0; i < nodeNums.length; i++) {
								nodeNums[i] = Integer.parseInt(userList[0]);
							}
							// Add the Request for indexing the file
							this.driver.execRequests
									.add(new SearchSocialTagsRequest(sargs[1]
											.trim(), nodeNums, sourceNum));
						} catch (NumberFormatException e) {
							// Add the Request for indexing the file
							this.driver.execRequests
									.add(new SearchSocialTagsRequest(sargs[1]
											.trim(), userList, sourceNum));
						}
					}

					doNotify();
					break;

				case IMPORTTEST:
					parseXMLFileSAX(new File(lastarg));
					// doNotify();
					break;

				case RANDOMQUERIES:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg != null && lastarg.startsWith("-t")) {
						// The type of queries (0=Content, 1=EnhancedContent,
						// 2=User-default, 3=EnhancedUSer ...)
						String targs[] = lastarg.split(" ", 5);
						// Add the Request for search
						this.driver.execRequests.add(new RandomQueriesRequest(
								Integer.parseInt(targs[1]), Integer
										.parseInt(targs[2]), Integer
										.parseInt(targs[3]), Integer
										.parseInt(targs[4]))); // Feed the whole
																// query!
					} else
						this.driver.execRequests
								.add(new RandomQueriesRequest()); // Feed the
																	// whole
																	// query!

					doNotify();
					break;

				case STOPTEST:
					doNotify();
					break;

				case NODE:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}
					Request req = parseNodeCommand(lastarg);
					if (req == null) {
						System.out
								.println("Please use the correct syntax :\n\n node (add [<uniquename>])|"
										+ "(delete <uniquename>|<nodenumber>)|"
										+ "(show pending|profiles|shared|bookmarks <node number>)\n");
						break;
					} else {
						// Add the StatsRequest
						this.driver.execRequests.add(req);
					}
					doNotify();
					break;

				case STATS:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					// Add the Request for get the global statistics
					this.driver.execRequests.add(new StatsRequest(
							StatsRequest.GLOBALSTATS));

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case CLEARSTATS:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					// Add the Request for get the global statistics
					this.driver.execRequests.add(new StatsRequest(
							StatsRequest.CLEANGLOBALSTATS));

					// Once for ALL :-)
					doNotify();
					lastarg = null;
					break;

				case HELP:
					break;

				case EXIT:
					if (requestDispatcher != null)
						driver.cleanUp();
					System.exit(0);
					break;

				case FRIENDS:
					if (requestDispatcher == null) {
						System.out
								.println("Nodes must have been created first.");
						break;
					}

					if (lastarg == null) {
						this.driver.execRequests.add(new FriendsRequest(null,
								FriendsRequest.RANDOMSOURCE,
								FriendsRequest.RANDOMSOURCE));
					} else if (lastarg.startsWith("-s")) {
						int sourceNum = FriendsRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						sourceNum = Integer.parseInt(sargs[1]);
						if (sargs[2].startsWith("-d")) {
							int destNum = FriendsRequest.RANDOMSOURCE;
							String sargs2[] = sargs[2].split(" ", 3);
							destNum = Integer.parseInt(sargs2[1]);
							if (sargs2.length > 2) {
								// Add the Request for friendship
								this.driver.execRequests
										.add(new FriendsRequest(sargs2[2],
												sourceNum, destNum));
							} else {
								// Add the Request for friendship
								this.driver.execRequests
										.add(new FriendsRequest(null,
												sourceNum, destNum));
							}
						} else if (sargs.length > 2) {
							// Add the Request for friendship
							this.driver.execRequests.add(new FriendsRequest(
									sargs[2], sourceNum,
									FriendsRequest.RANDOMSOURCE));
						} else {
							// Add the Request for friendship
							this.driver.execRequests.add(new FriendsRequest(
									null, sourceNum,
									FriendsRequest.RANDOMSOURCE));
						}
					} else if (lastarg.startsWith("-d")) {
						int destNum = FriendsRequest.RANDOMSOURCE;
						String sargs[] = lastarg.split(" ", 3);
						destNum = Integer.parseInt(sargs[1]);
						if (sargs.length > 2) {
							// Add the Request for search
							this.driver.execRequests.add(new FriendsRequest(
									sargs[2], FriendsRequest.RANDOMSOURCE,
									destNum));
						} else {
							// Add the Request for search
							this.driver.execRequests
									.add(new FriendsRequest(null,
											FriendsRequest.RANDOMSOURCE,
											destNum));
						}

					} else if (!lastarg.equals("")) {
						// Add the Request for search
						this.driver.execRequests.add(new FriendsRequest(
								lastarg, FriendsRequest.RANDOMSOURCE,
								FriendsRequest.RANDOMSOURCE));
					}
					doNotify();
					break;

				default:
					System.out
							.println("Bad command or filename! Use \"help\"!");
					break;
				}

				System.out.println("Got input: " + line);

			} catch (Exception e) {
				System.out
						.println("\n\nError occured in while loop, during the \"TRIGGER TEST\" phase!\n");
				e.printStackTrace();

			} finally {
				System.out.println("...Continue in CLI!");
			}
		}
	}

	/**
	 * Processes command line arguments and sets the global Environment and
	 * environment variables
	 * 
	 * 
	 */
	protected Environment parseArgs(String args[]) throws IOException {
		// process command line arguments

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-help")) {
				System.out
						.println("Usage: java SimMain [-help] [-simulator (euclidean|sphere|gt-itm)] [-nodes <number_of_nodes>] [-test <xml_test_file>]");
				System.exit(0);
			}
		}

		// Loads pastry settings, and sets up the Environment for simulation
		Environment env = Environment.directEnvironment();

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-simulator") && i + 1 < args.length) {
				env.getParameters().setString("direct_simulator_topology",
						args[i + 1]);
				System.out.println("\nI got " + args[i + 1]
						+ " as Simulator topology!\n");
				break;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-nodes") && i + 1 < args.length) {
				int p = Integer.parseInt(args[i + 1]);
				System.out.println("\nI got " + p + " nodes!\n");
				if (p > 0)
					env.getParameters()
							.setInt("commonapi_testing_num_nodes", p);
				break;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-test") && i + 1 < args.length) {
				File file = new File(args[i + 1]);
				if (file.exists() && file.isFile() && file.canRead()) {
					cliEnv = false;
					System.out
							.println("\nNon cli based output has been selected!\n");

					System.out
							.println("\nA new test scenario is going to be used...\n");
					// Parse xml test and feed requestDispatcher with the
					// requests
					this.lastarg = args[i + 1];
				}
				break;
			}
		}

		return env;
	}

	/**
	 * process user input line args
	 */
	int parseLine(String line) {
		String args[] = line.split(" ", 2);
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("help")) {
				if (i + 1 < args.length) {

				} else {
					System.out
							.println("Type \"help <command>\" to print more information about the specific command\n");
					System.out
							.println("********************** COMMANDS ********************************************");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  1.indexcontent [-s <source number>] <content file>|<content directory>  *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  2.searchuser [-s <source number>] [-t <type number>]                    *");
					System.out
							.println("*    [-k <# of results] <query>                                            *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  3.importtest [<xml file>]                                               *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  4.stoptest                                                              *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  5.node (add [<uniquename>])|(delete <uniquename>|<nodenumber>)          *");
					System.out
							.println("*    |(show pending|profiles|shared|bookmarks <node number>)               *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  6.exit                                                                  *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  7.help [<command>]                                                      *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  8.createnodes                                                           *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("*  9.searchcontent [-s <source number>] [-t <type number>]                 *");
					System.out
							.println("*    [-k <# of results] <query>                                            *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 10.friends [-s <source number>][-d <destination num>][<message>]         *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 11.indexurl [-s <source number>] <url> <space separated tags>            *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 12.searchurl [-s <source number>] <url>                                  *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 13.tagcontent [-s <source number>] <content id>                          *");
					System.out
							.println("*    <user-node-number> <space separated tags>                             *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 14.searchsocialtags [-s <source number>] <user numbers comma separated>  *");
					System.out
							.println("*    | <user UIDs comma separated> <query>                                 *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 15.retrievecont [-s <source number>] <content id> <user id>              *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 16.index2 [-s <source number>] <identifier> <space separated zero-tags>  *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 17.stats                                                                 *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 18.clearstats                                                            *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("* 19.randomqueries -t <type of queries> -k <keywords>                      *");
					System.out
							.println("*                                                                          *");
					System.out
							.println("****************************************************************************");
					return HELP;
				}

			}

		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("exit")) {
				System.out.println("...Terminating Simulator\n\n");
				return EXIT;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("createnodes")) {
				return CREATENODES;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("importtest") && i + 1 < args.length) {
				lastarg = args[i + 1];
				return IMPORTTEST;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("stoptest")) {
				return STOPTEST;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("indexcontent") && i + 1 < args.length) {
				lastarg = args[i + 1];
				return INDEXCONTENT;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("indexurl") && i + 1 < args.length) {
				lastarg = args[i + 1];
				return INDEXURL;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("searchcontent") && i + 1 < args.length) {
				lastarg = args[i + 1]; // THE WHOLE QUERY
				return SEARCHCONTENT;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("searchurl") && i + 1 < args.length) {
				lastarg = args[i + 1]; // THE WHOLE QUERY
				return SEARCHURL;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("searchuser") && i + 1 < args.length) {
				lastarg = args[i + 1]; // THE WHOLE QUERY
				return SEARCHUSER;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("searchsocialtags") && i + 1 < args.length) {
				lastarg = args[i + 1]; // ALL ARGUMENTs
				return SEARCHSOCIALTAGS;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("node") && i + 1 < args.length) {
				lastarg = args[i + 1]; // THE WHOLE QUERY
				return NODE;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("friends")) {
				if (i + 1 < args.length)
					lastarg = args[i + 1]; // THE WHOLE QUERY
				else
					lastarg = null;
				return FRIENDS;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("tagcontent") && i + 1 < args.length) {
				lastarg = args[i + 1];
				return TAGCONTENT;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("retrievecont") && i + 1 < args.length) {
				lastarg = args[i + 1];
				return RETRIEVECONTENT;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("index2") && i + 1 < args.length) {
				lastarg = args[i + 1];
				return INDEXPSEUDOCONTENT;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("stats")) {
				return STATS;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("clearstats")) {
				return CLEARSTATS;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("randomqueries")) {
				if (i + 1 < args.length)
					lastarg = args[i + 1]; // THE WHOLE QUERY
				else
					lastarg = null;
				return RANDOMQUERIES;
			}
		}

		return -1;
	}

	// SAX parsing
	@SuppressWarnings("unchecked")
	public boolean parseXMLFileSAX(File f) {
		try {
			// Create the network only if it is not exist!
			if (requestDispatcher == null) {
				createRequestDispatcher();
			}
			System.out.println("--- SAX ---");
			SAXTestUnmarshaller saxUms = new SAXTestUnmarshaller();
			XMLReader rdr = XMLReaderFactory
					.createXMLReader("com.sun.org.apache.xerces.internal.parsers.SAXParser");
			// createXMLReader( "org.apache.xerces.parsers.SAXParser"
			// );//com.sun.org.apache.xerces.internal.parsers.SAXParser
			rdr.setContentHandler(saxUms);
			InputSource src = new InputSource(new FileInputStream(f));
			rdr.parse(src);
			// SAXParserFactory factory = SAXParserFactory.newInstance();
			// factory.setNamespaceAware(true);
			// SAXParser saxParser = factory.newSAXParser();
			// saxParser.parse(f, saxUms);
			// Feed with the scenario request and notify dispatcher
			// This request must precede all the others!!
			this.driver.pendingScenarios.addAll(saxUms.getScenarios());
			// When all scenarios have been fed Notify the dispatcher
			// And firstly add the first submitted scenario request
			this.driver.execRequests
					.add(this.driver.pendingScenarios.remove(0));
			doNotify();
		} catch (IOException ex) {
			ex.printStackTrace();
			// } catch (ParserConfigurationException ex) {
			// ex.printStackTrace();
		} catch (SAXException ex) {
			ex.printStackTrace();
		}
		return true;
	}

	// TODO : Fix this part!
	// DOM=MEMORY INTENSIVE APPROACH!!!!!
	public boolean parseXMLFile(File f) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();

			// We use DTD for XML validation (see the current directory)
			factory.setValidating(true);
			factory.setIgnoringElementContentWhitespace(true);

			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(f);

			Element root;
			Vector<org.w3c.dom.Node> scenarios = new Vector<org.w3c.dom.Node>();
			org.w3c.dom.Node tmp, nodes = null;

			root = doc.getDocumentElement();
			NodeList children = root.getChildNodes();

			int i = 0;
			// First Level
			while (i < children.getLength()) {
				tmp = children.item(i);
				if (((Element) tmp).getTagName().equals("scenario"))
					scenarios.add(tmp);
				else if (((Element) tmp).getTagName().equals("nodes"))
					nodes = tmp;
				i++;
			}

			// Create the network only if it is not exist!
			if (requestDispatcher == null)
				createRequestDispatcher();

			if (nodes != null) {
				// This case is used to manage nodes of the underlying network.
				// For example we can ADD, DELETE, UPDATE nodes etc.
				// TODO : Add functionality
			}

			if (scenarios.isEmpty())
				return true;

			System.out.println("Ready to issue requests");

			// These will hold the requests
			Vector<org.w3c.dom.Node> index = null;
			Vector<org.w3c.dom.Node> search = null;
			Vector<org.w3c.dom.Node> retrieve = null;
			Vector<org.w3c.dom.Node> tag = null;
			Vector<org.w3c.dom.Node> random_queries = null;

			// Iterate through each scenario
			for (org.w3c.dom.Node tmp2 : scenarios) {
				children = tmp2.getChildNodes();
				if (children == null)
					continue;

				// Reset to create new Objects for the next scenario
				index = null;
				search = null;
				retrieve = null;
				tag = null;
				random_queries = null;

				i = 0;
				// Second Level
				while (i < children.getLength()) {
					tmp = children.item(i);
					if (((Element) tmp).getTagName().equals("index")) {
						if (index == null)
							index = new Vector<org.w3c.dom.Node>();
						index.add(tmp);
					} else if (((Element) tmp).getTagName().equals("search")) {
						if (search == null)
							search = new Vector<org.w3c.dom.Node>();
						search.add(tmp);
					} else if (((Element) tmp).getTagName().equals("retrieve")) {
						if (retrieve == null)
							retrieve = new Vector<org.w3c.dom.Node>();
						retrieve.add(tmp);
					} else if (((Element) tmp).getTagName().equals("tag")) {
						if (tag == null)
							tag = new Vector<org.w3c.dom.Node>();
						tag.add(tmp);
					} else if (((Element) tmp).getTagName().equals(
							"random_queries")) {
						if (random_queries == null)
							random_queries = new Vector<org.w3c.dom.Node>();
						random_queries.add(tmp);
					}
					i++;
				}

				// Feed the requests
				executeScenario(index, search, retrieve, tag, random_queries);
			}

			// When all scenarios have been fed Notify the dispatcher
			// And firstly add the first submitted scenario request
			this.driver.execRequests
					.add(this.driver.pendingScenarios.remove(0));
			doNotify();

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * This method creates the RequestDispatcher thread which starts the network
	 * creation and initialization.
	 * 
	 */
	private void createRequestDispatcher() {
		try {
			if (driver == null)
				driver = new SimDriver(env);
			if (requestDispatcher == null) {
				requestDispatcher = new Thread() {

					public void run() {
						try {
							Thread.currentThread().setName("RequestDispatcher");
							driver.start();
						} catch (Exception e) {
							System.out
									.println("Error occured in thread \"RequestDispatcher\"!\n");
							e.printStackTrace();
						}
					}
				};
				requestDispatcher.start();
			}
		} catch (IOException ex) {
			System.out
					.println("Error occured in createRequestDispatcher during driver creation!\n"
							+ ex.getMessage());
		}
	}

	/**
	 * Gets the vectors for each request type and transfers the requests for the
	 * specific scenario to the requestDispatcher to execute them.
	 * 
	 * @param index
	 * @param search
	 * @param retrieve
	 * @param tag
	 */
	private void executeScenario(Vector<org.w3c.dom.Node> index,
			Vector<org.w3c.dom.Node> search, Vector<org.w3c.dom.Node> retrieve,
			Vector<org.w3c.dom.Node> tag, Vector<org.w3c.dom.Node> randomQueries) {

		// Vectors to fill in with requests
		Vector<Request> index_req = new Vector<Request>();
		Vector<Request> search_req = new Vector<Request>();
		Vector<Request> tag_req = new Vector<Request>();
		Vector<Request> randomQueries_req = new Vector<Request>();

		NodeList children;

		// INDEXING
		if (index != null) {
			for (org.w3c.dom.Node tmp : index) {
				children = tmp.getChildNodes();
				if (children == null)
					continue;
				org.w3c.dom.Node currentNode = children.item(0);
				// source
				// TODO : Include the choice of source in a request
				if (((Element) currentNode).getTagName().equals("source")) {

					int sourceNum = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					currentNode = children.item(1);
					if (((Element) currentNode).getTagName()
							.equals("directory")) {
						String dir = ((Text) currentNode.getFirstChild())
								.getData().trim();
						File directory = new File(dir);
						if (directory.exists() && directory.isDirectory()) {
							System.out.println("Directory " + dir
									+ " is being indexed now!");
							index_req.add(directoryIndexing(directory,
									sourceNum));
						} else
							System.out.println("Directory is not valid!");
					} else if (((Element) currentNode).getTagName().equals(
							"single_file")) {
						String sf = ((Text) currentNode.getFirstChild())
								.getData().trim();
						File sfile = new File(sf);
						if (sfile.exists() && sfile.isFile()) {
							System.out.println("File " + sf
									+ " is being indexed now!");
							index_req.add(new IndexContentRequest(sfile
									.getAbsolutePath(), sourceNum));
						} else
							System.out.println("Single File is not valid!");

						// user
					} else if (((Element) currentNode).getTagName().equals(
							"user")) {
						children = currentNode.getChildNodes();

						Map<String, String> profile = new HashMap<String, String>();
						// user_address
						currentNode = children.item(0);
						String udata = ((Text) currentNode.getFirstChild())
								.getData().trim();
						profile.put("user address", udata);
						// user_profile
						currentNode = children.item(1);
						children = currentNode.getChildNodes();
						if (children != null) {

							// <!ELEMENT user_profile (field*|keywords)>
							currentNode = children.item(0);
							// keywords
							if (((Element) currentNode).getTagName().equals(
									"keywords")) {
								udata = ((Text) currentNode.getFirstChild())
										.getData().trim();
								// default field
								profile.put("user description", udata);
								// field
							} else if (((Element) currentNode).getTagName()
									.equals("field")) {
								String name;
								for (int i = 0; i < children.getLength(); i++) {
									currentNode = children.item(i);
									// name
									if (currentNode.getFirstChild()
											.getFirstChild() != null
											&& currentNode.getLastChild()
													.getFirstChild() != null) {
										name = ((Text) currentNode
												.getFirstChild()
												.getFirstChild()).getData();
										// keywords
										udata = ((Text) currentNode
												.getLastChild().getFirstChild())
												.getData();
										profile.put(name.trim(), udata.trim());
									}
								}
							}
						}
						if (!profile.isEmpty()) {
							System.out.println("A user is being indexed now!");
							index_req.add(new IndexUserRequest(profile,
									sourceNum, "::"));
						} else
							System.out.println("User profile is empty!");

						// pseudo_file
					} else if (((Element) currentNode).getTagName().equals(
							"pseudo_file")) {
						children = currentNode.getChildNodes();

						Map<String, String> profile = new HashMap<String, String>();
						// identifier
						currentNode = children.item(0);
						String identifier = ((Text) currentNode.getFirstChild())
								.getData().trim();
						profile.put("Identifier", identifier);
						// content_profile
						currentNode = children.item(1);
						children = currentNode.getChildNodes();

						// <!ELEMENT content_profile (field*|keywords)>
						currentNode = children.item(0);
						// keywords
						String cdata;
						if (((Element) currentNode).getTagName().equals(
								"keywords")) {
							cdata = ((Text) currentNode.getFirstChild())
									.getData().trim();
							// default field
							profile.put("content description", cdata);
							// field
						} else if (((Element) currentNode).getTagName().equals(
								"field")) {
							String name;
							for (int i = 0; i < children.getLength(); i++) {
								currentNode = children.item(i);
								// name
								if (currentNode.getFirstChild().getFirstChild() != null
										&& currentNode.getLastChild()
												.getFirstChild() != null) {
									name = ((Text) currentNode.getFirstChild()
											.getFirstChild()).getData();
									// keywords
									cdata = ((Text) currentNode.getLastChild()
											.getFirstChild()).getData();
									profile.put(name.trim(), cdata.trim());
								}
							}
						}
						if (!profile.isEmpty()) {
							System.out
									.println("Some pseudo-content is being indexed now!");
							index_req.add(new IndexPseudoContentRequest(
									identifier, profile, sourceNum));
						} else
							System.out.println("Content profile is empty!");
					}

				} else if (((Element) currentNode).getTagName().equals(
						"directory")) {
					String dir = ((Text) currentNode.getFirstChild()).getData()
							.trim();
					File directory = new File(dir);
					if (directory.exists() && directory.isDirectory()) {
						System.out.println("Directory " + dir
								+ " is being indexed now!");
						index_req.add(directoryIndexing(directory));
					} else
						System.out.println("Directory is not valid!");
				} else if (((Element) currentNode).getTagName().equals(
						"single_file")) {
					String sf = ((Text) currentNode.getFirstChild()).getData()
							.trim();
					File sfile = new File(sf);
					if (sfile.exists() && sfile.isFile()) {
						System.out.println("File " + sf
								+ " is being indexed now!");
						index_req.add(new IndexContentRequest(sfile
								.getAbsolutePath()));
					} else
						System.out.println("Single File is not valid!");

					// user
				} else if (((Element) currentNode).getTagName().equals("user")) {

					children = currentNode.getChildNodes();
					Map<String, String> profile = new HashMap<String, String>();

					// user_address
					currentNode = children.item(0);
					String udata = ((Text) currentNode.getFirstChild())
							.getData().trim();
					profile.put("user address", udata);

					// user_profile
					currentNode = children.item(1);
					children = currentNode.getChildNodes();

					// <!ELEMENT user_profile (field*|keywords)>
					currentNode = children.item(0);

					// keywords
					if (((Element) currentNode).getTagName().equals("keywords")) {
						udata = ((Text) currentNode.getFirstChild()).getData()
								.trim();
						// default field
						profile.put("user description", udata);

						// field
					} else if (((Element) currentNode).getTagName().equals(
							"field")) {
						String name;
						for (int i = 0; i < children.getLength(); i++) {
							currentNode = children.item(i);
							// name
							name = ((Text) currentNode.getFirstChild()
									.getFirstChild()).getData().trim();
							// keywords
							udata = ((Text) currentNode.getLastChild()
									.getFirstChild()).getData().trim();
							profile.put(name, udata);
						}
					}

					if (!profile.isEmpty()) {
						System.out.println("A user is being indexed now!");
						index_req.add(new IndexUserRequest(profile));
					} else
						System.out.println("User profile is empty!");

					// pseudo_file

				} else if (((Element) currentNode).getTagName().equals(
						"pseudo_file")) {
					children = currentNode.getChildNodes();

					Map<String, String> profile = new HashMap<String, String>();
					// identifier
					currentNode = children.item(0);
					String identifier = ((Text) currentNode.getFirstChild())
							.getData().trim();
					profile.put("Identifier", identifier);
					// content_profile
					currentNode = children.item(1);
					children = currentNode.getChildNodes();

					// <!ELEMENT user_profile (field*|keywords)>
					currentNode = children.item(0);
					// keywords
					String cdata;
					if (((Element) currentNode).getTagName().equals("keywords")) {
						cdata = ((Text) currentNode.getFirstChild()).getData()
								.trim();
						// default field
						profile.put("content description", cdata);
						// field
					} else if (((Element) currentNode).getTagName().equals(
							"field")) {
						String name;
						for (int i = 0; i < children.getLength(); i++) {
							currentNode = children.item(i);
							// name
							name = ((Text) currentNode.getFirstChild()
									.getFirstChild()).getData().trim();
							// keywords
							cdata = ((Text) currentNode.getLastChild()
									.getFirstChild()).getData().trim();
							profile.put(name, cdata);
						}
					}
					if (!profile.isEmpty()) {
						System.out
								.println("Some pseudo-content is being indexed now!");
						index_req.add(new IndexPseudoContentRequest(identifier,
								profile));
					} else
						System.out.println("Content profile is empty!");
				}

			}
		}
		// Searching
		if (search != null) {
			// <!ELEMENT search (source?,query,number_of_results)>
			for (org.w3c.dom.Node tmp : search) {
				children = tmp.getChildNodes();
				if (children == null)
					continue;
				org.w3c.dom.Node currentNode = children.item(0);
				// source
				// TODO : Include the choice of source in a request
				if (((Element) currentNode).getTagName().equals("source")) {

					int sourceNum = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// query
					// <!ELEMENT query (user,content)>
					currentNode = children.item(1);
					NodeList qchildren = currentNode.getChildNodes();

					// query_type
					currentNode = qchildren.item(0);
					int type = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// // user
					// currentNode = qchildren.item(1);
					// NodeList uchildren = currentNode.getChildNodes();
					// // user_address
					// currentNode = uchildren.item(0);
					// // user_profile
					// currentNode = uchildren.item(1);

					// content
					currentNode = qchildren.item(1);
					NodeList cchildren = currentNode.getChildNodes();
					// keywords
					currentNode = cchildren.item(0);
					String queryTerms = ((Text) currentNode.getFirstChild())
							.getData().trim();

					// number_of_results
					currentNode = children.item(2);
					int k = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// Add the Request for search
					if (type == QueryPDU.CONTENTQUERY
							|| type == QueryPDU.CONTENT_ENHANCEDQUERY)
						search_req.add(new SearchContentRequest(queryTerms,
								type, sourceNum, k)); // Feed the whole query!
					else if (type == QueryPDU.USERQUERY
							|| type == QueryPDU.USER_ENHANCEDQUERY)
						search_req.add(new SearchUserRequest(queryTerms,
								type % 2, sourceNum, k)); // Feed the whole
															// query!

				} else if (((Element) currentNode).getTagName().equals("query")) {
					// query
					// <!ELEMENT query (user,content)>
					NodeList qchildren = currentNode.getChildNodes();

					// query_type
					currentNode = qchildren.item(0);
					int type = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// // user
					// currentNode = qchildren.item(1);
					// NodeList uchildren = currentNode.getChildNodes();
					// // user_address
					// currentNode = uchildren.item(0);
					// // user_profile
					// currentNode = uchildren.item(1);

					// content
					currentNode = qchildren.item(1);
					NodeList cchildren = currentNode.getChildNodes();
					// keywords
					currentNode = cchildren.item(0);
					String queryTerms = ((Text) currentNode.getFirstChild())
							.getData().trim();

					// number_of_results
					currentNode = children.item(1);
					int k = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// Add the Request for search
					if (type == QueryPDU.CONTENTQUERY
							|| type == QueryPDU.CONTENT_ENHANCEDQUERY)
						search_req.add(new SearchContentRequest(queryTerms,
								type, SearchContentRequest.RANDOMSOURCE, k)); // Feed
																				// the
																				// whole
																				// query!
					else if (type == QueryPDU.USERQUERY
							|| type == QueryPDU.USER_ENHANCEDQUERY)
						search_req.add(new SearchUserRequest(queryTerms,
								type % 2, SearchUserRequest.RANDOMSOURCE, k)); // Feed
																				// the
																				// whole
																				// query!
				}
			}
		}

		// RETRIEVE
		if (retrieve != null) {
			for (org.w3c.dom.Node tmp : retrieve) {
				children = tmp.getChildNodes();
				if (children == null)
					continue;
			}
		}

		// TAGGING
		if (tag != null) {
			for (org.w3c.dom.Node tmp : tag) {
				children = tmp.getChildNodes();
				if (children == null)
					continue;
				org.w3c.dom.Node currentNode = children.item(0);
				// source
				if (((Element) currentNode).getTagName().equals("source")) {

					int sourceNum = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// uid
					// <!ELEMENT uid DATA>
					currentNode = children.item(1);
					int uid = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// cid
					// <!ELEMENT cid DATA>
					currentNode = children.item(2);
					String cid = ((Text) currentNode.getFirstChild()).getData()
							.trim();

					// tags separated by ::
					currentNode = children.item(3);
					String tagTerms = ((Text) currentNode.getFirstChild())
							.getData().trim();

					// Add the Request for tagging
					tag_req.add(new TagContentRequest(tagTerms, cid, uid,
							sourceNum)); // Feed all the tag terms!

				} else if (((Element) currentNode).getTagName().equals("uid")) {
					// uid
					// <!ELEMENT uid>
					currentNode = children.item(0);
					int uid = Integer.parseInt(((Text) currentNode
							.getFirstChild()).getData().trim());

					// cid
					// <!ELEMENT cid DATA>
					currentNode = children.item(1);
					String cid = ((Text) currentNode.getFirstChild()).getData()
							.trim();

					// tags separated by ::
					currentNode = children.item(2);
					String tagTerms = ((Text) currentNode.getFirstChild())
							.getData().trim();

					// Add the Request for tagging
					tag_req.add(new TagContentRequest(tagTerms, cid, uid)); // Feed all the tag terms!
				}

			}
		}

		// RANDOMQUERIES
		if (randomQueries != null) {
			for (org.w3c.dom.Node tmp : randomQueries) {
				children = tmp.getChildNodes();
				if (children == null)
					continue;

				// TODO : Some more checking on null or existence
				// type
				org.w3c.dom.Node currentNode = children.item(0);
				int typeNum = Integer.parseInt(((Text) currentNode
						.getFirstChild()).getData().trim());

				// keywords
				currentNode = children.item(1);
				int keywords = Integer.parseInt(((Text) currentNode
						.getFirstChild()).getData().trim());

				// queries
				currentNode = children.item(2);
				int queries = Integer.parseInt(((Text) currentNode
						.getFirstChild()).getData().trim());

				// results
				currentNode = children.item(3);
				int results = Integer.parseInt(((Text) currentNode
						.getFirstChild()).getData().trim());

				randomQueries_req.add(new RandomQueriesRequest(typeNum,
						keywords, queries, results));
			}
		}

		// Feed with the scenario request and notify dispatcher
		// This request must precede all the others!!
		this.driver.pendingScenarios.add(new ScenarioRequest(index_req,
				search_req, tag_req, randomQueries_req));
		// this.driver.execRequests.add(new ScenarioRequest(index_req,
		// search_req, tag_req, randomQueries_req));

	}

	private Request parseNodeCommand(String lastarg) {

		String args[] = lastarg.trim().split(" ");
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("show") && i + 2 < args.length) {
				if (args[i + 1] == null || args[i + 2] == null) {
					return null;
				} else if (args[i + 1].equals("pending")) {
					int source = Integer.parseInt(args[i + 2]);
					return new StatsRequest(StatsRequest.PENDING, source);
				} else if (args[i + 1].equals("shared")) {
					int source = Integer.parseInt(args[i + 2]);
					return new StatsRequest(StatsRequest.SHARED, source);
				} else if (args[i + 1].equals("profiles")) {
					int source = Integer.parseInt(args[i + 2]);
					return new StatsRequest(StatsRequest.PROFILES, source);
				} else if (args[i + 1].equals("bookmarks")) {
					int source = Integer.parseInt(args[i + 2]);
					return new StatsRequest(StatsRequest.BOOKMARKS, source);
				} else {
					return null;
				}
			} else if (args[i].equals("add") && i + 1 < args.length) {
				// TODO : Implement this!
			} else if (args[i].equals("delete") && i + 1 < args.length) {
				// TODO : Implement this!
			}
		}

		return null;
	}

	/**
	 * Used to recursively index every single file in a dir and its subdirs!
	 * 
	 * @param f
	 */
	private IndexContentRequest directoryIndexing(File f) {
		return this.directoryIndexing(f, IndexContentRequest.RANDOMSOURCE);
	}

	private IndexContentRequest directoryIndexing(File f, int sourceNum) {
		File innerf;
		LinkedList<File> dir = new LinkedList<File>();
		dir.add(f);
		while (!dir.isEmpty()) {

			innerf = (File) dir.removeFirst();
			if (innerf.isDirectory()) {
				File[] subs = innerf.listFiles();
				if (subs.length == 0) {
					continue;
				} else {
					dir.addAll(Arrays.asList(subs));
					// dir.addLast(innerf);
				}
			} else {
				// Add the Request for indexing the file
				return new IndexContentRequest(innerf.getAbsolutePath(),
						sourceNum);
			}
		}
		return null;
	}

	/**
	 * notify the RequestDispatcher Thread that a request has been received
	 */
	public void doNotify() {
		if (driver != null) {
			// TODO: Check if the monitorobject creates deadlock when multiple
			// requests
			// are issued simultaneously
			synchronized (driver) {
				driver.wasSignalled = true;
				driver.notify();
			}
		}
	}

	/**
	 * PRIVATE CLASS VARIABLES
	 */
	private BufferedReader in;
	private Environment env;
	// Debugging private ContentProfileFactory cpf;
	private String lastarg;
	private SimDriver driver;
	private Thread requestDispatcher;
	private boolean cliEnv = true;
	@SuppressWarnings("unused")
	private HttpServerHandler hsh;

}