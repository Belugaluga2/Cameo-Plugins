package com.example.configurationitemstereowarningplugin;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectEventListenerAdapter;
import com.nomagic.magicdraw.plugins.Plugin;

public class ConfigurationItemStereoWarningPlugin extends Plugin {
	
	public static final String PLUGIN_ID = "com.nomagic.configurationstereowarningplugin";
	private ConfigurationItemStereoWarningManager warningManager;
	private ProjectEventListenerAdapter projectListener;

	public void init() {

	    warningManager = new ConfigurationItemStereoWarningManager();

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
	    Application.getInstance().getProjectsManager().addProjectListener(projectListener);
	    
	    // If a project is already open attach to it
	    Project activeProject = Application.getInstance().getProject();
	    if (activeProject != null) {
	    	warningManager.attachToProject(activeProject);
	    }
	}

	@Override
	public boolean close() {
	    if (projectListener != null) {
	        Application.getInstance()
	            .getProjectsManager()
	            .removeProjectListener(projectListener);
	    }
	    Project activeProject = Application.getInstance().getProject();
	    if (activeProject != null && warningManager != null) {
	        warningManager.detachFromProject(activeProject);
	    }
	    warningManager = null;
	    projectListener = null;
	    return true;
	}

	@Override
	public boolean isSupported() {
		// TODO Auto-generated method stub
		return true;
	}

}
