package mars.venus;

import mars.Globals;
import mars.ProcessingException;
import mars.mips.hardware.RegisterFile;
import mars.simulator.ProgramArgumentList;
import mars.simulator.Simulator;

import javax.swing.*;
import java.awt.event.ActionEvent;


/**
 * Action  for the Run -> Step menu item
 */
public class RunStepAction extends GuiAction {

    String name;
    ExecutePane executePane;

    public RunStepAction(String name, Icon icon, String descrip,
                         Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
    }

    /**
     * perform next simulated instruction step.
     */
    public void actionPerformed(ActionEvent e) {
        name = this.getValue(Action.NAME).toString();
        executePane = mainUI.getMainPane().getExecutePane();
        boolean done = false;
        if (FileStatus.isAssembled()) {
            if (!VenusUI.getStarted()) {  // DPS 17-July-2008
                processProgramArgumentsIfAny();
            }
            VenusUI.setStarted(true);
            mainUI.messagesPane.setSelectedComponent(mainUI.messagesPane.runTab);
            executePane.getTextSegmentWindow().setCodeHighlighting(true);
            try {
                done = Globals.program.simulateStepAtPC(this);
            } catch (ProcessingException ev) {
            }
        } else {
            // note: this should never occur since "Step" is only enabled after successful assembly.
            JOptionPane.showMessageDialog(mainUI, "The program must be assembled before it can be run.");
        }
    }

    // When step is completed, control returns here (from execution thread, indirectly)
    // to update the GUI.
    public void stepped(boolean done, int reason, ProcessingException pe) {
        executePane.getRegistersWindow().updateRegisters();
        executePane.getCoprocessor1Window().updateRegisters();
        executePane.getCoprocessor0Window().updateRegisters();
        executePane.getDataSegmentWindow().updateValues();
        if (!done) {
            executePane.getTextSegmentWindow().highlightStepAtPC();
            FileStatus.set(FileStatus.RUNNABLE);
        }
        if (done) {
            RunGoAction.resetMaxSteps();
            executePane.getTextSegmentWindow().unhighlightAllSteps();
            FileStatus.set(FileStatus.TERMINATED);
        }
        if (done && pe == null) {
            mainUI.getMessagesPane().postMarsMessage(
                    "\n" + name + ": execution " +
                            ((reason == Simulator.CLIFF_TERMINATION) ? "terminated due to null instruction."
                                    : "completed successfully.") + "\n\n");
            mainUI.getMessagesPane().postRunMessage(
                    "\n-- program is finished running " +
                            ((reason == Simulator.CLIFF_TERMINATION) ? "(dropped off bottom)" : "") + " --\n\n");
            mainUI.getMessagesPane().selectRunMessageTab();
        }
        if (pe != null) {
            RunGoAction.resetMaxSteps();
            mainUI.getMessagesPane().postMarsMessage(
                    pe.errors().generateErrorReport());
            mainUI.getMessagesPane().postMarsMessage(
                    "\n" + name + ": execution terminated with errors.\n\n");
            mainUI.getRegistersPane().setSelectedComponent(executePane.getCoprocessor0Window());
            FileStatus.set(FileStatus.TERMINATED); // should be redundant.
            executePane.getTextSegmentWindow().setCodeHighlighting(true);
            executePane.getTextSegmentWindow().unhighlightAllSteps();
            executePane.getTextSegmentWindow().highlightStepAtAddress(RegisterFile.getProgramCounter() - 4);
        }
        VenusUI.setReset(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // Method to store any program arguments into MIPS memory and registers before
    // execution begins. Arguments go into the gap between $sp and kernel memory.
    // Argument pointers and count go into runtime stack and $sp is adjusted accordingly.
    // $a0 gets argument count (argc), $a1 gets stack address of first arg pointer (argv).
    private void processProgramArgumentsIfAny() {
        String programArguments = executePane.getTextSegmentWindow().getProgramArguments();
        if (programArguments == null || programArguments.length() == 0 ||
                !Globals.getSettings().getProgramArguments()) {
            return;
        }
        new ProgramArgumentList(programArguments).storeProgramArguments();
    }
}