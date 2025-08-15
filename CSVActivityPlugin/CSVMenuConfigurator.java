package com.example.csvactivityplugin;

import com.nomagic.actions.AMConfigurator;
import com.nomagic.actions.ActionsCategory;
import com.nomagic.actions.ActionsManager;

/**
 * Configurator class that adds our CSV import action to Cameo's menu system.
 * This implements AMConfigurator which is Cameo's interface for adding menu items.
 */
public class CSVMenuConfigurator implements AMConfigurator {
    
    /**
     * This method is called by Cameo to let us add our actions to the menu.
     * 
     * @param manager The ActionsManager that handles all menu actions in Cameo
     */
    @Override
    public void configure(ActionsManager manager) {
        // Get the "Tools" menu category - this is where we'll add our action
        ActionsCategory toolsCategory = (ActionsCategory) manager.getActionFor("TOOLS");
        
        // If Tools menu doesn't exist for some reason, create it
        if (toolsCategory == null) {
            toolsCategory = new ActionsCategory("TOOLS", "Tools");
            manager.addCategory(toolsCategory);
        }
        
        // Create our import action and add it to the Tools menu
        CSVImportAction importAction = new CSVImportAction();
        toolsCategory.addAction(importAction);
        
        System.out.println("CSV Import action added to Tools menu");
    }
    
    /**
     * Returns the priority of this configurator.
     * NORMAL_PRIORITY means it will be loaded in the standard order.
     */
    @Override
    public int getPriority() {
        return AMConfigurator.MEDIUM_PRIORITY;
    }
}