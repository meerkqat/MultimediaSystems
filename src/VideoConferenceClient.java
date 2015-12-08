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
		// start multicast
		multicastAddress = multicastAddr;
 
		Thread camStreamer = new Streamer(multicastAddr);
		camStreamer.start();
		
		// start gui
		gui = new VideoConferenceGUI(this,args);		
	}
	
	// on join channel
	public void join(String serverAddress) {
		System.out.println("Joining server on "+serverAddress);
		
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
		out.write(multicastAddress+"\n");
		out.flush();
		System.out.println("Sent multicast address");
		
		// subscribe to other streams
		if (listenLoop != null) listenLoop.interrupt(); 
		listenLoop = new ServerListener();
		listenLoop.start();
	}
	
	// listens for new multicast addresses coming from the server
	private class ServerListener extends Thread {
		@Override
		public void run() {
			System.out.println("Starting to listen to server...");
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
			System.out.println("Init cam streamer");
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
			
			outBuf = new byte[42]; // TODO
		}
		
		@Override
		public void run() {
			if (socket == null || host == null) interrupt();

			System.out.println("Streaming webcam...");
			
			try {
				DatagramPacket outPacket;
				while (true) {
					// TODO fill outBuf from pipeline

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
		new VideoConferenceClient("232.2.2.2:2345", args);
	}
}
