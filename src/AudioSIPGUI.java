import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;


public class AudioSIPGUI {
	private JFrame frame;
	private JPanel buttonPanel;
	private JPanel infoPanel;
	private JButton callButton;
	private JButton stopButton;

	private final Dimension buttonPanelDimension = new Dimension(180,400);
	private final Dimension infoPanelDimension = new Dimension(180,400);
	
	//If the window is resize, we have to resize each component
	private ComponentAdapter resizeListener = new ComponentAdapter() {  
		    public void componentResized(ComponentEvent evt) {
		    	//save the window's dimensions
			    Dimension windowSize = frame.getSize();
			    //resize each component according to the window's dimensions
			    infoPanel.setPreferredSize(new Dimension(180,windowSize.height-10));
	            buttonPanel.setPreferredSize(new Dimension(windowSize.width-panelDimension.width-20,windowSize.height-10));
	            callButton.setPreferredSize(new Dimension(panelDimension.width,30));
	            stopButton.setSize(new Dimension(buttonPanelDimensionbuttonPanelDimension.width,30));
	        }
		};
}
