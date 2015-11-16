import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.gstreamer.ElementFactory;
import org.gstreamer.Format;
import org.gstreamer.Gst;
import org.gstreamer.SeekFlags;
import org.gstreamer.SeekType;
import org.gstreamer.State;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.swing.VideoComponent;

public class AVPlayer {
	private PlayBin2 playbin;
	private JFrame frame;
	private JPanel bttnsPanel;
	private JSlider seekBar;
	private JSlider volumeSlider;
	private JToggleButton playBttn;
	private JToggleButton rewindBttn;
	private JToggleButton fforwardBttn;
	private JToggleButton muteBttn;
	
	private boolean userIsSeeking = false;
	
	private Dictionary<Integer,JLabel> seekLabels = new Hashtable<Integer,JLabel>();
	
	private final JFileChooser fc = new JFileChooser();
	
	private final Icon iPlay = new ImageIcon("imgs/play.png");
	private final Icon iPause = new ImageIcon("imgs/pause.png");
	private final Icon iForward = new ImageIcon("imgs/fforward.png");
	private final Icon iRewind = new ImageIcon("imgs/rewind.png");
	private final Icon iMute = new ImageIcon("imgs/mute.png");
	private final Icon iSpeaker = new ImageIcon("imgs/speaker.png");
	
	private final TimeUnit unitScale = TimeUnit.SECONDS;
	private final double playRate = 2.0;
    
	// toggle button events handler
	private ActionListener toggleBttnListener = new ActionListener() {
		
		@Override
		public void actionPerformed(ActionEvent e) {
			// no media selected
        	if (playbin.getState() == State.NULL) {
        		((JToggleButton)e.getSource()).setSelected(false);
        		return;
        	}
        	
        	String bttnName = ((JToggleButton)e.getSource()).getName();
        	boolean bttnSelected = ((JToggleButton)e.getSource()).isSelected();
        	if(bttnName.equals("play")) {
        		fforwardBttn.setSelected(false);
        		rewindBttn.setSelected(false);
        		playBttn.setSelected(bttnSelected);
        		
        		if (playBttn.isSelected()){
                	playBttn.setIcon(iPause);
                	playbin.seek(1.0, Format.TIME, SeekFlags.FLUSH | SeekFlags.ACCURATE, SeekType.SET, playbin.queryPosition(Format.TIME), SeekType.SET, -1);
                	playbin.setState(State.PLAYING);
                } else {
                	playBttn.setIcon(iPlay);
                	playbin.setState(State.PAUSED);
                }
        	}
        	else if(bttnName.equals("rewind")) {	
        		fforwardBttn.setSelected(false);
        		if(playBttn.isSelected()) {
        			playBttn.setSelected(false);
        			playBttn.setIcon(iPlay);
        		}
        		playbin.setState(State.PAUSED);
        		
        		if (rewindBttn.isSelected()) {
        			playbin.seek(-playRate, Format.TIME, SeekFlags.FLUSH | SeekFlags.ACCURATE, SeekType.SET, playbin.queryPosition(Format.TIME), SeekType.SET, playbin.queryPosition(Format.TIME));
        			playbin.setState(State.PLAYING);
        		}
        	}
        	else if(bttnName.equals("fforward")) {
        		rewindBttn.setSelected(false);
        		if(playBttn.isSelected()) {
        			playBttn.setSelected(false);
        			playBttn.setIcon(iPlay);
        		}
        		playbin.setState(State.PAUSED);
        		
        		if (fforwardBttn.isSelected()) {
        			playbin.seek(playRate, Format.TIME, SeekFlags.FLUSH | SeekFlags.ACCURATE, SeekType.SET, playbin.queryPosition(Format.TIME), SeekType.SET, -1);
        			playbin.setState(State.PLAYING);
        		}
        	}
		}
	};
    
	// keyboard shortcuts handler
    private KeyEventDispatcher keyboardListener = new KeyEventDispatcher() {
		@Override
		public boolean dispatchKeyEvent(KeyEvent e) {
			if(e.getID() == KeyEvent.KEY_PRESSED) {
				if ((e.getKeyCode() == KeyEvent.VK_O) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
					 int returnVal = fc.showOpenDialog(frame);
					 if (returnVal == JFileChooser.APPROVE_OPTION) {
					    File file = fc.getSelectedFile();
					    loadVideo(file, playbin);
					}
                }
				else if (e.getKeyCode() == KeyEvent.VK_F11) {
					frame.setExtendedState(frame.getExtendedState() ^ JFrame.MAXIMIZED_BOTH);
					bttnsPanel.setVisible(!bttnsPanel.isVisible());
				}
			}
			
			return false;
		}
	};
	
	// mouse events on seekbar handler
	private MouseListener seekListener = new MouseListener() {
		
		@Override
		public void mouseReleased(MouseEvent e) {
			playbin.seek(((JSlider)e.getComponent()).getValue(), unitScale);
			userIsSeeking = false;
			
		}
		
		@Override
		public void mousePressed(MouseEvent e) {
			userIsSeeking = true;
		}
		
		@Override
		public void mouseExited(MouseEvent e) {}
		@Override
		public void mouseEntered(MouseEvent e) {}
		@Override
		public void mouseClicked(MouseEvent e) {}
	};
	
	// thread that updates the seekbar
	private Thread seekThread = new Thread() {
		public void run() {
			while(true) {
				if(!userIsSeeking) {
					long position = playbin.queryPosition(unitScale);
					//long duration = playbin.queryDuration(unitScale);
					
					seekBar.setValue((int)position);
					//seekLabels.put((int)duration, new JLabel(labelFromTime(duration-position)));
					seekLabels.put(0, new JLabel(labelFromTime(position)));
					seekBar.setLabelTable(seekLabels);
				}
				// interesting "bug" with java - if sleeping is in the if statement, slider can get stuck (not update)
				// presumably when you grab the slider this while loop evaluates so many times that java decides to "optimize" it
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {}
			}
		}
	};
	
