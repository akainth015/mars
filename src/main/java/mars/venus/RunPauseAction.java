package mars.venus;

import mars.simulator.Simulator;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action class for the Run -> Pause menu item (and toolbar icon)
 */
public class RunPauseAction extends GuiAction {

    public RunPauseAction(String name, Icon icon, String descrip,
                          Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        Simulator.getInstance().stopExecution(this);
        // RunGoAction's "paused" method will do the cleanup.
    }

}