package mars.venus;

import mars.Globals;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action class for the Settings menu item to control display of Labels window (symbol table).
 */
public class SettingsLabelAction extends GuiAction {


    public SettingsLabelAction(String name, Icon icon, String descrip,
                               Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        boolean visibility = ((JCheckBoxMenuItem) e.getSource()).isSelected();
        Globals.getGui().getMainPane().getExecutePane().setLabelWindowVisibility(visibility);
        Globals.getSettings().setLabelWindowVisibility(visibility);
    }

}