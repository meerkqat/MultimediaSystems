import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Gst;
import org.gstreamer.Pipeline;

public class SIPClient {
	// TODO handle hangup

	// no spaces in URI!!
	private final String myURI = "sip:Bowser@mario.kart";
	private final String myIP = "127.0.0.1";
	private final int myPort = 5060;
	private final int TCPPort = 2346;
	private String remoteIP;
	private int remotePort;

	public final int PREINIT = -1;
	public final int FREE = 0;
	public final int BUSY = 1;

	private int myState = PREINIT;

	private final int millisToWaitForError = 2000;

	private Socket server;
	private PrintWriter out;
	private BufferedReader in;

	private ServerSocket localServerSocket;
	private Socket remote;
	private PrintWriter remoteOut;
	private BufferedReader remoteIn;

	private ServerListener serverListener;
	private RemoteListener remoteListener;

	private AudioSIPGUI gui;

	private String SERVER_THREAD_COMM = "";

	public SIPClient() {
		gui = new AudioSIPGUI(this);

		System.out.println("Running client " + myURI);
	}

	// call from GUI when you enter the server IP
	public void connectToServer(String host, int port) {
		System.out.println("Connecting to SIP server");
		try {
			// open socket, reader, writer
			server = new Socket(host, port);
			out = new PrintWriter(server.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(
					server.getInputStream()));

			// register with server
			out.write("REGISTER " + myURI + "\n");
			out.flush();

			// registration successful?
			String response = in.readLine();
			if (response.contains(CodeUtil.OK)) {
				System.out.println("Connected & registered");
				serverListener = new ServerListener();
				serverListener.start();

				myState = FREE;
			} else {
				System.out.println("Could not register " + myURI
						+ "\nDisconnecting...");
				// can't just call disconnect - if there was a conflict with the
				// name,
				// the server would remove the existing client from its table
				out.close();
				in.close();
				server.close();
				server = null;
			}
		} catch (IOException e) {
			System.out.println("Error connecting to server!");
			return;
		}
		System.out.println("Connected!");
	}

	// disconnects from current server
	public void disconnectFromServer() {
		if (server == null)
			return;
		out.write("DISCONNECT " + myURI + "\n");
		out.flush();
		serverListener.interrupt();
		try {
			out.close();
			in.close();
			server.close();
		} catch (IOException e) {
		}

		server = null;

		myState = PREINIT;
	}

	// returns the state of this client (free, calling, receiving call)
	public int getState() {
		return myState;
	}

