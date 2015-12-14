import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.gstreamer.Buffer;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Gst;
import org.gstreamer.Pipeline;
import org.gstreamer.elements.AppSink;

public class VideoConferenceClient {
	public String multicastAddress = "130.240.157.150:9989";
	public String localIP = "172.16.25.126";
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private VideoConferenceGUI gui;
	private Thread listenLoop;
	//private AppSink appsink = (AppSink) ElementFactory.make("appsink","appsink");
	private Pipeline outPipe;

	public VideoConferenceClient(String[] args) {
		// start multicast
		Thread camStreamer = new Streamer(multicastAddress);
		camStreamer.start();

		// start gui
		gui = new VideoConferenceGUI(this, args);
		Gst.main();
	}

	// on join channel
	public void join(String serverAddress) {
		System.out.println("Joining server on " + serverAddress);

		try {
			String[] banana = serverAddress.split(":");
			socket = new Socket(banana[0], Integer.valueOf(banana[1]));
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
		} catch (IOException e) {
			System.out.println("Error opening socket!");
			e.printStackTrace();
			return;
		}

		// publish our stream address
		out.write(multicastAddress + "\n");
		out.flush();
		System.out.println("Sent multicast address");

		// subscribe to other streams
		if (listenLoop != null)
			listenLoop.interrupt();
		listenLoop = new ServerListener();
		listenLoop.start();
	}

	// listens for new multicast addresses coming from the server
	private class ServerListener extends Thread {
		@Override
		public void run() {
			System.out.println("Starting to listen to server...");
			String line = "";
			while ("pigs" != "fly") {
				try {
					line = in.readLine();
				} catch (IOException e) {
					System.out.println("Error writing to socket!");
					listenLoop = null;
					interrupt();
				}

				gui.addNewStream(line);
			}
		}
	}

	private class Streamer extends Thread {
		//MulticastSocket socket;
		//InetAddress host;
		String hostString;
		int port;

		//byte[] outBuf;

		public Streamer(String streamTo) {
			System.out.println("Init cam streamer");
			String[] banana = streamTo.split(":");
			hostString = banana[0];
			port = Integer.valueOf(banana[1]);
			/*
			try {
				host = InetAddress.getByName(banana[0]);
			} catch (UnknownHostException e) {
				System.out.println("Error getting multicast address!");
				e.printStackTrace();
			}

			try {
				socket = new MulticastSocket(port);
				socket.setInterface(InetAddress.getByName(localIP));
			} catch (IOException e) {
				System.out.println("Error opening multicast socket!");
				e.printStackTrace();
			}
			*/

			//outBuf = new byte[42];
		}

		@Override
		public void run() {
			if (hostString == null || hostString.length() < 1)
				interrupt();
			
			// init appsink
			final Element v4l2src = ElementFactory.make("v4l2src","videosrc");
	        final Element filter = ElementFactory.make("capsfilter","filter");
	        filter.setCaps(Caps.fromString("video/x-raw-yuv, width=640, height=480")); 
	        final Element encoder = ElementFactory.make("x264enc","encoder");
	        encoder.set("quantizer", "20");
	        encoder.set("tune", "4"); 
	        final Element rtph264pay = ElementFactory.make("rtph264pay","payloader");
	        final Element udpsink = ElementFactory.make("udpsink","sink");
			
			udpsink.set("host", hostString);
		    udpsink.set("port", port);
		    udpsink.set("auto-multicast", "true");
		    
		    // gst-launch udpsrc port= ! "application/x-rtp, payload=127" ! rtph264depay ! ffdec_h264 ! xvimagesink sync=false
		    
		    /*
			appsink.set("emit-signals", true);
			appsink.setSync(false);
			appsink.connect(new AppSink.NEW_BUFFER() {

				@Override
				public void newBuffer(AppSink arg0) {
					// fills the buffer from the pipeline
					Buffer buffer = arg0.getLastBuffer();
					ByteBuffer byteBuffer = buffer.getByteBuffer();
					outBuf = new byte[byteBuffer.remaining()];
					byteBuffer.get(outBuf);
				}
			});
			*/
			outPipe = new Pipeline("outPipe");
			outPipe.addMany(v4l2src, filter, encoder, rtph264pay, udpsink);
			Element.linkMany(v4l2src, filter, encoder, rtph264pay, udpsink);
			outPipe.setState(org.gstreamer.State.PLAYING);

			System.out.println("Streaming webcam...");

			/*
			try {
				DatagramPacket outPacket;
				while (true) {
					// Send to multicast IP:port
					outPacket = new DatagramPacket(outBuf, outBuf.length, host,
							port);
					socket.send(outPacket);
				}
			} catch (IOException e) {
				System.out.println("Error sending to multicast address!");
				e.printStackTrace();
			}
			*/
		}
	}

	public static void main(String[] args) {
		args = Gst.init("SwingVideoTest", args);
		new VideoConferenceClient(args);
	}
}
