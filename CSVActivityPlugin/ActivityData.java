package com.example.csvactivityplugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model class that represents a single activity from the CSV file.
 * This is a simple POJO (Plain Old Java Object) that holds the data
 * for one row in the CSV. 
 */
public class ActivityData {
    
    // The name of the activity (column 1 in CSV)
    private String name;
    
    // Documentation/description for the activity (column 2 in CSV)
    private String documentation;
    
    // The actor performing this activity
    private String actor;
    
    // List of input pins for this activity, comma or semicolon separated
    private List<String> inputs;
    
    // List of output pins for this activity (column 3 in CSV, semicolon-or-comma-separated)
    private List<String> outputs;
    
    /**
     * Default constructor - initializes with empty values
     */
    public ActivityData() {
        this.name = "";
        this.documentation = "";
        this.actor = "";
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }
    
    /**
     * Constructor with all fields
     */
    public ActivityData(String name, String documentation, List<String> inputs, List<String> outputs, String actor) {
        this.name = name;
        this.documentation = documentation;
        this.inputs = inputs;
        this.outputs = outputs;
        this.actor = "";
    }
    
    // Getters and setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name != null ? name : "";
    }
    
    public String getDocumentation() {
        return documentation;
    }
    
    public void setDocumentation(String documentation) {
        this.documentation = documentation != null ? documentation : "";
    }
    
    public List<String> getInputs() {
        return inputs;
    }
    
    public void setInputs(List<String> inputs) {
        this.inputs = inputs != null ? inputs : new ArrayList<>();
    }
    
    public List<String> getOutputs() {
        return outputs;
    }
    
    public void setOutputs(List<String> outputs) {
        this.outputs = outputs != null ? outputs : new ArrayList<>();
    }
    
    /**
     * Adds a single input to the inputs list
     */
    public void addInput(String input) {
        if (input != null && !input.trim().isEmpty()) {
            this.inputs.add(input.trim());
        }
    }
    
    /**
     * Adds a single output to the outputs list
     */
    public void addOutput(String output) {
        if (output != null && !output.trim().isEmpty()) {
            this.outputs.add(output.trim());
        }
    }
    
    public void setActor(String actor) {
    	this.actor = actor != null ? actor : "";
    }
    
    public String getActor() {
    	return actor;
    }
    
    private boolean subAction = false;   // true ⇢ row is a “1.1” style sub‑action
    private String  parentName;          // name of the main action it belongs under

    public boolean isSubAction()  { return subAction; }
    public void setSubAction(boolean sub) { this.subAction = sub; }

    public String  getParentName() { return parentName; }
    public void    setParentName(String p) { this.parentName = p; }
    
    /**
     * Returns a string representation for debugging
     */
    @Override
    public String toString() {
        return "ActivityData{" +
               "name='" + name + '\'' +
               ", documentation='" + documentation + '\'' +
               ", inputs=" + inputs +
               ", outputs=" + outputs +
               '}';
    }
}