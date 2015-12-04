import java.awt.BorderLayout; 
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.net.URI;
import java.util.ArrayList;

import javax.swing.BoxLayout;
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
import org.gstreamer.Gst; 
import org.gstreamer.Pipeline; 
import org.gstreamer.State;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.swing.VideoComponent; 

public class VideoConferenceGUI extends JFrame{ 
    private Pipeline outPipe;
    private Pipeline inPipe;
    private PlayBin2 streamPipe;
    
    private String address = "232.5.5.5";
    private String port = "1234";
    private JFrame frame;
    private JPanel panel;
    private JButton joinButton;
    private JPanel videoPanel;
    
    //Save the dimension of the panel with the button "join"
    private final Dimension panelDimension = new Dimension(100,400);
    
    private VideoConferenceClient client;
    private String[] connections = new String[4];
    
    //If the window is resize, we have to resize each component
	private ComponentAdapter resizeListener = new ComponentAdapter() {  
	    public void componentResized(ComponentEvent evt) {
	    	//save the window's dimensions
		    Dimension windowSize = frame.getSize();
		    //resize each component according to the window's dimensions
		    panel.setPreferredSize(new Dimension(100,windowSize.height-10));
            videoPanel.setPreferredSize(new Dimension(windowSize.width-panelDimension.width-20,windowSize.height-10));
            joinButton.setPreferredSize(new Dimension(panelDimension.width,100));
        }
	};
	
	//Join a conference
	private ActionListener joinClick = new ActionListener() {
		
		@Override
		public void actionPerformed(ActionEvent e) {
			//create a dialog box
			String address = (String)JOptionPane.showInputDialog(frame, "Enter server address:\n","Server address", JOptionPane.PLAIN_MESSAGE,null, null,"");
			client.join(address);
		}
	};
    
