package mars.venus;

import mars.Globals;
import mars.mips.hardware.MemoryConfiguration;
import mars.mips.hardware.MemoryConfigurations;
import mars.simulator.Simulator;
import mars.util.Binary;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Action class for the Settings menu item for text editor settings.
 */
public class SettingsMemoryConfigurationAction extends GuiAction {

    JDialog configDialog;
    SettingsMemoryConfigurationAction thisAction;

    /**
     * Create a new SettingsEditorAction.  Has all the GuiAction parameters.
     */
    public SettingsMemoryConfigurationAction(String name, Icon icon, String descrip,
                                             Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, descrip, mnemonic, accel, gui);
        thisAction = this;
    }

    /**
     * When this action is triggered, launch a dialog to view and modify
     * editor settings.
     */
    public void actionPerformed(ActionEvent e) {
        configDialog = new MemoryConfigurationDialog(Globals.getGui(), "MIPS Memory Configuration", true);
        configDialog.setVisible(true);
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //   Private class to do all the work!
    //
    private class MemoryConfigurationDialog extends JDialog implements ActionListener {
        JTextField[] addressDisplay;
        JLabel[] nameDisplay;
        ConfigurationButton selectedConfigurationButton, initialConfigurationButton;

        public MemoryConfigurationDialog(Frame owner, String title, boolean modality) {
            super(owner, title, modality);
            this.setContentPane(buildDialogPanel());
            this.setDefaultCloseOperation(
                    JDialog.DO_NOTHING_ON_CLOSE);
            this.addWindowListener(
                    new WindowAdapter() {
                        public void windowClosing(WindowEvent we) {
                            performClose();
                        }
                    });
            this.pack();
            this.setLocationRelativeTo(owner);
        }

        private JPanel buildDialogPanel() {
            JPanel dialogPanel = new JPanel(new BorderLayout());
            dialogPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel configInfo = new JPanel(new FlowLayout());
            MemoryConfigurations.buildConfigurationCollection();
            configInfo.add(buildConfigChooser());
            configInfo.add(buildConfigDisplay());
            dialogPanel.add(configInfo);
            dialogPanel.add(buildControlPanel(), BorderLayout.SOUTH);
            return dialogPanel;
        }

        private Component buildConfigChooser() {
            JPanel chooserPanel = new JPanel(new GridLayout(4, 1));
            ButtonGroup choices = new ButtonGroup();
            Iterator<MemoryConfiguration> configurationsIterator = MemoryConfigurations.getConfigurationsIterator();
            while (configurationsIterator.hasNext()) {
                MemoryConfiguration config = configurationsIterator.next();
                ConfigurationButton button = new ConfigurationButton(config);
                button.addActionListener(this);
                if (button.isSelected()) {
                    this.selectedConfigurationButton = button;
                    this.initialConfigurationButton = button;
                }
                choices.add(button);
                chooserPanel.add(button);
            }
            chooserPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.BLACK)
                    , "Configuration"));
            return chooserPanel;
        }


        private Component buildConfigDisplay() {
            JPanel displayPanel = new JPanel();
            MemoryConfiguration config = MemoryConfigurations.INSTANCE.getCurrentConfiguration();
            String[] configurationItemNames = MemoryConfigurations.configurationItemNames;
            int numItems = configurationItemNames.length;
            JPanel namesPanel = new JPanel(new GridLayout(numItems, 1));
            JPanel valuesPanel = new JPanel(new GridLayout(numItems, 1));
            Font monospaced = new Font("Monospaced", Font.PLAIN, 12);
            nameDisplay = new JLabel[numItems];
            addressDisplay = new JTextField[numItems];
            for (int i = 0; i < numItems; i++) {
                nameDisplay[i] = new JLabel();
                addressDisplay[i] = new JTextField();
                addressDisplay[i].setEditable(false);
                addressDisplay[i].setFont(monospaced);
            }
            // Display vertically from high to low memory addresses so
            // add the components in reverse order.
            for (int i = addressDisplay.length - 1; i >= 0; i--) {
                namesPanel.add(nameDisplay[i]);
                valuesPanel.add(addressDisplay[i]);
            }
            setConfigDisplay(config);
            Box columns = Box.createHorizontalBox();
            columns.add(valuesPanel);
            columns.add(Box.createHorizontalStrut(6));
            columns.add(namesPanel);
            displayPanel.add(columns);
            return displayPanel;
        }


        // Carry out action for the radio buttons.
        public void actionPerformed(ActionEvent e) {
            MemoryConfiguration config = ((ConfigurationButton) e.getSource()).getConfiguration();
            setConfigDisplay(config);
            this.selectedConfigurationButton = (ConfigurationButton) e.getSource();
        }


        // Row of control buttons to be placed along the button of the dialog
        private Component buildControlPanel() {
            Box controlPanel = Box.createHorizontalBox();
            JButton okButton = new JButton("Apply and Close");
            okButton.setToolTipText(SettingsHighlightingAction.CLOSE_TOOL_TIP_TEXT);
            okButton.addActionListener(
                    e -> {
                        performApply();
                        performClose();
                    });
            JButton applyButton = new JButton("Apply");
            applyButton.setToolTipText(SettingsHighlightingAction.APPLY_TOOL_TIP_TEXT);
            applyButton.addActionListener(
                    e -> performApply());
            JButton cancelButton = new JButton("Cancel");
            cancelButton.setToolTipText(SettingsHighlightingAction.CANCEL_TOOL_TIP_TEXT);
            cancelButton.addActionListener(
                    e -> performClose());
            JButton resetButton = new JButton("Reset");
            resetButton.setToolTipText(SettingsHighlightingAction.RESET_TOOL_TIP_TEXT);
            resetButton.addActionListener(
                    e -> performReset());
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(okButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(applyButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(cancelButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(resetButton);
            controlPanel.add(Box.createHorizontalGlue());
            return controlPanel;
        }

        private void performApply() {
            MemoryConfigurations.INSTANCE.setCurrentConfiguration(this.selectedConfigurationButton.getConfiguration());
            Globals.getSettings().setMemoryConfiguration(this.selectedConfigurationButton.getConfiguration().name());
            Globals.getGui().getRegistersPane().getRegistersWindow().clearHighlighting();
            Globals.getGui().getRegistersPane().getRegistersWindow().updateRegisters();
            Globals.getGui().getMainPane().getExecutePane().getDataSegmentWindow().updateBaseAddressComboBox();
            // 21 July 2009 Re-assemble if the situation demands it to maintain consistency.
            if (FileStatus.get() == FileStatus.RUNNABLE ||
                    FileStatus.get() == FileStatus.RUNNING ||
                    FileStatus.get() == FileStatus.TERMINATED) {
                // Stop execution if executing -- should NEVER happen because this
                // Action's widget is disabled during MIPS execution.
                if (FileStatus.get() == FileStatus.RUNNING) {
                    Simulator.getInstance().stopExecution(thisAction);
                }
                Globals.getGui().getRunAssembleAction().actionPerformed(null);
            }
        }

        private void performClose() {
            this.setVisible(false);
            this.dispose();
        }

        private void performReset() {
            this.selectedConfigurationButton = this.initialConfigurationButton;
            this.selectedConfigurationButton.setSelected(true);
            setConfigDisplay(this.selectedConfigurationButton.getConfiguration());
        }


        // Set name values in JLabels and address values in the JTextFields
        private void setConfigDisplay(MemoryConfiguration config) {
            String[] configurationItemNames = MemoryConfigurations.configurationItemNames;
            int[] configurationItemValues = config.getListFormat();
            // Will use TreeMap to extract list of address-name pairs sorted by
            // hex-stringified address. This will correctly handle kernel addresses,
            // whose int values are negative and thus normal sorting yields incorrect
            // results.  There can be duplicate addresses, so I concatenate the name
            // onto the address to make each key unique.  Then slice off the name upon
            // extraction.
            TreeMap<String, String> treeSortedByAddress = new TreeMap<>();
            for (int i = 0; i < configurationItemValues.length; i++) {
                treeSortedByAddress.put(Binary.intToHexString(configurationItemValues[i]) + configurationItemNames[i], configurationItemNames[i]);
            }
            Iterator<Map.Entry<String, String>> setSortedByAddress = treeSortedByAddress.entrySet().iterator();
            int addressStringLength = Binary.intToHexString(configurationItemValues[0]).length();
            for (int i = 0; i < configurationItemValues.length; i++) {
                Map.Entry<String, String> pair = setSortedByAddress.next();
                nameDisplay[i].setText(pair.getValue());
                addressDisplay[i].setText(pair.getKey().substring(0, addressStringLength));
            }
        }

    }

    // Handy class to connect button to its configuration...
    private static class ConfigurationButton extends JRadioButton {
        private final MemoryConfiguration configuration;

        public ConfigurationButton(MemoryConfiguration config) {
            super(config.name(), config == MemoryConfigurations.INSTANCE.getCurrentConfiguration());
            this.configuration = config;
        }

        public MemoryConfiguration getConfiguration() {
            return configuration;
        }

    }

}