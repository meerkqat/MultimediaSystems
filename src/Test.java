
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
    private static Pipeline pipe;
    private static PlayBin2 pipe2;
    public static void main(String[] args) { 
        args = Gst.init("SwingVideoTest", args); 
        pipe = new Pipeline("pipeline"); 
        pipe2 = new PlayBin2("p2");
        
        final Element videosrc = ElementFactory.make("v4l2src", "vidsrc"); 
        final Element videofilter = ElementFactory.make("capsfilter", "flt"); 
        videofilter.setCaps(Caps.fromString("video/x-raw-yuv, width=640, height=480")); 
        final Element tee = ElementFactory.make("tee", "tee0");
        tee.set("silent", "false");
        
        /*
        // video from file element
        final Element filesrc = ElementFactory.make("filesrc", "filesrc");
        filesrc.set("location", "/home/jurij/Downloads/test.mp4");
        */
        
        /*
        // udpsink doesn't work like this probably 
        final Element encoder = ElementFactory.make("jpegenc", "encoder");
        final Element payloader = ElementFactory.make("rtpjpegpay", "payloader");
        final Element sink = ElementFactory.make("udpsink", "sink");
	    sink.set("host", "127.0.0.1");
	    sink.set("port", "45001");
	    sink.set("sync", "true");
	   `*/

        SwingUtilities.invokeLater(new Runnable() { 
            public void run() { 
                VideoComponent videoComponent = new VideoComponent(); 
                Element videosink = videoComponent.getElement(); 
                videosink.setName("a");
                VideoComponent videoComponent2 = new VideoComponent();
                Element videosink2 = videoComponent2.getElement();
                videosink2.setName("b");
 
                /*
               `//split stream to multiple sinks
                pipe.addMany(videosrc, videofilter, videosink, tee, videosink2); 
                videosrc.link(tee);
                videofilter.link(tee);
                tee.link(videosink);
                tee.link(videosink2);
                */
                
                // get stream from net 
                pipe.addMany(videosrc, videofilter, videosink);
                videosrc.link(videofilter);
                videofilter.link(videosink);
                //pipe2.setInputFile(new File("/home/jurij/Downloads/test.mp4"));
                try {
                	pipe2.setURI(new URI("http://1tv.ambra.ro"));
                }
                catch (Exception e) {
                	e.printStackTrace();
                }
                pipe2.setVideoSink(videosink2);
                
                JFrame frame = new JFrame("Swing Video Test"); 
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
                frame.add(videoComponent, BorderLayout.CENTER);
                frame.add(videoComponent2, BorderLayout.SOUTH);
                videoComponent.setPreferredSize(new Dimension(640, 480));
                videoComponent2.setPreferredSize(new Dimension(640, 480));
                frame.pack(); 
                frame.setVisible(true); 
                // Start the pipeline processing 
                pipe.setState(State.PLAYING); 
                pipe2.setState(State.PLAYING);
            } 
        }); 
    }
}