	// establishes a VoIP connection with calleeURI
	// callee should provide its IP & port as optional params appended to the
	// 200-OK message
	// caller should provide its IP & port as optional params appended to the
	// ACK message
	public void call(String calleeURI) {
		myState = BUSY;

		System.out.println("Calling " + calleeURI);

		String response;
		// invite ->
		out.write("INVITE " + calleeURI + "\n");
		out.flush();

		// OK <-
		while (SERVER_THREAD_COMM.length() < 1) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}
		}
		response = SERVER_THREAD_COMM;
		SERVER_THREAD_COMM = "";
		if (response.contains(CodeUtil.OK)) {
			String[] banana = response.split(" ");
			remoteIP = banana[3];
			remotePort = Integer.valueOf(banana[4]);

			remoteListener = new RemoteListener(true);
			remoteListener.start();

			// ACK ->
			out.write("ACK " + calleeURI + " " + myIP + " " + myPort + "\n");
			out.flush();

			// error CODE <- ?
			long now = System.currentTimeMillis();
			while (SERVER_THREAD_COMM.length() < 1
					&& System.currentTimeMillis() - now < millisToWaitForError) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
			response = SERVER_THREAD_COMM;
			SERVER_THREAD_COMM = "";

			if (response.length() > 0) {
				System.out.println("Error occured sending ACK - "
						+ response.split(" ")[2]);
				return;
			}
			System.out.println("Direct connection now");
/**/

			// TODO establish direct connection to remoteIP:remotePort
			final Element alsasrc = ElementFactory.make("alsasrc", "source");
			final Element rate = ElementFactory.make("audiorate", "rate");
			final Element filter = ElementFactory.make("capsfilter", "filter");
			filter.setCaps(Caps
					.fromString("audio/x-raw-int,rate=44100,channels=2"));
			final Element udpsink = ElementFactory.make("udpsink", "sink");

			udpsink.set("host", remoteIP);
			udpsink.set("port", remotePort);
			udpsink.set("auto-multicast", "true");

			Pipeline outPipe = new Pipeline("outPipe");
			outPipe.addMany(alsasrc, rate, filter, udpsink);
			Element.linkMany(alsasrc, rate, filter, udpsink);

			final Element udpsrc = ElementFactory.make("udpsrc", "udpsrc");
			final Element audiosink = ElementFactory.make("alsasink", "sink");
			udpsrc.set("uri", "udp://" + remoteIP + ":" + remotePort);

			Pipeline inPipe = new Pipeline("inPipe");
			inPipe.addMany(udpsrc, audiosink);
			Element.linkMany(udpsrc, audiosink);
/**/
		} else if (response.contains(CodeUtil.RequestTerminated)) {
			System.out.println("Callee declined the call");
		} else if (response.contains(CodeUtil.BusyHere)) {
			System.out.println("Callee is busy");
		} else {
			System.out.println("Unable to call " + calleeURI + " - "
			
					+ response.split(" ")[2]);
		}
	}

	// establishes a VoIP connection with caller
	// callee should provide its IP & port as optional params appended to the
	// 200-OK message
	// caller should provide its IP & port as optional params appended to the
	// ACK message
	public void pickUp(String callerURI) {
		myState = BUSY;

		System.out.println("Receiving call from " + callerURI);

		String response;

		out.write("CODE " + callerURI + " " + CodeUtil.OK + " " + myIP + " "
				+ myPort + "\n");
		out.flush();

		// response = in.readLine();
		while (SERVER_THREAD_COMM.length() < 1) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}
		}
		response = SERVER_THREAD_COMM;
		SERVER_THREAD_COMM = "";
		if (response.startsWith("ACK")) {
			String[] banana = response.split(" ");
			remoteIP = banana[2];
			remotePort = Integer.valueOf(banana[3]);

			remoteListener = new RemoteListener(false);
			remoteListener.start();

			System.out.println("Direct connection now");
/**/
			// TODO establish direct connection to remoteIP:remotePort
			final Element alsasrc = ElementFactory.make("alsasrc", "source");
			final Element rate = ElementFactory.make("audiorate", "rate");
			final Element filter = ElementFactory.make("capsfilter", "filter");
			filter.setCaps(Caps
					.fromString("audio/x-raw-int,rate=44100,channels=2"));
			final Element udpsink = ElementFactory.make("udpsink", "sink");

			udpsink.set("host", remoteIP);
			udpsink.set("port", remotePort);
			udpsink.set("auto-multicast", "true");

			Pipeline outPipe = new Pipeline("outPipe");
			outPipe.addMany(alsasrc, rate, filter, udpsink);
			Element.linkMany(alsasrc, rate, filter, udpsink);

			final Element udpsrc = ElementFactory.make("udpsrc", "udpsrc");
			final Element audiosink = ElementFactory.make("alsasink", "sink");
			udpsrc.set("uri", "udp://" + remoteIP + ":" + remotePort);

			Pipeline inPipe = new Pipeline("inPipe");
			inPipe.addMany(udpsrc, audiosink);
			Element.linkMany(udpsrc, audiosink);
/**/
		} else {
			System.out.println("Error occured sending 200-OK - "
					+ response.split(" ")[2]);
		}
	}

	// decline current call
	public void declineCall(String callerURI) {
		out.write("CODE " + callerURI + " " + CodeUtil.RequestTerminated + "\n");
		out.flush();
	}

	// disconnect from current call
	public void closeCall() {
		if (remoteListener != null)
			remoteListener.endCall();
	}

	// listens to messages coming from the sip server (invites)
	private class ServerListener extends Thread {

		public ServerListener() {
		}

		@Override
		public void run() {
			while (true) {
				String line = "";
				try {
					line = in.readLine();
				} catch (IOException e) {
					System.out.println("Error reading from server!");
				}
				System.out.println("Server sent: " + line);

				if (line.startsWith("INVITE")) {
					String[] banana = line.split(" ");
					if (myState == BUSY) {
						out.write("CODE " + banana[1] + " " + CodeUtil.BusyHere
								+ "\n");
						out.flush();
						continue;
					}

					final String callee = banana[1];
					Thread incoming = new Thread() {
						@Override
						public void run() {
							gui.receivingCall(callee);
						}
					};
					incoming.start();
					
				} else {
					SERVER_THREAD_COMM = line;
				}
			}
		}
	}

	// thread that starts a server on the caller side and a socket on the callee
	// side
	// so they can send/receive the BYE signal
	private class RemoteListener extends Thread {
		protected boolean inCall = true;
		private boolean isServer;

		public RemoteListener(boolean isServer) {
			this.isServer = isServer;
			if (isServer) {
				try {
					localServerSocket = new ServerSocket(TCPPort);
				} catch (IOException E) {
					System.out.println("Error establishing server socket!");
				}
			} else {
				try {
					remote = new Socket(remoteIP, TCPPort);
					remoteOut = new PrintWriter(remote.getOutputStream(), true);
					remoteIn = new BufferedReader(new InputStreamReader(
							remote.getInputStream()));
				} catch (IOException e) {
					System.out.println("Could not open connection to remote!");
				}
			}
		}

		public void endCall() {
			inCall = false;
		}

		protected void doCall() {
			try {
				String msg;
				while (inCall) {
					msg = "";
					try {
						msg = remoteIn.readLine();
					} catch (SocketTimeoutException e) {
					}
					if (msg.startsWith("BYE")) {
						remoteOut.write("CODE " + CodeUtil.OK + "\n");
						remoteOut.flush();

							remoteIn.close();
							remoteOut.close();
							remote.close();
							if (isServer) localServerSocket.close();
						inCall = false;
					} else if (msg.length() > 0){
						System.out.println("Unexpected message from remote: "
								+ msg);
					}
				}

				// we broke out of the while, but didn't get a BYE message,
				// therefore we must be ending the call
				if (!remote.isClosed()) {
					remoteOut.write("BYE\n");
					remoteOut.flush();
					remote.setSoTimeout(0);
					msg = remoteIn.readLine();

					if (msg.contains(CodeUtil.OK)) {
						remoteIn.close();
						remoteOut.close();
						remote.close();
						localServerSocket.close();
					} else {
						System.out.println("Unexpected message from remote: "
								+ msg);
					}
				}

				myState = FREE;
			} catch (IOException e) {
				System.out.println("Error occured talking to remote!");
			}
		}

		@Override
		public void run() {
			if (isServer) {
				try {
					// open connection
					remote = localServerSocket.accept();
					remote.setSoTimeout(500);
					remoteOut = new PrintWriter(remote.getOutputStream(), true);
					remoteIn = new BufferedReader(new InputStreamReader(
							remote.getInputStream()));
				} catch (IOException e) {
					System.out.println("Error occured connecting to remote!");
				}
			}

			doCall();
		}
	}

	public static void main(String[] args) {
		args = Gst.init("SwingVideoTest", args);
		new SIPClient();
		Gst.main();
	}
}
