
import java.awt.BorderLayout; 
import java.awt.Dimension;
import java.net.URI;

import javax.swing.JFrame; 
import javax.swing.SwingUtilities; 
import org.gstreamer.Caps; 
import org.gstreamer.Element; 
import org.gstreamer.ElementFactory; 
import org.gstreamer.Gst; 
import org.gstreamer.Pipeline; 
import org.gstreamer.State;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.swing.VideoComponent; 
public class Test { 
    private static Pipeline outPipe;
    private static Pipeline inPipe;
    private static PlayBin2 streamPipe;
    
    private static String address = "232.5.5.5";
    private static String port = "1234";
    
    public static void main(String[] args) { 
        args = Gst.init("SwingVideoTest", args); 
        outPipe = new Pipeline("outPipe"); 
        inPipe = new Pipeline("inPipe"); 
        streamPipe = new PlayBin2("streamPipe");
        
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
                
                JFrame frame = new JFrame("Swing Video Test"); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.add(videoComponent, BorderLayout.CENTER);
                frame.add(videoComponent2, BorderLayout.SOUTH);
                videoComponent.setPreferredSize(new Dimension(640, 480));
                videoComponent2.setPreferredSize(new Dimension(640, 480));
                frame.pack(); 
                frame.setVisible(true); 
                
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
}