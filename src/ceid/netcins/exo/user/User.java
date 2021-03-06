package ceid.netcins.exo.user;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import rice.p2p.commonapi.Id;
import ceid.netcins.exo.catalog.CatalogEntry;
import ceid.netcins.exo.catalog.ContentCatalogEntry;
import ceid.netcins.exo.catalog.SocialCatalog;
import ceid.netcins.exo.catalog.UserCatalogEntry;
import ceid.netcins.exo.content.ContentProfile;
import ceid.netcins.exo.content.TermField;
import ceid.netcins.exo.messages.QueryPDU;
import ceid.netcins.exo.social.SocialBookMark;
import ceid.netcins.exo.social.TagCloud;

/**
 * This class represents a User entity. User includes all the necessary
 * information about a user.
 * 
 * @author <a href="mailto:loupasak@ceid.upatras.gr">Andreas Loupasakis</a>
 * @author <a href="mailto:ntarmos@cs.uoi.gr">Nikos Ntarmos</a>
 * @author <a href="mailto:peter@ceid.upatras.gr">Peter Triantafillou</a>
 * 
 * "eXO: Decentralized Autonomous Scalable Social Networking"
 * Proc. 5th Biennial Conf. on Innovative Data Systems Research (CIDR),
 * January 9-12, 2011, Asilomar, California, USA.
 */
public class User implements Serializable {
	private static final long serialVersionUID = 2795996785244748371L;
	public static final String UsernameTag = "Username";
	public static final String ResourceTag = "Resource";
	public static final String NotAvailableTag = "<N/A>";
	public static final String ScreennameDelimiter = "/";

	static public class SharedContentItem implements Serializable {
		private static final long serialVersionUID = 5355929555863726303L;
		private String filename;
		private ContentProfile profile;

		public SharedContentItem(String filename, ContentProfile profile) {
			this.filename = filename;
			this.profile = profile;
		}

		public String getFilename() { return filename; }
		public ContentProfile getProfile() { return profile; }
	}

	// User unique identifier created by SHA-1 hash function
	private Id uid;

	// User nick(screen) name in the network. This name will be indexed as a
	// field in the userProfile!
	private String username;

	private String resourceName;

	// This is the set of "terms" that describe the user.
	private ContentProfile userProfile;

	// The list of friends' UIDs
	private Hashtable<Id, Friend> friends;

	// Friendship requests pending to be confirmed by user
	private Hashtable<Id, FriendRequest> pendingIncomingFReq;

	// Friendship requests that user waits to be approved by his candidate friends
	private Set<Id> pendingOutgoingFReq;

	// Map of shared files with their corresponding SHA-1 checksums and 	content profile.
	// TIP : SHA-1 checksum is returned by "libextractor", so we need to use
	// buildId(String) to obtain the Id instance.
	private Map<Id, SharedContentInfo> sharedContent;

	// The content Ids (checksums) mapped with their corresponding TagCloud.
	// TAGEE's PERSPECTIVE (owner of content)
	private Map<Id, TagCloud> contentTagClouds;

	private Map<Id, TagCloud> userTagClouds;
	
	// The tag Ids mapped with their corresponding Content Profiles of contents,
	// which have been tagged by this user.
	// TAGER's PERSPECTIVE (non-owner of content)
	// alternatively Map<Id,SocialCatalog>
	private Map<String, SocialCatalog> invertedTagContentList;

	// Bookmarks : URLBookMarks etc.
	// Maps Bookmark Id (bid) to the set of tags, name, description etc.
	private Map<Id, SocialBookMark> bookMarks;

	// TODO: Implement this, Describe design etc.
	//private Map<Id, SocialLink> sociallinks;

	/**
	 * Constructor of user entity
	 * 
	 * @param uid
	 */
	public User(Id uid) {
		this(uid, null, (ContentProfile)null);
	}

	/**
	 * Constructor of user entity
	 * 
	 * @param uid
	 * @param username
	 */
	public User(Id uid, String username) {
		this(uid, username, (ContentProfile)null);
	}

	/**
	 * Constructor of user entity
	 * 
	 * @param uid
	 * @param username
	 * @param resourceName
	 */
	public User(Id uid, String username, String resourceName) {
		this(uid, username, resourceName, null, null);
	}

	/**
	 * Constructor of user entity
	 * 
	 * @param username
	 * @param userProfile
	 */
	public User(Id uid, String username, ContentProfile userProfile) {
		this(uid, username, null, userProfile, null);
	}

