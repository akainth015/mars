package mars.venus;

import mars.Globals;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action  for the Help -> About menu item
 */
public class HelpAboutAction extends GuiAction {
    public HelpAboutAction(String name, Icon icon, String descrip,
                           Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    public void actionPerformed(ActionEvent e) {
        JOptionPane.showMessageDialog(mainUI,
                "MARS " + Globals.version + "    Copyright " + Globals.copyrightYears + "\n" +
                        Globals.copyrightHolders + "\n" +
                        "MARS is the Mips Assembler and Runtime Simulator.\n\n" +
                        "Mars image courtesy of NASA/JPL.\n" +
                        "Toolbar and menu icons are from:\n" +
                        "  *  Tango Desktop Project (tango.freedesktop.org),\n" +
                        "  *  glyFX (www.glyfx.com) Common Toolbar Set,\n" +
                        "  *  KDE-Look (www.kde-look.org) crystalline-blue-0.1,\n" +
                        "  *  Icon-King (www.icon-king.com) Nuvola 1.0.\n" +
                        "Print feature adapted from HardcopyWriter class in David Flanagan's\n" +
                        "Java Examples in a Nutshell 3rd Edition, O'Reilly, ISBN 0-596-00620-9.",
                "About Mars",
                JOptionPane.INFORMATION_MESSAGE,
                new ImageIcon("images/RedMars50.gif"));
    }
}