	public AVPlayer(String[] args) {
		args = Gst.init("AVPlayer", args);
		playbin = new PlayBin2("AVPlayer");

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
            	// frame
                frame = new JFrame("AVPlayer");
                frame.setPreferredSize(new Dimension(640, 480));
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);

                // video component
                VideoComponent videoComponent = new VideoComponent();
                playbin.setVideoSink(videoComponent.getElement());
                playbin.setAudioSink(ElementFactory.make("alsasink", "sortieaudio"));
                
                // keyevents (open file/fullscreen)
                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                kfm.addKeyEventDispatcher(keyboardListener);
                
                // bottom panel
                bttnsPanel = new JPanel(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints() ;
                
                frame.add(videoComponent, BorderLayout.CENTER);
                frame.add(bttnsPanel, BorderLayout.SOUTH);
                
                Dimension bttnDim = new Dimension(30, 30);
                
                // play/pause bttn
                playBttn = new JToggleButton(iPlay);
                playBttn.addActionListener(toggleBttnListener);
                playBttn.setPreferredSize(bttnDim);
                playBttn.setSize(bttnDim);

                playBttn.setName("play");
                gbc.gridx=0;
                gbc.gridy=1;
                
                bttnsPanel.add(playBttn, gbc);
                
                // rewind bttn
                rewindBttn = new JToggleButton(iRewind);
                rewindBttn.addActionListener(toggleBttnListener);
                rewindBttn.setPreferredSize(bttnDim);
                rewindBttn.setSize(bttnDim);

                rewindBttn.setName("rewind");
                gbc.gridx=1;
                gbc.gridy=1;
               
                bttnsPanel.add(rewindBttn, gbc);
                
                // fast forward bttn
                fforwardBttn = new JToggleButton(iForward);
                fforwardBttn.addActionListener(toggleBttnListener);
                fforwardBttn.setPreferredSize(bttnDim);
                fforwardBttn.setSize(bttnDim);

                fforwardBttn.setName("fforward");
                gbc.gridx=2;
                gbc.gridy=1;
                
                bttnsPanel.add(fforwardBttn, gbc);
                
                // seek bar
                seekBar = new JSlider(JSlider.HORIZONTAL, 0, 1, 0);

                seekBar.setPaintTicks(false);
                seekBar.setPaintLabels(true);
                seekBar.setPreferredSize(new Dimension(480, 40));
                seekBar.setSize(new Dimension(480, 40));

                seekBar.addMouseListener(seekListener);
                gbc.gridx=0;
                gbc.gridy=0;
                gbc.gridwidth=6;
                
                bttnsPanel.add(seekBar, gbc);
                gbc.gridwidth=1;
                
                //White space
                JPanel dummy = new JPanel();
                
                dummy.setPreferredSize(new Dimension(100,30));
                gbc.gridx=3;
                gbc.gridy=1;
                bttnsPanel.add(dummy,gbc);
                
                //Mute button
                muteBttn = new JToggleButton(iSpeaker);
                muteBttn.addActionListener(toggleBttnListener);
                muteBttn.setPreferredSize(bttnDim);
                muteBttn.setSize(bttnDim);

                muteBttn.setName("mute");
                gbc.gridx=4;
                gbc.gridy=1;
                
                bttnsPanel.add(muteBttn, gbc);
                
                //Volume Slider
                volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100,50);
                volumeSlider.setPaintTicks(false);
                volumeSlider.setPaintLabels(false);
                volumeSlider.setPreferredSize(new Dimension(90, 40));
                volumeSlider.setSize(new Dimension(90, 40));
                gbc.gridx=5;
                gbc.gridy=1;
                
                bttnsPanel.add(volumeSlider, gbc);
                
                seekThread.start();
                
                loadVideo(null, playbin); // init playbin & seekbar
            }
        });
       
        Gst.main();
        playbin.setState(State.NULL);
	}
	
	private void loadVideo(File f, PlayBin2 playbin) {
		playbin.setState(State.NULL);
		try {
			playbin.setInputFile(f);
			playbin.setState(State.PAUSED);
		}
		catch(Exception e) {
			playbin.setInputFile(new File(""));
		}
		
		// setup the seek bar for this video
		if (playbin.getState() != State.NULL) {
			
			long position = playbin.queryPosition(unitScale);
			long duration = playbin.queryDuration(unitScale);
			
			seekBar.setMaximum((int)duration);
			seekBar.setValue((int)position);
			

			seekLabels = new Hashtable<Integer,JLabel>();
			seekLabels.put(0,new JLabel("00:00"));
			seekLabels.put((int)duration, new JLabel(labelFromTime(duration)));
			
		}
		else {
			seekBar.setMaximum(1);
			seekBar.setValue(0);
			
			seekLabels.put(0,new JLabel("00:00"));
			seekLabels.put(1,new JLabel("00:00"));
		}
		
		seekBar.setLabelTable(seekLabels);

	}
	
	private String labelFromTime(long time) {
		int intMin = (int) (time/60);
		String min = (intMin < 10 ? "0" : "")+intMin;

		
		int intSec = (int) (time - (60*intMin));
		String sec = (intSec < 10 ? "0" : "")+intSec;
		
		return min+":"+sec;
	}
	
    public static void main(String[] args) {
    	new AVPlayer(args);
    }
}
