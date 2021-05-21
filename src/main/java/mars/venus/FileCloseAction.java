package mars.venus;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action  for the File -> Close menu item
 */
public class FileCloseAction extends GuiAction {

    public FileCloseAction(String name, Icon icon, String descrip,
                           Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        mainUI.editor.close();
    }
}