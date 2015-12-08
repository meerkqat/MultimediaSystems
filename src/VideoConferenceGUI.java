import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.gstreamer.Element;
import org.gstreamer.Gst;
import org.gstreamer.swing.VideoComponent; 

public class VideoConferenceGUI extends JFrame{ 
    private JFrame frame;
    private JPanel sidebarPanel;
    private JButton joinButton;
    private JPanel videoPanel;
    
    //Save the dimension of the panel with the button "join"
    private final Dimension panelDimension = new Dimension(180,400);
    
    private VideoConferenceClient client;
    private String[] connections = new String[4];
    
    //If the window is resize, we have to resize each component
	private ComponentAdapter resizeListener = new ComponentAdapter() {  
	    public void componentResized(ComponentEvent evt) {
	    	//save the window's dimensions
		    Dimension windowSize = frame.getSize();
		    //resize each component according to the window's dimensions
		    sidebarPanel.setPreferredSize(new Dimension(180,windowSize.height-10));
            videoPanel.setPreferredSize(new Dimension(windowSize.width-panelDimension.width-20,windowSize.height-10));
            joinButton.setPreferredSize(new Dimension(panelDimension.width,30));
            joinButton.setSize(new Dimension(panelDimension.width,30));
        }
	};
	
	//Join a conference
	private ActionListener joinClick = new ActionListener() {
		
		@Override
		public void actionPerformed(ActionEvent e) {
			//create a dialog box
			String address = (String)JOptionPane.showInputDialog(frame, "Enter server address:\n","Server address", JOptionPane.PLAIN_MESSAGE,null, null,"");
			System.out.println("Join dialog retured "+address);
			if (address != null && address.length() > 0) {
				client.join(address);
			}
		}
	};
    
    public VideoConferenceGUI(VideoConferenceClient c, String[] args) {
    	client = c;
        args = Gst.init(client.multicastAddress, args); 
        
        System.out.println("Init GUI");

        SwingUtilities.invokeLater(new Runnable() { 
            public void run() { 
                //Create a new frame 
                frame = new JFrame(client.multicastAddress); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.setMinimumSize(new Dimension(500,300)); 
                frame.setLayout(new FlowLayout());
                frame.setPreferredSize(new Dimension(1000,600));                
                frame.addComponentListener(resizeListener);
                frame.pack(); 
                frame.setVisible(true);
                
                sidebarPanel = new JPanel();
                joinButton= new JButton("Join");
                joinButton.addActionListener(joinClick);
                videoPanel = new JPanel(new GridBagLayout());
                
                videoPanel.setBackground(Color.black);
                resizeListener.componentResized(null);
                
                sidebarPanel.setLayout(new FlowLayout());
                
                //add button to the panel
                sidebarPanel.add(joinButton);
                
                //add panel and videopanel to the frame
                frame.add(videoPanel);
                frame.add(sidebarPanel);
                
            } 
        }); 
        
        //Gst.main();
    }
    
    public void addNewStream(String address) {
    	if (address.length() == 0) return;
    	
    	System.out.println("Obtained new stream: "+address);
    	
    	JLabel addressLabel = new JLabel(address);
    	sidebarPanel.add(addressLabel);
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
    	GridBagConstraints gbc = new GridBagConstraints();
    	if(i==0){
    		gbc.gridx = 0;
    		gbc.gridy = 0;
    	}
    	else if(i==1){
    		gbc.gridx=1;
    		gbc.gridy=0;
    	}
    	else if(i==2){
    		gbc.gridx=0;
    		gbc.gridy=1;
    	}
    	else if(i==3){
    		gbc.gridx=1;
    		gbc.gridy=1;
    	}
    	else{
    		System.out.println("No more room");
    		return;
    	}
    	videoPanel.add(videoComponent, gbc);
    	
    	Thread stream = new StreamListener(address, videosink);
    	stream.start();
    	
    	// apparently resizing make box layout behave properly
    	frame.setSize(new Dimension(frame.getSize().width+1, frame.getSize().height));
    	frame.setSize(new Dimension(frame.getSize().width-1, frame.getSize().height));
    }
    
    public JComponent findByName(String name, JComponent c){
    	if(c.getName().equals(name)){
    		return c;
    	}
    	JComponent tmp = null;
    	for(int i =0;i<c.getComponentCount();i++){
    		tmp = findByName(name,(JComponent)c.getComponent(i));
    	}
    	return tmp;
    }
    
    public void stopConnection(String address){
    	System.out.println("Stopping "+address);
    	
    	sidebarPanel.remove(findByName(address,sidebarPanel));
    	videoPanel.remove(findByName(address,videoPanel));
    	for(int i=0;i<4;i++){
    		if(connections[i].equals(address)){
    			connections[i] = null;
    			break;
    		}
    	}    	
    }
    
    private class StreamListener extends Thread{
    	InetAddress host;
    	int port;
    	MulticastSocket socket;
    	DatagramPacket inPacket;
    	byte[] inBuf;
    	String addr;
    	Element videosink;
    	
    	public StreamListener(String address, Element vsink) {
    		System.out.println("Stream listener init");
    		
    		addr = address;
    		String[] banana = address.split(":");
			try {
				host = InetAddress.getByName(banana[0]);
			}
			catch (UnknownHostException e) {
				System.out.println("Error getting multicast address!");
				e.printStackTrace();
			}
			port = Integer.valueOf(banana[1]);
			
			try {
				socket = new MulticastSocket(port);
				socket.joinGroup(host);
			}
			catch (IOException e) {
				System.out.println("Error opening socket!");
				e.printStackTrace();
			}
			
			videosink = vsink;
			
			inBuf = new byte[42]; // TODO
		}
    	
    	@Override
    	public void run() {
    		System.out.println("Starting stream listener...");
    	    try {
    	      while (true) {
    	        inPacket = new DatagramPacket(inBuf, inBuf.length);
    	        socket.receive(inPacket);
    	        
    	        // TODO push from inBuf into pipeline, ends with videosink
    	      }
    	    } catch (IOException ioe) {
    	    	stopConnection(addr);
    	    	interrupt();
    	    }
    	}
    }
}

