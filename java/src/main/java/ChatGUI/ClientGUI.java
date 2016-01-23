package ChatGUI;

import ChatCommons.*;
import main.XmppManager;
import org.jivesoftware.smack.XMPPException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.FileNotFoundException;
import java.util.Date;
import java.text.SimpleDateFormat;
import org.whispersystems.libaxolotl.*;
import security.conversation.DecryptedPackage;
import security.management.SecureConversation;
import security.management.SecureParty;
import security.trust.concrete.FingerprintWG;
import security.trust.concrete.FingerprintWitness;
import security.trust.concrete.PersistentTrustStore;
import security.utils.HexHumanizer;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.util.Enumeration;

public class ClientGUI extends JFrame implements ActionListener, INotifier, IChatSender
{
	private JLabel label;
	private JLabel labelKS;
	private JTextField tfMessage;
	private JTextField tfServer, tfPort, tfRetransmit;
	private JPasswordField tfPassword;
	private JTextField tfUser;
	private JButton login, retransmitBtn, whoIsIn, btnCreateKS;
	private JTextArea ta;
	private boolean connected;
	private int defaultPort;
	private String defaultHost;
	private JList<User> listOfUsers;
	private DefaultListModel<User> listModel;
	private XmppManager xmppManager;
	private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
	private JTextField tfFingerPrint;
	private SecureParty party1 = null;
	private JTextField tfPathKS;
	private PersistentTrustStore store1 = null;
	private HexHumanizer hexHumanizer = null;
	private SecureConversation secureConversation = null;

	ClientGUI(String host, int port)
	{
		super("Chat Client");
		defaultPort = port;
		defaultHost = host;

		tfRetransmit = new JTextField();
		// The NorthPanel with:
		JPanel northPanel = new JPanel(new GridLayout(5,1));
		// the server name anmd the port number
		JPanel serverAndPort = new JPanel(new GridLayout(1,5, 1, 3));
		// the two JTextField with default value for server address and port number
		tfServer = new JTextField(host);
		tfPort = new JTextField("" + port);
		tfPort.setHorizontalAlignment(SwingConstants.LEFT);

		serverAndPort.add(new JLabel("Server Address:  "));
		serverAndPort.add(tfServer);
		serverAndPort.add(new JLabel("Port Number:  "));
		serverAndPort.add(tfPort);
		serverAndPort.add(new JLabel(""));

		JPanel userAndPasswordPanel = new JPanel(new GridLayout(1,5,1,3));
		// the two JTextField with default value for server address and port number
		tfUser = new JTextField("user1");
		tfPassword = new JPasswordField("crypto");
		tfPassword.setHorizontalAlignment(SwingConstants.LEFT);

		userAndPasswordPanel.add(new JLabel("User Name:  "));
		userAndPasswordPanel.add(tfUser);
		userAndPasswordPanel.add(new JLabel("Password:  "));
		userAndPasswordPanel.add(tfPassword);
		userAndPasswordPanel.add(new JLabel(""));

		northPanel.add(serverAndPort);
		northPanel.add(userAndPasswordPanel);

		JPanel ksPanel = new JPanel(new GridLayout(1,5, 1, 3));
		ksPanel.add(new JLabel("Key Store Path:"));
		tfPathKS = new JTextField("C:\\ks");
		ksPanel.add(tfPathKS);
		ksPanel.add(new JLabel("ReTransmit ID:  "));
		ksPanel.add(tfRetransmit);
		ksPanel.add(new JLabel(""));

		northPanel.add(ksPanel);

		label = new JLabel("Login and then Enter your message below", SwingConstants.CENTER);
		northPanel.add(label);

		tfMessage = new JTextField("");
		tfMessage.setEnabled(false);
		tfMessage.setBackground(Color.WHITE);
		northPanel.add(tfMessage);

		add(northPanel, BorderLayout.NORTH);

		ta = new JTextArea("Welcome to the Chat room\n", 80, 80);
		JPanel centerPanel = new JPanel(new GridLayout(1,2));

		User user1 = new User("user1", eUserStatus.Offline);
		User user2 = new User("user2", eUserStatus.Offline);
		User user3 = new User("user3", eUserStatus.Offline);
		listModel = new DefaultListModel<>();
		listModel.addElement(user1);
		listModel.addElement(user2);
		listModel.addElement(user3);

		listOfUsers = new JList<>(listModel);
		centerPanel.add(new JScrollPane(listOfUsers));
		listOfUsers.setCellRenderer(new UserRenderer());

		centerPanel.add(new JScrollPane(ta));
		ta.setEditable(false);
		add(centerPanel, BorderLayout.CENTER);

		listOfUsers.addMouseListener(new MouseAdapter(){
			public void mouseClicked(MouseEvent mouseEvent) {
				JList theList = (JList) mouseEvent.getSource();
				if (mouseEvent.getClickCount() == 2) {
					int index = theList.locationToIndex(mouseEvent.getPoint());
					if (index >= 0) {
						User o = (User)theList.getModel().getElementAt(index);
						System.out.println("Double-clicked on: " + o.toString());
						//o.SetUserStatus(eUserStatus.Offline);
					}
				}
			}
		});

		// the 3 buttons
		login = new JButton("Login");
		login.addActionListener(this);
		retransmitBtn = new JButton("ReTransmit");
		retransmitBtn.addActionListener(this);
		retransmitBtn.setEnabled(false);
		whoIsIn = new JButton("Witness");
		whoIsIn.addActionListener(this);
		whoIsIn.setEnabled(false);
		btnCreateKS = new JButton("Create KS");
		btnCreateKS.addActionListener(this);
		tfFingerPrint = new JTextField("Your Finger Print...");
		tfFingerPrint.setEditable(false);
		tfFingerPrint.setBackground(Color.WHITE);
		JPanel southPanel = new JPanel(new GridLayout(2,2));
		JPanel buttonPanel = new JPanel(new GridLayout(1,4));

		buttonPanel.add(login);
		buttonPanel.add(retransmitBtn);
		buttonPanel.add(whoIsIn);
		buttonPanel.add(btnCreateKS);
		southPanel.add(tfFingerPrint);
		southPanel.add(buttonPanel);

		add(southPanel, BorderLayout.SOUTH);

		// add(southPanel2,BorderLayout.SOUTH);

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(600, 600);
		setVisible(true);
		tfMessage.requestFocus();
	}

