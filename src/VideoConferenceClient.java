import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class VideoConferenceClient {
	private String multicastAddress;
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private VideoConferenceGUI gui;
	private Thread listenLoop;
	
	
	
	public VideoConferenceClient (String multicastAddr, String[] args) {
		// start gui
		gui = new VideoConferenceGUI(args);
		
		// start multicast
		multicastAddress = multicastAddr;
 
		Thread camStreamer = new Streamer(multicastAddr);
		camStreamer.start();
	}
	
	// on join channel
	public void join(String serverAddress) {
		try {
			String[] banana = serverAddress.split(":");
		    socket = new Socket(banana[0], Integer.valueOf(banana[1]));
		    out = new PrintWriter(socket.getOutputStream(), true);
		    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}
		catch (IOException e) {
			System.out.println("Error opening socket!");
			e.printStackTrace();
			return;
		}
		
		// publish our stream address
		out.write(multicastAddress);
		
		// subscribe to other streams
		if (listenLoop != null) listenLoop.interrupt(); 
		listenLoop = new ServerListener();
		listenLoop.start();
		
	}
	
	// listens for new multicast addresses coming from the server
	private class ServerListener extends Thread {
		@Override
		public void run() {
			String line = "";
			while("pigs" != "fly") {
				try {
					line = in.readLine();
				}
				catch (IOException e) {
					System.out.println("Error writing to socket!");
					listenLoop = null;
					interrupt();
				}
				
				// TODO maybe also do receiving end of udp stream here instead of in the gui? 
				gui.addNewStream(line);  
			}
		}
	}
	
	private class Streamer extends Thread {
		DatagramSocket socket;
		InetAddress host;
		int port;
		
		byte[] outBuf;
		
		public Streamer(String streamTo) {
			String[] banana = streamTo.split(":");
			try {
				host = InetAddress.getByName(banana[0]);
			}
			catch (UnknownHostException e) {
				System.out.println("Error getting multicast address!");
				e.printStackTrace();
			}
			port = Integer.valueOf(banana[1]);
			
			try {
				socket = new DatagramSocket();
			}
			catch (SocketException e) {
				System.out.println("Error opening multicast socket!");
				e.printStackTrace();
			}
			
			outBuf = new byte[42];
		}
		
		@Override
		public void run() {
			if (socket == null || host == null) interrupt();

			try {
				DatagramPacket outPacket;
				while (true) {
					outBuf = "TODO fill buffer and stuff".getBytes();

					//Send to multicast IP:port
					outPacket = new DatagramPacket(outBuf, outBuf.length, host, port);
					socket.send(outPacket);
				}
			} catch (IOException e) {
				System.out.println("Error sending to multicast address!");
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		new VideoConferenceClient("127.0.0.1:2345", args);
	}
}
