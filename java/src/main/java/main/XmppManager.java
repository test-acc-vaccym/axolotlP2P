package main;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
//import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;


public class XmppManager implements commManager {
    
	 	
	private static final int packetReplyTimeout = 500; // millis
	    private final String SERVER = "ben-probook";
	    private final int PORT = 5222;
	    private String server;
	    private int port;
	    
	    private ConnectionConfiguration config;
	    private XMPPConnection connection;

	    private ChatManager chatManager;
	    private MessageListener messageListener;
	    
	    
	    private XmppManager(){
	    	server = SERVER;
	    	port = PORT;
	    }
	    
	    static public XmppManager createManager() throws XMPPException {
	    	XmppManager manager = new XmppManager();	    	
	    	try {
				manager.init();
			} 
	    	catch (XMPPException e) {
	    		manager = null;
				throw e;
			}
	    	return manager;
	    }
	    
	    private void init() throws XMPPException {
	    	
	        
	        System.out.println(String.format("Initializing connection to server %1$s port %2$d", server, port));

	        SmackConfiguration.setPacketReplyTimeout(packetReplyTimeout);
	        
	        config = new ConnectionConfiguration(server, port);
	        
	        config.setSASLAuthenticationEnabled(false);
	        config.setSecurityMode(SecurityMode.disabled);
	        
	        connection = new XMPPConnection(config);
	        
	        connection.connect();
	        
	        System.out.println("Connected: " + connection.isConnected());
	        
	        chatManager = connection.getChatManager();
	        setMessageReciver();
	        
	    }
	    public void setMessageReciver(){
	    	messageListener = new XmppMessageListener();
	    }  
	    
	    
	    public void userLogin(String username, String password) throws XMPPException {
	        if (connection!=null && connection.isConnected()) {
	            connection.login(username, password);
	        }
	    }

	    public void setStatus(boolean available, String status) {
	        
	        Presence.Type type = available? Type.available: Type.unavailable;
	        Presence presence = new Presence(type);	        
	        presence.setStatus(status);
	        connection.sendPacket(presence);
	        
	    }
	    
	    public void disconnect() {
	        if (connection!=null && connection.isConnected()) {
	            connection.disconnect();
	        }
	    }
	    
	    public void sendMessage(String message, String buddyJID) throws XMPPException {
	        System.out.println(String.format("Sending mesage '%1$s' to user %2$s", message, buddyJID));
	        Chat chat = chatManager.createChat(buddyJID, messageListener);
	        chat.sendMessage(message);
	    }
	    
	    public void createEntry(String user, String name) throws Exception {
	        System.out.println(String.format("Creating entry for buddy '%1$s' with name %2$s", user, name));
	        Roster roster = connection.getRoster();
	        roster.createEntry(user, name, null);
	    }
	    	    
	}
