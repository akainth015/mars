package mars.venus;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * parent class for Action subclasses to be defined for every menu/toolbar
 * option.
 */

public class GuiAction extends AbstractAction {
    protected VenusUI mainUI;

    protected GuiAction(String name, Icon icon, String descrip,
                        Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon);
        putValue(SHORT_DESCRIPTION, descrip);
        putValue(MNEMONIC_KEY, mnemonic);
        putValue(ACCELERATOR_KEY, accel);
        mainUI = gui;
    }

    /**
     * does nothing by default.  Should be over-ridden by subclass
     */
    public void actionPerformed(ActionEvent e) {

    }
}