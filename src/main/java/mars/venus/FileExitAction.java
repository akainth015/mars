package mars.venus;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action  for the File -> Exit menu item
 */
public class FileExitAction extends GuiAction {

    public FileExitAction(String name, Icon icon, String descrip,
                          Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    /**
     * Exit MARS, unless one or more files have unsaved edits and user cancels.
     */
    public void actionPerformed(ActionEvent e) {
        if (mainUI.editor.closeAll()) {
            System.exit(0);
        }
    }
}
