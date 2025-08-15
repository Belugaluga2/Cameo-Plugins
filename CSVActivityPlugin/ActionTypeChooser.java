package com.example.csvactivityplugin;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.nomagic.uml2.impl.PropertyNames;

import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Dialog that allows users to choose the action type for each imported activity.
 * Users can select between:
 * - Structured Activity Node (default)
 * - Call Behavior Action
 */
public class ActionTypeChooser extends JDialog {
    
    public enum ActionType {
        STRUCTURED_ACTIVITY("Structured Activity Node"),
        CALL_BEHAVIOR("Call Behavior Action");
        
        private final String displayName;
        
        ActionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private JTable actionTable;
    private DefaultTableModel tableModel;
    private Map<String, ActionType> actionTypeMap;
    private boolean userConfirmed = false;
    
    /**
     * Creates a new ActionTypeChooser dialog.
     * 
     * @param parent The parent frame for this dialog
     * @param activities List of activities to choose types for
     */
    public ActionTypeChooser(Frame parent, List<ActivityData> activities) {
        super(parent, "Choose Action Types", true);
        this.actionTypeMap = new HashMap<>();
        
        // Initialize all actions to structured activity (default)
        for (ActivityData activity : activities) {
            actionTypeMap.put(activity.getName(), ActionType.STRUCTURED_ACTIVITY);
        }
        
        initializeUI(activities);
        setupDialog();
    }
    
    /**
     * Initializes the user interface components.
     */
    private void initializeUI(List<ActivityData> activities) {
        setLayout(new BorderLayout());
        
        // Create header panel
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel headerLabel = new JLabel("Choose the action type for each imported activity:");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);
        
        // Create table
        createTable(activities);
        
        // Create button panel (this now includes info panel)
        createButtonPanel();
    }
    
    /**
     * Creates the main table for action type selection.
     */
    private void createTable(List<ActivityData> activities) {
        // Define column names
        String[] columnNames = {"Action Name", "Input Pins", "Output Pins", "Action Type"};
        
        // Create table model
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Only the Action Type column is editable
                return column == 3;
            }
        };
        
        // Populate table with activity data
        for (ActivityData activity : activities) {
            String displayName = activity.getName();
            if (activity.isSubAction()) {
                displayName = "    └─ " + displayName; // Indent sub-actions
            }
            
            Object[] row = {
                displayName,
                String.join(", ", activity.getInputs()),
                String.join(", ", activity.getOutputs()),
                ActionType.STRUCTURED_ACTIVITY // Default selection
            };
            tableModel.addRow(row);
        }
        
        // Create table
        actionTable = new JTable(tableModel);
        actionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionTable.getTableHeader().setReorderingAllowed(false);
        
        // Set up custom cell renderer and editor for the Action Type column
        JComboBox<ActionType> comboBox = new JComboBox<>(ActionType.values());
        actionTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(comboBox));
        actionTable.getColumnModel().getColumn(3).setCellRenderer(new ComboBoxRenderer());
        
        // Set column widths
        actionTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Action Name
        actionTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Input Pins
        actionTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Output Pins
        actionTable.getColumnModel().getColumn(3).setPreferredWidth(200); // Action Type
        
        // Add table change listener to update our map
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 3) { // Action Type column changed
                int row = e.getFirstRow();
                String cell = (String) tableModel.getValueAt(row, 0);
                String rawName = cell.replaceAll("^\\s*└─\\s*", "");
                ActionType actionType = (ActionType) tableModel.getValueAt(row, 3);
                actionTypeMap.put(rawName, actionType);
            }
        });
        
        // Put table in scroll pane
        JScrollPane scrollPane = new JScrollPane(actionTable);
        scrollPane.setPreferredSize(new Dimension(700, 300));
        add(scrollPane, BorderLayout.CENTER);
    }
    
    /**
     * Creates the button panel at the bottom of the dialog.
     */
    private void createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // Bulk action buttons
        JButton setAllStructuredBtn = new JButton("Set All to Structured Activity");
        setAllStructuredBtn.addActionListener(e -> setAllActionTypes(ActionType.STRUCTURED_ACTIVITY));
        
        JButton setAllCallBehaviorBtn = new JButton("Set All to Call Behavior");
        setAllCallBehaviorBtn.addActionListener(e -> setAllActionTypes(ActionType.CALL_BEHAVIOR));
        
        // Dialog control buttons
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            userConfirmed = true;
            dispose();
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            userConfirmed = false;
            dispose();
        });
        
        // Add buttons to panel
        buttonPanel.add(setAllStructuredBtn);
        buttonPanel.add(setAllCallBehaviorBtn);
        buttonPanel.add(Box.createHorizontalStrut(20)); // Spacer
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        
        // Create a combined panel for info and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        // Create info panel first
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Action Type Information"));
        
        JTextArea infoText = new JTextArea();
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.PLAIN, 11f));
        infoText.setText(
            "• Structured Activity Node: Creates a nested activity that can contain other activities\n" +
            "• Call Behavior Action: Creates an action that calls an existing behavior/activity"
        );
        
        infoPanel.add(infoText, BorderLayout.CENTER);
        infoPanel.setPreferredSize(new Dimension(700, 80));
        
        bottomPanel.add(infoPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Sets all actions to the specified type.
     */
    private void setAllActionTypes(ActionType actionType) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(actionType, i, 3);
        }
    }
    
    /**
     * Custom renderer for the combo box column.
     */
    private static class ComboBoxRenderer implements TableCellRenderer {
        private JComboBox<ActionType> comboBox = new JComboBox<>(ActionType.values());
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            comboBox.setSelectedItem(value);
            
            if (isSelected) {
                comboBox.setBackground(table.getSelectionBackground());
                comboBox.setForeground(table.getSelectionForeground());
            } else {
                comboBox.setBackground(table.getBackground());
                comboBox.setForeground(table.getForeground());
            }
            
            return comboBox;
        }
    }
    
    /**
     * Sets up the dialog properties.
     */
    private void setupDialog() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(750, 500);
        setLocationRelativeTo(getParent());
        setResizable(true);
    }
    
    /**
     * Shows the dialog and waits for user input.
     * 
     * @return Map of action names to their selected types, or null if cancelled
     */
    public Map<String, ActionType> showDialog() {
        setVisible(true);
        
        if (userConfirmed) {
            return new HashMap<>(actionTypeMap);
        } else {
            return null;
        }
    }
    
    /**
     * Static convenience method to show the dialog and get results.
     * 
     * @param parent The parent frame
     * @param activities List of activities to choose types for
     * @return Map of action names to their selected types, or null if cancelled
     */
    public static Map<String, ActionType> chooseActionTypes(Frame parent, List<ActivityData> activities) {
        ActionTypeChooser chooser = new ActionTypeChooser(parent, activities);
        return chooser.showDialog();
    }
}