	// called by the Client to append text in the TextArea
	void append(String str) {
		ta.append(str);
		ta.setCaretPosition(ta.getText().length() - 1);
	}

	// called by the GUI is the connection failed
	// we reset our buttons, label, textfield
	void connectionFailed() {
		login.setEnabled(true);
		retransmitBtn.setEnabled(false);
		whoIsIn.setEnabled(false);
		// reset port number and host name as a construction time
		tfPort.setText("" + defaultPort);
		tfServer.setText(defaultHost);
		// let the user change them
		tfServer.setEditable(false);
		tfPort.setEditable(false);
		// don't react to a <CR> after the username
		tfMessage.removeActionListener(this);
		connected = false;
	}

	private JList GetFriendsList()
	{
		String listUsers[] =
				{
						"user1",
						"user2",
						"user3",
				};

		return new JList<>(listUsers);
	}

	private void Retransmit()
	{

	}

	public void actionPerformed(ActionEvent e) {
		Object o = e.getSource();

		if (o == login)
		{
			Login();

			tfMessage.setText("");
		}
		else if (o == retransmitBtn)
		{
			Retransmit();
		}
		else if (o == btnCreateKS)
		{
			String userName = tfUser.getText().trim();
			String password = new String(tfPassword.getPassword());

			try {
				store1 = new PersistentTrustStore(String.format("%s/%s.ks", tfPathKS.getText(), userName), password, true);
				System.out.println("finish create trust store");
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}

			tfMessage.setText("");
		}
		else if (o == whoIsIn)
		{
			try
			{
				String result = JOptionPane.showInputDialog(this, "Please enter the finger print", "Finger Print Witness",JOptionPane.INFORMATION_MESSAGE);
				String userName = listOfUsers.getSelectedValue().GetUserName();

				if (result !=null)
				{
					if (party1.consumeIdentityWitness(listOfUsers.getSelectedValue().GetUserName(),
							new FingerprintWitness(hexHumanizer.dehumanize(result))))
					{
						int index = GetIndexOfUserName(userName);
						User fromUser = listOfUsers.getModel().getElementAt(index);
						fromUser.SetUserStatus(eUserStatus.Trusted);
						listOfUsers.updateUI();
					}
				}

			}
			catch (Exception ex)
			{
				ex.printStackTrace();

			}

			tfMessage.setText("");
		}
		else if (connected)  // Send msg
		{
			SendMSG(listOfUsers.getSelectedValue().GetUserName(),tfMessage.getText());
		}
	}

