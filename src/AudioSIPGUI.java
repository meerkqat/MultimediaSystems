import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Pipeline;
import org.gstreamer.swing.VideoComponent;


public class AudioSIPGUI {
	private JFrame frame;
	private JPanel infoPanel;
	private JButton callButton;
	private JButton stopButton;
	private Pipeline pipeline;
	private JLabel connectionLabel;
	private JLabel listLabel;

	private SIPClient client;
	
	private String[] connections = new String[4];
	
	
	//Invite someone
	private ActionListener callClick = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			//create a dialog box
			String address = (String)JOptionPane.showInputDialog(frame, "Enter address:\n","Callee address", JOptionPane.PLAIN_MESSAGE,null, null,"");
			System.out.println("Join dialog retured "+address);
			if (address != null && address.length() > 0) {
				client.call(address);
				connectionLabel=new JLabel("calling...");
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
				connectionLabel=new JLabel("No connection");
			}
		}
	};
	
	//Receiving call- accept or deny
	public void receivingCall(String calleeURI){
		System.out.println("Receivig call");
					
		int option = JOptionPane.showConfirmDialog(null, "Do you accept the call ?", "Receiving call", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
					
		if(option == JOptionPane.OK_OPTION){
		  System.out.println("Accep");
		  client.pickUp(calleeURI);
		  connectionLabel=new JLabel("Connection OK");
		}
		else{
			System.out.println("Deny");
			client.declineCall(calleeURI);
			connectionLabel=new JLabel("No connection");
		}
	}
	
	//Join Server pressing ctrl S
	private KeyEventDispatcher keyboardListener = new KeyEventDispatcher(){
		
		public boolean dispatchKeyEvent(KeyEvent e){
			if(e.getID() == KeyEvent.KEY_PRESSED){
				if(e.getKeyCode() == KeyEvent.VK_S && ((e.getModifiers() &KeyEvent.CTRL_MASK )!=0)){
	
			        //Join Server
			        String address = (String)JOptionPane.showInputDialog(frame, "Enter server address:\n","Server address", JOptionPane.PLAIN_MESSAGE,null, null,"");
					System.out.println("Join dialog retured "+address);
					if (address != null && address.length() > 0) {
						String[] banana=address.split(":");
						client.connectToServer(banana[0],Integer.valueOf(banana[1]));
						
					}
				}
			}
			return false;
		}
		
	};
	

	
	public AudioSIPGUI(SIPClient c) {
    	client = c;
        
        System.out.println("Init GUI");

        SwingUtilities.invokeLater(new Runnable() { 
            public void run() { 
                //Create a new frame 
                frame = new JFrame("test"); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.setMinimumSize(new Dimension(400,200)); 
                frame.setLayout(new BorderLayout());
                frame.setPreferredSize(new Dimension(400,300));                
                
                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                kfm.addKeyEventDispatcher(keyboardListener);
                
                frame.pack(); 
                frame.setVisible(true);
                
                listLabel = new JLabel("List of participants");
                listLabel.setHorizontalAlignment(JLabel.CENTER);
                listLabel.setForeground(Color.white);
                
                infoPanel = new JPanel();
                infoPanel.setBackground(Color.black);
                
                infoPanel.add(listLabel);
                
                connectionLabel = new JLabel("No connection");
                connectionLabel.setHorizontalAlignment(JLabel.CENTER);
                
                callButton = new JButton("Invite");
                callButton.addActionListener(callClick);
                
                stopButton = new JButton("Bye");
                stopButton.addActionListener(stopClick);
                
                //add components to the frame      
                frame.getContentPane().add(callButton, BorderLayout.NORTH);
                frame.getContentPane().add(stopButton, BorderLayout.SOUTH);
                frame.getContentPane().add(connectionLabel,BorderLayout.CENTER);
                frame.getContentPane().add(infoPanel,BorderLayout.EAST);
                
                //Join Server
                String address = (String)JOptionPane.showInputDialog(frame, "Enter server address:\n","Server address", JOptionPane.PLAIN_MESSAGE,null, null,"");
    			System.out.println("Join dialog retured "+address);
    			if (address != null && address.length() > 0) {
    				String[] banana=address.split(":");
    				client.connectToServer(banana[0],Integer.valueOf(banana[1]));
    			}
            } 
        }); 
        
	}

	
	

    public void addNewStream(String address) {
    	if (address.length() == 0) return;
    	
    	System.out.println("Obtained new stream: "+address);
    	
    	JLabel addressLabel = new JLabel(address);
    	infoPanel.add(addressLabel);
    	VideoComponent videoComponent = new VideoComponent();
    	Element videosink = videoComponent.getElement();
    	videosink.setName(address);
    	
    	addressLabel.setName(address);
    	videoComponent.setName(address);
    	int i;
    	for(i=0;i<4;i++){
    		if(connections[i]== null){
    			connections[i] = address;
    			break;
    		}
    	}
    	//connexionLabel
    	
    	Thread stream = new StreamListener(address, videosink);
    	stream.start();
    	
    	// apparently resizing make box layout behave properly
    	frame.setSize(new Dimension(frame.getSize().width+1, frame.getSize().height));
    	frame.setSize(new Dimension(frame.getSize().width-1, frame.getSize().height));
    }
    
	private class StreamListener extends Thread {
	
		String hostString;
		int port;
		Element videosink;
		
		public StreamListener(String address, Element vsink) {
			System.out.println("Stream listener init");
		
		String[] banana = address.split(":");
		
		hostString = banana[0];
		port = Integer.valueOf(banana[1]);
		
		videosink = vsink;
	}
	
	@Override
	public void run() {
		
		pipeline = new Pipeline("Playbin");
	
		final Element udpsrc = ElementFactory.make("udpsrc", "udpsrc");
		udpsrc.set("multicast-group", hostString);
	    udpsrc.set("port", port);
	    udpsrc.set("auto-multicast", 1);
	    udpsrc.set("caps", Caps.fromString("application/x-rtp, payload=127"));
	    final Element rtph264depay = ElementFactory.make("rtph264depay", "payldr");
	    final Element ffdec_h264 = ElementFactory.make("ffdec_h264", "decoder");
	    
	    pipeline.addMany(udpsrc, rtph264depay, ffdec_h264, videosink);
	    Element.linkMany(udpsrc, rtph264depay, ffdec_h264, videosink);
	    
	    pipeline.setState(org.gstreamer.State.PLAYING);
	    
	    System.out.println("Started stream listener...");
	
		}
	}
}