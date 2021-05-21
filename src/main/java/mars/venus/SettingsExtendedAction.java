package mars.venus;

import mars.Globals;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action class for the Settings menu item to control use of extended (pseudo) instructions or formats.
 */
public class SettingsExtendedAction extends GuiAction {


    public SettingsExtendedAction(String name, Icon icon, String descrip,
                                  Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        Globals.getSettings().setExtendedAssemblerEnabled(((JCheckBoxMenuItem) e.getSource()).isSelected());
    }

}