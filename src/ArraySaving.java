import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.gstreamer.Buffer;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Gst;
import org.gstreamer.Pad;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.Structure;
import org.gstreamer.elements.AppSink;
import org.gstreamer.elements.AppSrc;
import org.gstreamer.elements.DecodeBin2;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.swing.VideoComponent;

public class ArraySaving {

	private static final Logger logger = Logger.getLogger(ArraySaving.class
			.getName());

	private static Pipeline outPipe;
	private static Pipeline inPipe;
	private static PlayBin2 streamPipe;

	private static String address = "232.5.5.5";
	private static String port = "1234";

	private static ArrayBlockingQueue<byte[]> abq = new ArrayBlockingQueue<byte[]>(
			10);

	public static void main(String[] args) {
		args = Gst.init("SwingVideoTest", args);
		outPipe = new Pipeline("outPipe");
		inPipe = new Pipeline("inPipe");
		streamPipe = new PlayBin2("streamPipe");
		final AppSink appsink = (AppSink) ElementFactory.make("appsink",
				"appsink");
		final AppSrc appsrc = (AppSrc) ElementFactory.make("appsrc", "source");

		// branch element
		final Element tee = ElementFactory.make("tee", "tee0");
		tee.set("silent", "false");

		// Appsink

		final Element v4l2src = ElementFactory.make("v4l2src", "v4l2src");
		final Element filter = ElementFactory.make("capsfilter", "filter");
		filter.setCaps(Caps.fromString(String.format(
				"video/x-raw-yuv, width=%s, height=%s"
						+ ", bpp=24, depth=16,framerate=%s/1", "640", "480",
				"30")));
		final Element encoder = ElementFactory.make("theoraenc", "encoder");
		final Element formatConverter = ElementFactory.make("ffmpegcolorspace",
				"formatConverter");
		final Element muxer = ElementFactory.make("oggmux", "muxer");
		final Element videorate = ElementFactory.make("videorate", "videorate");
		final Element ratefilter = ElementFactory.make("capsfilter",
				"ratefilter");
		ratefilter.setCaps(Caps.fromString(String.format(
				"video/x-raw-yuv,framerate=%s/1", "30")));

		appsink.set("emit-signals", true);
		appsink.setSync(false);
		appsink.connect(new AppSink.NEW_BUFFER() {

			@Override
			public void newBuffer(AppSink arg0) {
				Buffer buffer = arg0.getLastBuffer();
				ByteBuffer byteBuffer = buffer.getByteBuffer();
				byte[] byteArray = new byte[byteBuffer.remaining()];
				byteBuffer.get(byteArray);
				// logger.log(Level.INFO, "{0}", new Object[] { abq });
				if (abq.remainingCapacity() > 0) {
					try {
						abq.put(byteArray);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					abq.poll();
					try {
						abq.put(byteArray);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});

		// appsrc
		appsrc.setCaps(Caps.fromString(String.format(
				"video/x-raw-yuv, width=%s, height=%s"
						+ ", bpp=24, depth=16, framerate=%s/1", "640", "480",
				"30")));
		final DecodeBin2 decodeBin = (DecodeBin2) ElementFactory.make("decodebin2", "decodeBin");
		final VideoComponent videoComponent = new VideoComponent();
		final Element filter2 = ElementFactory.make("capsfilter", "filter");
		filter2.setCaps(Caps.fromString(String.format(
				"video/x-raw-yuv, width=%s, height=%s"
						+ ", bpp=24, depth=16,framerate=%s/1", "640", "480",
				"30")));

		decodeBin.connect(new DecodeBin2.NEW_DECODED_PAD() {

			@Override
			public void newDecodedPad(DecodeBin2 arg0, Pad arg1, boolean arg2) {
				Caps caps = arg1.getCaps();
				Structure structure = caps.getStructure(0);
				if (structure.getName().startsWith("video/")) {
					arg1.link(videoComponent.getElement().getStaticPad("sink"));
				}
			}
		});

		appsrc.set("emit-signals", true);
		appsrc.connect(new AppSrc.NEED_DATA() {

			@Override
			public void needData(AppSrc arg0, int arg1) {
				byte[] byteArray = abq.poll();

				while (byteArray == null) {

					byteArray = abq.poll();
				}
				Buffer buffer = new Buffer(byteArray.length);
				buffer.getByteBuffer().put(byteArray);
				arg0.pushBuffer(buffer);
				logger.log(Level.INFO, "{0}", new Object[] { "need data" });
			}
		});

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// VideoComponent videoComponent = new VideoComponent();
				Element videosink = videoComponent.getElement();
				videosink.setName("center");

				VideoComponent videoComponent2 = new VideoComponent();
				Element videosink2 = videoComponent2.getElement();
				videosink2.setName("south");

				/**/

				outPipe.addMany(v4l2src, formatConverter, filter, encoder,
						muxer, appsink);
				Element.linkMany(v4l2src, formatConverter, filter, encoder,
						muxer, appsink);

				inPipe.addMany(appsrc, filter2, decodeBin, videosink);
				Element.linkMany(appsrc, filter2, decodeBin, videosink);

				try {
					streamPipe.setURI(new URI("http://1tv.ambra.ro"));
				} catch (Exception e) {
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
				inPipe.setState(State.NULL);
				streamPipe.setState(State.PLAYING);

				long delay = 1000;
				long period = 1000;
				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						// log state of inPipes
//						logger.log(Level.INFO, "{0}", new Object[] { inPipe.getState() });
//						logger.log(Level.INFO, "{0}", new Object[] { "Got through" });

					}
				}, delay, period);
				timer.schedule(new TimerTask() {
					
					@Override
					public void run() {
						// restarts the inpipe
						inPipe.setState(State.PLAYING);
						
					}
				}, delay, period*2);
			}
		});

		Gst.main();
		outPipe.setState(State.NULL);
		inPipe.setState(State.NULL);
		streamPipe.setState(State.NULL);

	}

	private static Timer timer = new Timer();
}