	/**
	 * Constructor of user entity
	 * 
	 * @param username
	 * @param userProfile
	 * @param friends
	 */
	public User(Id uid, String username, String resourceName, ContentProfile userProfile,
			Hashtable<Id, Friend> friends) {
		this.uid = uid;
		this.username = username;
		this.resourceName = resourceName;
		if ((username != null && username.contains(ScreennameDelimiter)) ||
				(resourceName != null && resourceName.contains(ScreennameDelimiter)))
			throw new RuntimeException("Username and resource name cannot contain '" +
					ScreennameDelimiter + "'");

		setUserProfile(userProfile);

		if (friends == null)
			this.friends = new Hashtable<Id, Friend>();
		else
			this.friends = friends;
		this.pendingIncomingFReq = new Hashtable<Id, FriendRequest>();
		this.pendingOutgoingFReq = Collections.synchronizedSet(new HashSet<Id>());
		this.sharedContent = new Hashtable<Id, SharedContentInfo>();
		this.bookMarks = new Hashtable<Id, SocialBookMark>();
		this.contentTagClouds = new Hashtable<Id, TagCloud>();
		this.userTagClouds = new Hashtable<Id, TagCloud>();
		this.invertedTagContentList = new Hashtable<String, SocialCatalog>();
	}

	/**
	 * Helper to get the proper entries corresponding to the query type issued
	 * by the user.
	 * 
	 * @param type One of the types defined in QueryPDU
	 * @return Return the corresponding vector of catalog entries.
	 */
	public Vector<CatalogEntry> getCatalogEntriesForQueryType(int type, Id requester){
		Vector<CatalogEntry> v = new Vector<CatalogEntry>();
		switch(type){
			case QueryPDU.USERQUERY:
			case QueryPDU.USER_ENHANCEDQUERY:
				v.add(this.wrapUserProfileToCatalogEntry(isFriend(requester)));
				break;
			case QueryPDU.HYBRIDQUERY:
			case QueryPDU.HYBRID_ENHANCEDQUERY:
				v.add(this.wrapUserProfileToCatalogEntry(isFriend(requester)));
				v.addAll(this.wrapContentToCatalogEntries(isFriend(requester)));
				break;
			case QueryPDU.CONTENTQUERY:
			case QueryPDU.CONTENT_ENHANCEDQUERY:
				v.addAll(this.wrapContentToCatalogEntries(isFriend(requester)));
				break;
		}
		return v; 
	}

	/**
	 * Helper to wrap the user profile to UserCatalogEntry.
	 * This is useful for similarity processing. E.g. to be included in a 
	 * SimilarityRequest.
	 * 
	 * @param completeUserProfile Choose if we will include the public or the 
	 * complete version of the user profile (if the queried user is friend ...)
	 * @return The wrapped user profile.
	 */
	public UserCatalogEntry wrapUserProfileToCatalogEntry(
			boolean completeUserProfile){
		return new UserCatalogEntry(this.uid, completeUserProfile?
				this.getCompleteUserProfile():
					this.getPublicUserProfile());
	}

	public Vector<ContentCatalogEntry> wrapContentToCatalogEntries(){
		return this.wrapContentToCatalogEntries(false, false);
	}

	public Vector<ContentCatalogEntry> wrapContentToCatalogEntries(
			boolean completeUserProfile){
		return this.wrapContentToCatalogEntries(true, completeUserProfile);
	}

	/**
	 * Helper to wrap the shared content profiles to ContentCatalogEntries.
	 * This is useful to form a list for similarity processing. E.g. to include
	 * in a SimilarityRequest. 
	 * 
	 * @param includeUserProfile Flag to denote if user profile is needed to 
	 * be wrapped too.
	 * @param completeUserProfile In case we want to include the user profile
	 * choose if we will include the public or the complete version.
	 * @return A vector with all the content profiles wrapped to catalog entries.
	 */
	public Vector<ContentCatalogEntry> wrapContentToCatalogEntries(
			boolean includeUserProfile,	boolean completeUserProfile){
		Vector<ContentCatalogEntry> v = new Vector<ContentCatalogEntry>();
		for(Id id : this.sharedContent.keySet()){
			v.add(new ContentCatalogEntry(this.uid,
					sharedContent.get(id).getProfile(), 
					includeUserProfile?
							(completeUserProfile?
									this.getCompleteUserProfile():
										this.getPublicUserProfile()): null));
		}
		return v;
	}

	/**
	 * Given an Id, determine if the its owner belongs to user friends. 
	 * 
	 * @param user The id of the checking user
	 * @return True if he is a friend, false if he is not.
	 */
	public boolean isFriend(Id user){
		return friends.containsKey(user);
	}

	public void setUserProfile(ContentProfile userProfile) {
		if (userProfile != null)
			this.userProfile = new ContentProfile(userProfile);
		else
			this.userProfile = new ContentProfile();

		// (Re-)Set the Username and Resource in the user's profile from the 
		// current User object (ignoring any relevant data provided)
		this.userProfile.add(new TermField(UsernameTag, username, true));
		this.userProfile.add(new TermField(ResourceTag, resourceName, true));
	}

	public ContentProfile getCompleteUserProfile() {
		return userProfile;
	}

