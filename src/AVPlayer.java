import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
	private JPanel spacerPanel;
	private Dimension bttnDim = new Dimension(30, 30);
	private Dimension volumeSliderDim = new Dimension(90, 40);
	/**
	 * saved frame size (before entering fullscreen)
	 */
	private Dimension prevFrameDim;

	/**
	 * set to true if user has pressed LMB on the seekbar and set to false when LMB is released
	 */
	private boolean userIsSeeking = false;

	/**
	 * seekbar labels
	 */
	private Dictionary<Integer, JLabel> seekLabels = new Hashtable<Integer, JLabel>();

	/**
	 * file chooser dialog
	 */
	private final JFileChooser fc = new JFileChooser();

	private File log;

	private String latestLoaded;
	/**
	 * Icon for the play button
	 */
	private final Icon iPlay = new ImageIcon("imgs/play.png");
	/**
	 * Icon for the pause button
	 */
	private final Icon iPause = new ImageIcon("imgs/pause.png");
	/**
	 * Icon for fast forward button
	 */
	private final Icon iForward = new ImageIcon("imgs/fforward.png");
	/**
	 * Icon for rewind button
	 */
	private final Icon iRewind = new ImageIcon("imgs/rewind.png");
	/**
	 * Icon for speaker button, if muted
	 */
	private final Icon iMute = new ImageIcon("imgs/mute.png");
	/**
	 * Icon for speaker button, if not muted
	 */
	private final Icon iSpeaker = new ImageIcon("imgs/speaker.png");

	/**
	 * time unit we use everywhere in the project
	 */
	private final TimeUnit unitScale = TimeUnit.SECONDS;
	
	/**
	 * playback rate for fast-forwarding and rewinding
	 */
	private final double playRate = 2.0;

	/**
	 * Handler for all JToggleButton events, like play, rewind, mute. 
	 * Pressing one of play, rewind, or fast-forward buttons should deselect the other two and set the playrate accordingly.
	 * Mute disables the sound.  
	 */
	private ActionListener toggleBttnListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			// no media selected
			if (playbin.getState() == State.NULL) {
				((JToggleButton) e.getSource()).setSelected(false);
				return;
			}

			String bttnName = ((JToggleButton) e.getSource()).getName();
			boolean bttnSelected = ((JToggleButton) e.getSource()).isSelected();
			// handles interaction with play button
			if (bttnName.equals("play")) {
				fforwardBttn.setSelected(false);
				rewindBttn.setSelected(false);
				playBttn.setSelected(bttnSelected);

				if (playBttn.isSelected()) {
					playBttn.setIcon(iPause);
					playbin.seek(1.0, Format.TIME, SeekFlags.FLUSH
							| SeekFlags.ACCURATE, SeekType.SET,
							playbin.queryPosition(Format.TIME), SeekType.SET,
							-1);

					playbin.setState(State.PLAYING);
				} else {
					playBttn.setIcon(iPlay);
					playbin.setState(State.PAUSED);
				}
				// handles interaction with rewind button
			} else if (bttnName.equals("rewind")) {
				fforwardBttn.setSelected(false);
				if (playBttn.isSelected()) {
					playBttn.setSelected(false);
					playBttn.setIcon(iPlay);
				}
				playbin.setState(State.PAUSED);

				if (rewindBttn.isSelected()) {
					playbin.seek(-playRate, Format.TIME, SeekFlags.FLUSH
							| SeekFlags.ACCURATE, SeekType.SET,
							playbin.queryPosition(Format.TIME), SeekType.SET,
							playbin.queryPosition(Format.TIME));
					playbin.setState(State.PLAYING);
				}
				// handles interaction with fast forward buttons
			} else if (bttnName.equals("fforward")) {
				rewindBttn.setSelected(false);
				if (playBttn.isSelected()) {
					playBttn.setSelected(false);
					playBttn.setIcon(iPlay);
				}
				playbin.setState(State.PAUSED);

				if (fforwardBttn.isSelected()) {
					playbin.seek(playRate, Format.TIME, SeekFlags.FLUSH
							| SeekFlags.ACCURATE, SeekType.SET,
							playbin.queryPosition(Format.TIME), SeekType.SET,
							-1);
					playbin.setState(State.PLAYING);
				}
				// handles interaction with mute button
			} else if (bttnName.equals("mute")) {
				if (muteBttn.isSelected()) {
					muteBttn.setSelected(true);
					muteBttn.setIcon(iMute);
					playbin.setVolumePercent(0);
				} else {
					muteBttn.setSelected(false);
					muteBttn.setIcon(iSpeaker);
					playbin.setVolumePercent(volumeSlider.getValue());
				}
			}
		}
	};

	/**
	 * Handler for keyboard shortcuts.
	 * Ctrl+O opens a media file
	 * F11 toggles fullscreen
	 */
	private KeyEventDispatcher keyboardListener = new KeyEventDispatcher() {
		@Override
		public boolean dispatchKeyEvent(KeyEvent e) {
			// with ctrl + o a filechooser will open
			if (e.getID() == KeyEvent.KEY_PRESSED) {
				if ((e.getKeyCode() == KeyEvent.VK_O)
						&& ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
					int returnVal = fc.showOpenDialog(frame);
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						File file = fc.getSelectedFile();
						latestLoaded = fc.getSelectedFile().getAbsolutePath();
						loadVideo(file, playbin);
					}
					// F11 will switch to fullscreen
				} else if (e.getKeyCode() == KeyEvent.VK_F11) {
					if (frame.isUndecorated()) {
						frame.setSize(prevFrameDim);
						resizeListener.componentResized(null);
					}
					else {
						prevFrameDim = frame.getSize();
					}
					frame.setExtendedState(frame.getExtendedState()
							^ JFrame.MAXIMIZED_BOTH);
					bttnsPanel.setVisible(!bttnsPanel.isVisible());
					frame.dispose();
					frame.setUndecorated(!frame.isUndecorated());
					frame.setVisible(true);
				}
			}

			return false;
		}
	};

	/**
	 * Handler for mouse events on the seekbar.
	 * Briefly clicking anywhere on the seekbar sets the video to that position. 
	 * Position can also be set by dragging the slider.
	 */
	private MouseListener seekListener = new MouseListener() {

		private long timePressed = 0;

		@Override
		public void mouseReleased(MouseEvent e) {
			// lets the user seek
			playbin.seek(((JSlider) e.getComponent()).getValue(), unitScale);
			// seeking while fast-forwarding or rewinding should set playback speed to normal 
			if(rewindBttn.isSelected() || fforwardBttn.isSelected()) { 
				playBttn.doClick();
			}
			userIsSeeking = false;

		}

		@Override
		public void mousePressed(MouseEvent e) {
			userIsSeeking = true;
			timePressed = System.currentTimeMillis();
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			// user can click on the time bar and hop to that point
			// timeDifference is needed, otherwise seeking will be overwritten
			long timeNow = System.currentTimeMillis();
			//System.out.println(timeNow - timePressed);
			long timeDifference = timeNow - timePressed;
			if (seekBar.getWidth() != 0 && timeDifference < 150) {
				Point mouse = e.getPoint();
				double percent = (double) mouse.x / (double) seekBar.getWidth();
				long duration = playbin.queryDuration(unitScale);
				playbin.seek((long) (duration * percent), unitScale);
			}
		}
	};

	/**
	 * Thread that updates the seekbar during video playback.
	 */
	private Thread seekThread = new Thread() {
		public void run() {
			while (true) {
				if (!userIsSeeking) {
					long position = playbin.queryPosition(unitScale);
					// long duration = playbin.queryDuration(unitScale);

					seekBar.setValue((int) position);
					// seekLabels.put((int)duration, new
					// JLabel(labelFromTime(duration-position)));
					seekLabels.put(0, new JLabel(labelFromTime(position)));
					seekBar.setLabelTable(seekLabels);
				}
				// interesting "bug" with java - if sleeping is in the if
				// statement, slider can get stuck (not update)
				// presumably when you grab the slider this while loop evaluates
				// so many times that java decides to "optimize" it
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
		}
	};
	
	/**
	 * Handler for the volume slider.
	 * Sets the volume of the media.
	 */
	private ChangeListener volumeListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			int volume = ((JSlider)e.getSource()).getValue();
			playbin.setVolumePercent(volume);
		}
	};
	
	/**
	 * Handler for scaling the seekbar and button spacing when the player is resized.
	 * Resizes the seekbar to fill the window width, aligns volume controls to the right, playback buttons to the left.
	 */
	private ComponentAdapter resizeListener = new ComponentAdapter() {  
	    public void componentResized(ComponentEvent evt) {
		    int margin = 20;
		    int frameWidth = frame.getWidth();
		    seekBar.setPreferredSize(new Dimension(frameWidth-margin, 40));
		    int spacerWidth = (int)(frameWidth - (4*bttnDim.getWidth()+volumeSliderDim.getWidth()));
		    spacerPanel.setPreferredSize(new Dimension(spacerWidth-margin, 30));
        }
	};

	/**
	 * Main part of the code.
	 * Creates the GUI, initializes everything, and loads a dummy file to start off with.
	 * @param args 
	 *            commandline parameters
	 */
	public AVPlayer(String[] args) {
		// initialisation of gstreamer and start the player
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
				// on exit, the last played file and the current volume will be saved
				frame.addWindowListener(new WindowAdapter() {
					public void windowClosing(WindowEvent we) {
						try (Writer writer = new BufferedWriter(
								new OutputStreamWriter(new FileOutputStream(
										"log/log.txt"), "utf-8"))) {
							String toWrite = latestLoaded + "\n" + volumeSlider.getValue();
							writer.write(toWrite);
						} catch (Exception e) {
						}
					}
				});
				
				frame.addComponentListener(resizeListener);

				// video component
				VideoComponent videoComponent = new VideoComponent();
				playbin.setVideoSink(videoComponent.getElement());
				playbin.setAudioSink(ElementFactory.make("alsasink",
						"sortieaudio"));

				// keyevents (open file/fullscreen)
				KeyboardFocusManager kfm = KeyboardFocusManager
						.getCurrentKeyboardFocusManager();
				kfm.addKeyEventDispatcher(keyboardListener);

				// bottom panel
				bttnsPanel = new JPanel(new GridBagLayout());
				GridBagConstraints gbc = new GridBagConstraints();

				frame.add(videoComponent, BorderLayout.CENTER);
				frame.add(bttnsPanel, BorderLayout.SOUTH);

				// play/pause bttn
				playBttn = new JToggleButton(iPlay);
				playBttn.addActionListener(toggleBttnListener);
				playBttn.setPreferredSize(bttnDim);

				playBttn.setName("play");
				gbc.gridx = 0;
				gbc.gridy = 1;

				bttnsPanel.add(playBttn, gbc);

				// rewind bttn
				rewindBttn = new JToggleButton(iRewind);
				rewindBttn.addActionListener(toggleBttnListener);
				rewindBttn.setPreferredSize(bttnDim);

				rewindBttn.setName("rewind");
				gbc.gridx = 1;
				gbc.gridy = 1;

				bttnsPanel.add(rewindBttn, gbc);

				// fast forward bttn
				fforwardBttn = new JToggleButton(iForward);
				fforwardBttn.addActionListener(toggleBttnListener);
				fforwardBttn.setPreferredSize(bttnDim);

				fforwardBttn.setName("fforward");
				gbc.gridx = 2;
				gbc.gridy = 1;

				bttnsPanel.add(fforwardBttn, gbc);

				// seek bar
				seekBar = new JSlider(JSlider.HORIZONTAL, 0, 1, 0);

				seekBar.setPaintTicks(false);
				seekBar.setPaintLabels(true);
				seekBar.setPreferredSize(new Dimension(480, 40));

				seekBar.addMouseListener(seekListener);
				gbc.gridx = 0;
				gbc.gridy = 0;
				gbc.gridwidth = 6;

				bttnsPanel.add(seekBar, gbc);
				gbc.gridwidth = 1;

				// White space
				spacerPanel = new JPanel();

				spacerPanel.setPreferredSize(new Dimension(300, 30));
				gbc.gridx = 3;
				gbc.gridy = 1;
				bttnsPanel.add(spacerPanel, gbc);

				// Mute button
				muteBttn = new JToggleButton(iSpeaker);
				muteBttn.addActionListener(toggleBttnListener);
				muteBttn.setPreferredSize(bttnDim);

				muteBttn.setName("mute");
				gbc.gridx = 4;
				gbc.gridy = 1;

				bttnsPanel.add(muteBttn, gbc);

				// Volume Slider
				volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 100);
				volumeSlider.setPaintTicks(false);
				volumeSlider.setPaintLabels(false);
				volumeSlider.setPreferredSize(volumeSliderDim);
				volumeSlider.addChangeListener(volumeListener);
				gbc.gridx = 5;
				gbc.gridy = 1;

				bttnsPanel.add(volumeSlider, gbc);
				
				resizeListener.componentResized(null);

				// the last played file and the current volume are loaded
				log = new File("log/log.txt");
				File f = null;
				try {
					log.createNewFile();
					FileReader fr = new FileReader(log);
					BufferedReader br = new BufferedReader(fr);
					String title = br.readLine();
					String volume = br.readLine();
					if (title != null) {
						f = new File(title);
						latestLoaded = title;
					}
					if (volume != null) {
						volumeSlider.setValue(Integer.parseInt(volume));
					}

				} catch (Exception e) {
				}
				seekThread.start();

				loadVideo(f, playbin); // init playbin & seekbar
				setFileFilters();
			}
		});

		Gst.main();
		playbin.setState(State.NULL);
	}

	/**
	 * Loads a video into the playbin. Video will be paused. If the given file
	 * does not exist or is corrupted, a dummy file will be loaded
	 * 
	 * @param f
	 *            a media file
	 * @param playbin
	 *            a gstreamer pipeline
	 */
	private void loadVideo(File f, PlayBin2 playbin) {
		playbin.setState(State.NULL);
		try {
			playbin.setInputFile(f);
			playbin.setState(State.PAUSED);
		} catch (Exception e) {
			playbin.setInputFile(new File(""));
		}

		// setup the seek bar for this video
		if (playbin.getState() != State.NULL) {

			long position = playbin.queryPosition(unitScale);
			long duration = playbin.queryDuration(unitScale);

			seekBar.setMaximum((int) duration);
			seekBar.setValue((int) position);

			seekLabels = new Hashtable<Integer, JLabel>();
			seekLabels.put(0, new JLabel("00:00"));
			seekLabels.put((int) duration, new JLabel(labelFromTime(duration)));

		} else {
			seekBar.setMaximum(1);
			seekBar.setValue(0);

			seekLabels.put(0, new JLabel("00:00"));
			seekLabels.put(1, new JLabel("00:00"));
		}

		seekBar.setLabelTable(seekLabels);

	}

	/**
	 * Formats a given time for the labels of the seekBar
	 * 
	 * @param time
	 *            which should be formated
	 * @return String in the format MM:SS
	 */
	private String labelFromTime(long time) {
		int intMin = (int) (time / 60);
		String min = (intMin < 10 ? "0" : "") + intMin;

		int intSec = (int) (time - (60 * intMin));
		String sec = (intSec < 10 ? "0" : "") + intSec;

		return min + ":" + sec;
	}

	public static void main(String[] args) {
		new AVPlayer(args);
	}

	/**
	 * creates and ads file filters for the JFileChooser
	 */
	private void setFileFilters() {
		ArrayList<FileNameExtensionFilter> filters = new ArrayList<FileNameExtensionFilter>();
		fc.setAcceptAllFileFilterUsed(true);
		filters.add(new FileNameExtensionFilter("avi", "avi"));
		filters.add(new FileNameExtensionFilter("mp3", "mp3"));
		filters.add(new FileNameExtensionFilter("mp4", "mp4"));
		filters.add(new FileNameExtensionFilter("RealAudio", "ra", "rm"));
		filters.add(new FileNameExtensionFilter("wav", "wav"));
		filters.add(new FileNameExtensionFilter("webm", "webm"));
		for (FileNameExtensionFilter f : filters) {
			fc.addChoosableFileFilter(f);
		}

	}
}
