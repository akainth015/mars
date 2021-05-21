package mars.venus;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action  for the Edit -> Paste menu item
 */
public class EditPasteAction extends GuiAction {

    public EditPasteAction(String name, Icon icon, String descrip,
                           Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        mainUI.getMainPane().getEditPane().pasteText();
    }
}