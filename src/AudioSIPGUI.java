import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.sql.rowset.JoinRowSet;
import javax.sql.rowset.Joinable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Pipeline;
import org.gstreamer.swing.VideoComponent;

import com.sun.jna.platform.win32.COM.DispatchVTable.GetTypeInfoCountCallback;


public class AudioSIPGUI {
	private JFrame frame;
	private JPanel infoPanel;
	private JButton callButton;
	private JButton stopButton;
	private Pipeline pipeline;
	private JLabel connectionLabel;
	private JLabel listLabel;
	private GridBagConstraints gbc;

	private SIPClient client;
	
	private String[] connections = new String[4];
	
	private ArrayList<Label> contactList = new ArrayList<>();
	
	private final String CONTACTS_FILE = "contacts";
	
	//Invite someone
	private ActionListener callClick = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			//create a dialog box
			String address = (String)JOptionPane.showInputDialog(frame, "Enter address:\n","Callee address", JOptionPane.PLAIN_MESSAGE,null, null,"");
			System.out.println("Join dialog retured "+address);
			if (address != null && address.length() > 0) {
				connectionLabel.setText("<html>Calling<br>"+address);
				client.call(address);
			}
		}
	};
	
	//Stop a conference
	private ActionListener stopClick = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			System.out.println("Stop");
			if(client.getState()==client.BUSY){
				client.closeCall();
				connectionLabel.setText("No connection");
			}
		}
	};
	
	private MouseListener clickTextToCall = new MouseListener() {
		@Override
		public void mouseReleased(MouseEvent e) {}
		@Override
		public void mousePressed(MouseEvent e) {}
		@Override
		public void mouseExited(MouseEvent e) {}
		@Override
		public void mouseEntered(MouseEvent e) {}
		
		// double click name on buddy list to call
		@Override
		public void mouseClicked(MouseEvent e) {
			if (e.getClickCount() == 2) {
				String address = e.getComponent().getName();
				System.out.println(address);
				connectionLabel.setText("<html>Calling<br>"+address);
				client.call(address);
			}
		}
	};
	
	//Receiving call- accept or deny
	public void receivingCall(String calleeURI){
		connectionLabel.setText("Receiving call");
					
		int option = JOptionPane.showConfirmDialog(null, "Do you accept the call from "+calleeURI+"?", "Receiving call", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
					
		if(option == JOptionPane.OK_OPTION){
		  System.out.println("Accept");
		  connectionLabel.setText("<html>Calling<br>"+calleeURI);
		  client.pickUp(calleeURI);
		}
		else{
			System.out.println("Denied");
			client.declineCall(calleeURI);
			connectionLabel.setText("No connection");
		}
	}
	
	private void joinServer() {
		if (client.getState() != client.PREINIT) {
			client.disconnectFromServer();
		}
		
		//Join Server
        String address = (String)JOptionPane.showInputDialog(frame, "Enter server address:\n","Server address", JOptionPane.PLAIN_MESSAGE,null, null,"");
		System.out.println("Join dialog retured "+address);
		if (address != null && address.length() > 0) {
			String[] banana=address.split(":");
			client.connectToServer(banana[0],Integer.valueOf(banana[1]));
			
		}
	}
	
	private void addContactPopup() {
		String address = (String)JOptionPane.showInputDialog(frame, "Enter contact address:\n","Add contact", JOptionPane.PLAIN_MESSAGE,null, null,"");
		System.out.println("Contact dialog retured "+address);
		if (address != null && address.length() > 0) {
			addContact(address);
		}
	}
	
	private void addContact(String address) {
		Label name = new Label(address);
		name.setName(address);
		name.setForeground(Color.white);
		name.addMouseListener(clickTextToCall);
		
		contactList.add(name);
		
		gbc.gridx = 0;
		gbc.gridy = contactList.size();
		gbc.anchor = GridBagConstraints.NORTHWEST;
		infoPanel.add(name, gbc);
		infoPanel.revalidate();
	}
	
	
	// removes contact from contact list, supports partial matching
	private void removeContact() {
		String uri = (String)JOptionPane.showInputDialog(frame, "Who would you like to remove?\n","Remove contact", JOptionPane.PLAIN_MESSAGE,null, null,"");
		System.out.println("Contact dialog retured "+uri);
		
		if (uri != null && uri.length() > 0) {
			for (Label l : contactList) {
				if (l.getName().contains(uri)) {
					infoPanel.remove(l);
					infoPanel.revalidate();
					contactList.remove(l);
					break;
				}
			}
		}
	}
	
	private void saveContacts() {
		try {
			FileOutputStream fout = new FileOutputStream(new File(CONTACTS_FILE));
			for (Label l : contactList) {
				fout.write((l.getName()+"\n").getBytes());
			}
			fout.close();
		} 
		catch (IOException e) {
			System.out.println("Error saving contacts");
		}
	}
	
	private void loadContacts() {
		try {
			BufferedReader fin = new BufferedReader(new FileReader(new File(CONTACTS_FILE)));
			String line;
			while((line = fin.readLine()) != null) {
				addContact(line);
			}
			fin.close();
		}
		catch (IOException e) {
			System.out.println("Error loading contacts");
		}
	}
	
	public void disconnectEvent() {
		connectionLabel.setText("No connection");
		connectionLabel.paintImmediately(connectionLabel.getVisibleRect());
	}
	
	public AudioSIPGUI(SIPClient c) {
    	client = c;
        
        System.out.println("Init GUI");

        SwingUtilities.invokeLater(new Runnable() { 
            public void run() { 
                //Create a new frame 
                frame = new JFrame(client.myURI); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.setMinimumSize(new Dimension(400,200)); 
                frame.setLayout(new BorderLayout());
                frame.setPreferredSize(new Dimension(400,300));    
                
                frame.addWindowListener(new WindowAdapter() {
            	    @Override
            	    public void windowClosing(WindowEvent we) {
            			client.disconnectFromServer();
            			saveContacts();
            	    }
            	});
                
                frame.pack(); 
                frame.setVisible(true);
                
                listLabel = new JLabel("Contact list");
                listLabel.setHorizontalAlignment(JLabel.CENTER);
                listLabel.setForeground(Color.white);
                
                infoPanel = new JPanel();
                infoPanel.setLayout(new GridBagLayout());
                infoPanel.setBackground(Color.black);
                
                gbc = new GridBagConstraints();
                
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.weighty = 1.0;
                gbc.anchor = GridBagConstraints.NORTH;
                infoPanel.add(listLabel, gbc);
                
                connectionLabel = new JLabel("No connection");
                connectionLabel.setHorizontalAlignment(JLabel.CENTER);
                
                callButton = new JButton("Call");
                callButton.addActionListener(callClick);
                
                stopButton = new JButton("Bye");
                stopButton.addActionListener(stopClick);
                
                //add components to the frame      
                frame.getContentPane().add(callButton, BorderLayout.NORTH);
                frame.getContentPane().add(stopButton, BorderLayout.SOUTH);
                frame.getContentPane().add(connectionLabel,BorderLayout.CENTER);
                frame.getContentPane().add(infoPanel,BorderLayout.EAST);
                
                frame.setSize(new Dimension(500, 300));
                infoPanel.setPreferredSize(new Dimension(250, frame.getHeight()));
                
                loadContacts();
                
                JMenuBar bar = new JMenuBar();
                JMenu menu = new JMenu("Options");
                frame.setJMenuBar(bar);
                bar.add(menu);
                
                JMenuItem connect = new JMenuItem("Connect server");
                connect.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
                JMenuItem call = new JMenuItem("Call");
                call.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
                JMenuItem addBuddy = new JMenuItem("Add contact");
                addBuddy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
                JMenuItem rmBuddy = new JMenuItem("Remove contact");
                rmBuddy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
                
                connect.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						joinServer();
					}
				});
                
                call.addActionListener(callClick);
                
                addBuddy.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						addContactPopup();
					}
				});
                
                rmBuddy.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						removeContact();
					}
				});
                
                menu.add(connect);
                menu.add(call);
                menu.add(addBuddy);
                menu.add(rmBuddy);
                
                joinServer();
            }
        }); 
        
	}
}