	public ContentProfile getPublicUserProfile() {
		return userProfile.getPublicPart();
	}

	public Hashtable<Id, Friend> getFriends() {
		return friends;
	}

	public Id getUID() {
		return uid;
	}

	public String getUsername() {
		return username;
	}

	public String getResourceName() {
		return resourceName;
	}

	public String getScreenName() {
		return username + ScreennameDelimiter + resourceName;
	}

	public Hashtable<Id, FriendRequest> getPendingIncomingFReq() {
		return pendingIncomingFReq;
	}

	public Set<Id> getPendingOutgoingFReq() {
		return pendingOutgoingFReq;
	}

	public Map<Id, SharedContentInfo> getSharedContent() {
		return sharedContent;
	}

	public Set<Id> getSharedContentIDs() {
		return sharedContent.keySet();
	}

	public ContentProfile getSharedContentProfile(Id id) {
		SharedContentInfo sci = sharedContent.get(id);
		return (sci == null) ? null : sci.getProfile();
	}

	public Map<Id, ContentProfile> getSharedContentProfiles() {
		Map<Id, ContentProfile> ret = new Hashtable<Id, ContentProfile>();
		Iterator<Id> keys = sharedContent.keySet().iterator();
		Iterator<SharedContentInfo> values = sharedContent.values().iterator();
		while (keys.hasNext())
			ret.put(keys.next(), values.next().getProfile());
		return ret;
	}

	public Map<Id, SharedContentItem> getSharedContentItems() {
		Map<Id, SharedContentItem> ret = new Hashtable<Id, User.SharedContentItem>();
		Iterator<Id> keys = sharedContent.keySet().iterator();
		Iterator<SharedContentInfo> values = sharedContent.values().iterator();
		while (keys.hasNext()) {
			SharedContentInfo cInfo = values.next();
			ret.put(keys.next(), new SharedContentItem(cInfo.getFilename(), cInfo.getProfile()));
		}
		return ret;
	}

	public Map<Id, SocialBookMark> getBookMarks() {
		return bookMarks;
	}

	public Map<Id, TagCloud> getContentTagClouds() {
		return contentTagClouds;
	}

	public Map<Id, TagCloud> getUserTagClouds() {
		return userTagClouds;
	}

	public Map<String, SocialCatalog> getTagContentList() {
		return invertedTagContentList;
	}

	/**
	 * Add a friend's uid in the friend list
	 * 
	 * @param friend
	 */
	public void addFriend(Friend friend) {
		friends.put(friend.getUID(), friend);
	}

	public void addPendingIncomingFReq(FriendRequest freq) {
		pendingIncomingFReq.put(freq.getUID(), freq);
	}

	public void addPendingOutgoingFReq(Id uid) {
		pendingOutgoingFReq.add(uid);
	}

	public void addSharedContent(Id checksum, File file, ContentProfile profile) {
		sharedContent.put(checksum, new SharedContentInfo(file, null, profile));
	}

	public void addSharedContent(Id checksum, File file) {
		addSharedContent(checksum, file, null);
	}

	public void addSharedContentProfile(Id checksum, String identifier, ContentProfile cp) {
		SharedContentInfo sci = sharedContent.get(checksum);
		if (sci == null)
			sci = new SharedContentInfo(null, identifier, cp);
		else {
			sci.setProfile(cp);
			if (identifier != null)
				sci.setFilename(identifier);
		}
		sharedContent.put(checksum, sci);
		// XXX: Should we also update the content tag clouds?
		/*
		TagCloud tc = contentTagClouds.get(checksum);
		if (tc == null) {
			tc = new TagCloud();
		}
		for (ContentField cf : cp.getAllFields())
			tc.addTagTFMap(cf);
		contentTagClouds.put(checksum, tc);
		*/
	}

	public void addBookMark(Id bid, SocialBookMark sbm) {
		bookMarks.put(bid, sbm);
	}

	public void addContentTagCloud(Id cid, TagCloud tc) {
		contentTagClouds.put(cid, tc);
	}

	public void addTagContentList(String tag, SocialCatalog cat) {
		invertedTagContentList.put(tag, cat);
	}

	public void removeFriend(Friend friend) {
		friends.remove(friend.getUID());
	}

	public void removePendingIncomingFReq(FriendRequest freq) {
		pendingIncomingFReq.remove(freq.getUID());
	}

	public void removePendingOutgoingFReq(Id uid) {
		pendingOutgoingFReq.remove(uid);
	}

	public void removeSharedContent(Id checksum) {
		sharedContent.remove(checksum);
	}

	public void removeBookMark(Id bid) {
		bookMarks.remove(bid);
	}

	public void removeContentTagCloud(Id cid) {
		contentTagClouds.remove(cid);
	}

	public void removeTagContentList(String tag) {
		invertedTagContentList.remove(tag);
	}
}