	private void SendMSG(String sendTo, String text)
	{
		try
		{
			if (party1.isSessionInitialized(sendTo))
			{
				secureConversation.sendMessage(text);
				append(dateFormat.format(new Date()) + " " + tfUser.getText() + ": " + tfMessage.getText() + "\n");
				tfMessage.setText("");
			}
			else
			{
				java.util.List<User> users = listOfUsers.getSelectedValuesList();

				for (User user : users)
				{
					user.SetUserStatus(eUserStatus.Wait);
					listOfUsers.updateUI();

					String keyExchange = party1.createKeyExchangeMessage(user.GetUserName());
					xmppManager.sendMessage(keyExchange, user.GetUserName(), eMessageType.eKEY_START);
				}
			}
		} catch (Exception ex) {
			System.out.println("System Error");
			ex.printStackTrace();
		}
	}

	private void Login(){
		ChangeGUIWhenLoginPressed();

		String userName = tfUser.getText().trim();
		String password = new String(tfPassword.getPassword());

		if (store1 == null)
		{
			try {
				store1 = new PersistentTrustStore(String.format("%s/%s.ks", tfPathKS.getText(), userName), password, false);
			} catch (KeyStoreException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				try {
					store1 = new PersistentTrustStore(String.format("%s/%s.ks", tfPathKS.getText(), userName), password, true);
				} catch (Exception ex) {
					e.printStackTrace();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		try
		{
			party1 = new SecureParty(userName, store1, new FingerprintWG());
			secureConversation = new SecureConversation(party1, this);
		} catch (Exception e) {
			e.printStackTrace();
		}

		OpenXMPPConnection(userName,password);

		GenerateWitness();
	}

	private void GenerateWitness()
	{
		String witnessRaw = party1.generateWitness().serialize();

		try
		{
			String current = new java.io.File(getClass().getClassLoader().getResource("64K_english_dict.dic").getFile()).getCanonicalPath();
			hexHumanizer = new HexHumanizer (current);
			String humanized = hexHumanizer.humanize(witnessRaw);
			tfFingerPrint.setText(humanized);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void ChangeGUIWhenLoginPressed()
	{
		tfMessage.setText("");
		label.setText("Enter your message below");
		tfMessage.setEnabled(true);
		connected = true;

		login.setEnabled(false);
		retransmitBtn.setEnabled(true);
		whoIsIn.setEnabled(true);
		tfServer.setEditable(false);
		tfPort.setEditable(false);
		tfUser.setEditable(false);
		tfPassword.setEditable(false);
		tfPathKS.setEditable(false);
		tfMessage.addActionListener(this);
	}

	private void OpenXMPPConnection(String userName, String password)
	{
		try
		{
			xmppManager = XmppManager.createManager(tfServer.getText());
			xmppManager.addNotifier(this);
			xmppManager.userLogin(userName, password);

			RemoveUserFromList(userName);
			System.out.println("Connected User Name is: " + userName);

			ConnectedWithFriends(userName);
		}
		catch (XMPPException e)
		{
			e.printStackTrace();
			connectionFailed();
		}
	}

	private void RemoveUserFromList(String userName)
	{
		int index = GetIndexOfUserName(userName);
		listModel.remove(index);
		listOfUsers.updateUI();

	}

	private void ConnectedWithFriends(String userName) throws XMPPException
	{
		userName = userName.toLowerCase();
		if (userName.equals("user1"))
		{
			secureConversation.addPeer("user2");
			secureConversation.addPeer("user3");

		}
		else if (userName.equals("user2"))
		{
			secureConversation.addPeer("user1");
			secureConversation.addPeer("user3");
		}
		else
		{
			secureConversation.addPeer("user1");
			secureConversation.addPeer("user2");
		}
	}

	public static void main(String[] args)
	{
		new ClientGUI("dell", 5222);
	}

	private int GetIndexOfUserName(String name)
	{
		Enumeration<User> enumeration = listModel.elements();
		while (enumeration.hasMoreElements())
		{
			User currUser =enumeration.nextElement();
			if (currUser.GetUserName().equals(name))
			{
				return listModel.indexOf(currUser);
			}
		}

		return -1;
	}

	private User GetUserFromName(String userName)
	{
		String from = userName.split("@")[0];
		int index = GetIndexOfUserName(from);
		return listOfUsers.getModel().getElementAt(index);
	}

	private void UpdateUserStauts(User user, eUserStatus status)
	{
		user.SetUserStatus(status);
		listOfUsers.updateUI();
	}

	public void RecieveMessage(String from, String Message,eMessageType messageType)
	{
		User fromUser = GetUserFromName(from);

		switch (messageType)
		{
			case eKEY_START:
			{
				try
				{
					if (party1.consumeKeyExchangeMessage(from, Message))
					{
						UpdateUserStauts(fromUser, eUserStatus.Trusted);
						append("Started trusted conversation with: " + from + "\n");
					}
					else
					{
						UpdateUserStauts(fromUser, eUserStatus.UnTrusted);
						append("Started untrusted conversation with: " + from + "\n");
					}

					String keyExchange = party1.createKeyExchangeMessage(from);
					xmppManager.sendMessage(keyExchange, from, eMessageType.eKEY_RESPONSE);
				}
				catch (UnrecoverableEntryException e) {
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				} catch (KeyStoreException e) {
					e.printStackTrace();
				} catch (UntrustedIdentityException e) {
					e.printStackTrace();
				} catch (InvalidKeyException e) {
					e.printStackTrace();
				} catch (XMPPException e) {
					e.printStackTrace();
				}

				break;
			}
			case eKEY_RESPONSE:
			{
				try
				{
					if (party1.consumeKeyExchangeMessage(from, Message))
					{
						UpdateUserStauts(fromUser, eUserStatus.Trusted);
						append("Started trusted conversation with: " + from + "\n");
					}
					else
					{
						UpdateUserStauts(fromUser, eUserStatus.UnTrusted);
						append("Started untrusted conversation with: " + from + "\n");
					}

					if (party1.isSessionInitialized(from))
					{
						String msg = tfMessage.getText();
						secureConversation.sendMessage(msg);
						append(dateFormat.format(new Date()) + " " + tfUser.getText() + ": " + tfMessage.getText() + "\n");
						tfMessage.setText("");
					}
				} catch (UnrecoverableEntryException e) {
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				} catch (KeyStoreException e) {
					e.printStackTrace();
				} catch (UntrustedIdentityException e) {
					e.printStackTrace();
				} catch (InvalidKeyException e) {
					e.printStackTrace();
				}

				break;
			}
			case eNORMAL:
			{
				try
				{
					DecryptedPackage dp= secureConversation.receiveMessage(from, Message);
					String display = String.format("%s[%d/%d]:%s", from, dp.getIndex(), dp.getLastChainIndex(), dp.getContent());
					append(dateFormat.format(new Date()) + " " +  display + "\n");
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
				break;
			}
		}
	}

	@Override
	public void sendMessage(String peer, String Message)
	{
		try {
			xmppManager.sendMessage(Message,peer,eMessageType.eNORMAL);
		} catch (XMPPException e) {
			e.printStackTrace();
		}
	}
}