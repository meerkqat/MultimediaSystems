import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class VideoConferenceClient {
	private String multicastAddress;
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private VideoConferenceGUI gui;
	private Thread listenLoop;
	
	
	
	public VideoConferenceClient (String multicastAddr, String[] args) {
		// start gui
		gui = new VideoConferenceGUI(this,args);
		
		//start multicast
		multicastAddress = multicastAddr;
		// TODO
		// make multicast inetaddress
		// send udp packets (should probably be threaded)
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
				gui.addNewStream(line,null);  
			}
		}
	}
	
	public static void main(String[] args) {
		new VideoConferenceClient("127.0.0.1:2345", args);
	}
}
