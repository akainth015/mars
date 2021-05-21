package mars.venus;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;


/**
 * Action  for the File -> Print menu item
 */
public class FilePrintAction extends GuiAction {

    public FilePrintAction(String name, Icon icon, String descrip,
                           Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    /**
     * Uses the HardcopyWriter class developed by David Flanagan for the book
     * "Java Examples in a Nutshell".  It will do basic printing of multipage
     * text documents.  It displays a print dialog but does not act on any
     * changes the user may have specified there, such as number of copies.
     *
     * @param e component triggering this call
     */

    public void actionPerformed(ActionEvent e) {
        EditPane editPane = mainUI.getMainPane().getEditPane();
        if (editPane == null) return;
        int fontsize = 10;  // fixed at 10 point
        double margins = .5; // all margins (left,right,top,bottom) fixed at .5"
        HardcopyWriter out;
        try {
            out = new HardcopyWriter(mainUI, editPane.getFilename(),
                    fontsize, margins, margins, margins, margins);
        } catch (HardcopyWriter.PrintCanceledException pce) {
            return;
        }
        BufferedReader in = new BufferedReader(new StringReader(editPane.getSource()));
        int lineNumberDigits = new Integer(editPane.getSourceLineCount()).toString().length();
        String line;
        String lineNumberString = "";
        int lineNumber = 0;
        int numchars;
        try {
            line = in.readLine();
            while (line != null) {
                if (editPane.showingLineNumbers()) {
                    lineNumber++;
                    lineNumberString = new Integer(lineNumber) + ": ";
                    while (lineNumberString.length() < lineNumberDigits) {
                        lineNumberString = lineNumberString + " ";
                    }
                }
                line = lineNumberString + line + "\n";
                out.write(line.toCharArray(), 0, line.length());
                line = in.readLine();
            }
            in.close();
            out.close();
        } catch (IOException ioe) {
        }
        return;
    }
}