    public VideoConferenceGUI(VideoConferenceClient client,String[] args) {
        args = Gst.init("SwingVideoTest", args); 
        outPipe = new Pipeline("outPipe"); 
        inPipe = new Pipeline("inPipe"); 
        streamPipe = new PlayBin2("streamPipe");
        this.client=client;
        
        // branch element
        final Element tee = ElementFactory.make("tee", "tee0");
        tee.set("silent", "false");

        // video from file element
//        final Element filesrc = ElementFactory.make("filesrc", "filesrc");
//        filesrc.set("location", "/home/jurij/Downloads/test.mp4");
        
        // stream video over UDP
        // gst-launch v4l2src device=/dev/video0 ! 'video/x-raw-yuv,width=640,height=480' !  x264enc pass=qual quantizer=20 tune=zerolatency ! rtph264pay ! udpsink host=127.0.0.1 port=1234
        final Element v4l2src = ElementFactory.make("v4l2src","videosrc");
        final Element filter = ElementFactory.make("capsfilter","filter");
        filter.setCaps(Caps.fromString("video/x-raw-yuv, width=640, height=480")); 
        final Element x264enc = ElementFactory.make("x264enc","encoder");
        //x264enc.set("pass", "qual");
        x264enc.set("quantizer", "20");
        x264enc.set("tune", "4"); 
        /*
         tune:
         static const GFlagsValue tune_types[] = {
		  {0x0, "No tuning", "none"},
		  {0x1, "Still image", "stillimage"},
		  {0x2, "Fast decode", "fastdecode"},
		  {0x4, "Zero latency", "zerolatency"},
		  {0, NULL, NULL},
		};
         */
        final Element rtph264pay = ElementFactory.make("rtph264pay","payloader");
        final Element udpsink = ElementFactory.make("udpsink","sink");
        udpsink.set("host", address);
	    udpsink.set("port", port);

	    // receive video stream over UDP
	    // gst-launch udpsrc port=1234 ! "application/x-rtp, payload=127" ! rtph264depay ! ffdec_h264 ! xvimagesink sync=false
	    final Element udpsrc = ElementFactory.make("udpsrc", "udpsrc");
	    udpsrc.set("uri", "udp://"+address+":"+port);
	    udpsrc.set("caps", Caps.fromString("application/x-rtp, payload=127"));
	    final Element rtph264depay = ElementFactory.make("rtph264depay", "payldr");
	    final Element ffdec_h264 = ElementFactory.make("ffdec_h264", "decoder");

        SwingUtilities.invokeLater(new Runnable() { 
            public void run() { 
            	VideoComponent videoComponent = new VideoComponent(); 
                Element videosink = videoComponent.getElement(); 
                videosink.setName("center");

                VideoComponent videoComponent2 = new VideoComponent();
                Element videosink2 = videoComponent2.getElement();
                videosink2.setName("south");
                
                /**/
                outPipe.addMany(v4l2src, filter, x264enc, rtph264pay, udpsink);
                Element.linkMany(v4l2src, filter, x264enc, rtph264pay, udpsink);
                /**/
                
                /**
                outPipe.addMany(v4l2src, filter, x264enc, rtph264pay, udpsink, tee, videosink);
                v4l2src.link(filter);
                filter.link(tee);
                tee.link(videosink);
                tee.link(x264enc);
                x264enc.link(rtph264pay);
                rtph264pay.link(udpsink);
                /**/
                
                inPipe.addMany(udpsrc, rtph264depay, ffdec_h264, videosink);
                Element.linkMany(udpsrc, rtph264depay, ffdec_h264, videosink);

                //streamPipe.setInputFile(new File("/home/jurij/Downloads/test.mp4"));
                try {
                	streamPipe.setURI(new URI("http://1tv.ambra.ro"));
                }
                catch (Exception e) {
                	e.printStackTrace();
                }
                streamPipe.setVideoSink(videosink2);
                
                //Create a new frame 
                frame = new JFrame("Swing Video Test"); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.setMinimumSize(new Dimension(500,300)); 
                frame.setLayout(new FlowLayout());
                frame.setPreferredSize(new Dimension(500,300));                
                frame.addComponentListener(resizeListener);
                frame.pack(); 
                frame.setVisible(true);
                
                //Add each component
                videoComponent.setPreferredSize(new Dimension(640, 480));
                videoComponent2.setPreferredSize(new Dimension(640, 480));
                
                panel = new JPanel();
                joinButton= new JButton("Join");
                joinButton.addActionListener(joinClick);
                videoPanel = new JPanel(new GridBagLayout());
                
                videoPanel.setBackground(Color.black);
                resizeListener.componentResized(null);
                
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                
                //add button to the panel
                panel.add(joinButton);
                
                //add panel and videopanel to the frame
                frame.add(videoPanel);
                frame.add(panel);
 
                //Playing
                outPipe.setState(State.PLAYING);
                inPipe.setState(State.PLAYING);
                streamPipe.setState(State.PLAYING);
                
            } 
        }); 
        
        Gst.main();
		outPipe.setState(State.NULL);
        inPipe.setState(State.NULL);
		streamPipe.setState(State.NULL);
		
    }
    
    public void addNewStream(String address,byte[] data) {
    	if (address.length() == 0) return;
    	JLabel addressLabel =new JLabel(address);
    	panel.add(addressLabel);
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
    	GridBagConstraints grid = new GridBagConstraints();
    	if(i==0){
    		grid.gridx = 0;
    		grid.gridy = 0;
    	}
    	else if(i==1){
    		grid.gridx=1;
    		grid.gridy=0;
    	}
    	else if(i==2){
    		grid.gridx=0;
    		grid.gridy=1;
    	}
    	else if(i==3){
    		grid.gridx=1;
    		grid.gridy=1;
    	}
    	else{
    		System.out.println("No more room");
    		return;
    	}
    	videoPanel.add(videoComponent);
    	
  	
    }
    public JComponent findByName(String name, JComponent c){
    	if(c.getName().equals(name)){
    		return c;
    	}
    	for(int i =0;i<c.getComponentCount();i++){
    		return findByName(name,(JComponent)c.getComponent(i));
    	}
    	return null;
    }
    
    public void stopConnection(String address){
    	panel.remove(findByName(address,panel));
    	videoPanel.remove(findByName(address,videoPanel));
    	for(int i=0;i<4;i++){
    		if(connections[i].equals(address)){
    			connections[i] = null;
    			break;
    		}
    	}    	
    }
}

