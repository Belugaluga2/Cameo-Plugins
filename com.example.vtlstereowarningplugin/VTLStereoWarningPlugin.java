package com.example.vtlstereowarningplugin;

import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectEventListenerAdapter;

/**
 * Main plugin class for VTL Stereo Warning functionality
 */
public class VTLStereoWarningPlugin extends Plugin {

    // Unique identifier for this plugin - should match the ID in plugin.xml
    public static final String PLUGIN_ID = "com.nomagic.vtlstereowarningplugin";
    
    private VTLStereoWarningManager warningManager;
    private ProjectEventListenerAdapter projectListener;

    /**
     * Called by Cameo when the plugin is loaded.
     * This is where we register our menu items and actions.
     */
    @Override
    public void init() {
        System.out.println("VTL Stereo Warning Plugin initializing...");
        
        
        // Initialize the warning manager
        warningManager = new VTLStereoWarningManager();
        
        // Create project event listener to handle project open/close
        projectListener = new ProjectEventListenerAdapter() {
            @Override
            public void projectOpened(Project project) {
                warningManager.attachToProject(project);
            }
            
            @Override
            public void projectClosed(Project project) {
                warningManager.detachFromProject(project);
            }
        };
        
        // Register the project listener
        Application.getInstance()
        .getProjectsManager()
        .addProjectListener(projectListener);
        
        // If a project is already open, attach to it
        Project activeProject = Application.getInstance().getProject();
        if (activeProject != null) {
            warningManager.attachToProject(activeProject);
        }
        
        System.out.println("VTL Stereo Warning Plugin initialized successfully");
    }

    /**
     * Called by Cameo when the plugin is being unloaded.
     * Return true to allow the plugin to be closed.
     */
    @Override
    public boolean close() {
        System.out.println("VTL Stereo Warning Plugin closing...");
        
        // Clean up resources
        if (projectListener != null) {
        	Application.getInstance().getProjectsManager().removeProjectListener(projectListener);
        }
        
        // Detach from current project if any
        Project activeProject = Application.getInstance().getProject();
        if (activeProject != null && warningManager != null) {
            warningManager.detachFromProject(activeProject);
        }
        
        warningManager = null;
        projectListener = null;
        
        return true;
    }

    /**
     * Indicates whether this plugin is supported in the current environment.
     * We return true because we support all Cameo environments.
     */
    @Override
    public boolean isSupported() {
        return true;
    }
}