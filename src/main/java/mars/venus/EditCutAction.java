package mars.venus;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action  for the Edit -> Cut menu item
 */
public class EditCutAction extends GuiAction {

    public EditCutAction(String name, Icon icon, String descrip,
                         Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        mainUI.getMainPane().getEditPane().cutText();
    }
}