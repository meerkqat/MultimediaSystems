import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
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
	private JPanel buttonPanel;
	private JPanel infoPanel;
	private JButton callButton;
	private JButton stopButton;
	private Pipeline pipeline;

	private final Dimension buttonPanelDimension = new Dimension(180,400);
	private final Dimension infoPanelDimension = new Dimension(180,400);
	
//NAME OF CLIENT 
	private VideoConferenceClient client;
	
	private String[] connections = new String[4];
	
	//If the window is resize, we have to resize each component
	private ComponentAdapter resizeListener = new ComponentAdapter() {  
	    public void componentResized(ComponentEvent evt) {
	    	//save the window's dimensions
		    Dimension windowSize = frame.getSize();
		    //resize each component according to the window's dimensions
		    infoPanel.setPreferredSize(new Dimension(180,windowSize.height-10));
            buttonPanel.setPreferredSize(new Dimension(windowSize.width-180,windowSize.height-10));
            callButton.setPreferredSize(new Dimension(buttonPanelDimension.width-10,30));
            stopButton.setSize(new Dimension(buttonPanelDimension.width-10,30));
        }
	};
	
	//Join a conference
	private ActionListener callClick = new ActionListener() {
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
	
	//Stop a conference
	private ActionListener stopClick = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			System.out.println("Stop");
			//stopConnection();
		}
	};
	
	public AudioSIPGUI(VideoConferenceClient c, String[] args) {
    	client = c;
        
        System.out.println("Init GUI");

        SwingUtilities.invokeLater(new Runnable() { 
            public void run() { 
                //Create a new frame 
                frame = new JFrame(client.multicastAddress); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.setMinimumSize(new Dimension(300,300)); 
                frame.setLayout(new FlowLayout());
                frame.setPreferredSize(new Dimension(600,600));                
                frame.addComponentListener(resizeListener);
                frame.pack(); 
                frame.setVisible(true);
                
                infoPanel = new JPanel();
                infoPanel.setBackground(Color.black);
                
                buttonPanel = new JPanel();
                
                callButton = new JButton("Invite");
                callButton.addActionListener(callClick);
                
                stopButton = new JButton("Bye");
                stopButton.addActionListener(stopClick);
                
                resizeListener.componentResized(null);
                
                buttonPanel.setLayout(new FlowLayout());
                
                //add button to the panel
                buttonPanel.add(callButton);
                buttonPanel.add(stopButton);
                
                //add panels to the frame
                frame.add(infoPanel);
                frame.add(buttonPanel);
                
            } 
        }); 
        
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
    	
    	buttonPanel.remove(findByName(address,buttonPanel));
    	infoPanel.remove(findByName(address,infoPanel));
    	for(int i=0;i<4;i++){
    		if(connections[i].equals(address)){
    			connections[i] = null;
    			break;
    		}
    	}    	
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