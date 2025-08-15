package com.example.configurationitemstereowarningplugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Timer;
import javax.swing.JOptionPane;

import com.nomagic.ci.persistence.versioning.IVersionDescriptor;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.teamwork2.ProjectVersion;
import com.nomagic.magicdraw.esi.EsiUtils;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;


public class SpecChangeLogger {
	
	// Logs a change in the specification field by adding an owned comment
	
	public static void logSpecChange(Project project, NamedElement element, String oldValue, 
            String newValue, String propertyType, AtomicBoolean confirming) {
		
		// Get Teamwork Cloud User (falls back to OS user)
		String twcUser = EsiUtils.getLoggedUserName();
		if (twcUser == null) twcUser = System.getProperty("user.name", "<unknown>");
		
		String timestamp = LocalDateTime.now()
				.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		
		String modelVersion = "Local/No Version";
		try {
			// 1> descriptor for the currently opened project
			ProjectDescriptor descriptor = ProjectDescriptorsFactory.getDescriptorForProject(project);
			if (descriptor != null) {
				long last = EsiUtils.getLastVersion(descriptor);
				if (last > 0) {
					modelVersion = String.valueOf(last);
				} else {
					// Fallback: enumerate versions and pick the max
					java.util.List<IVersionDescriptor> vds = EsiUtils.getVersions(descriptor);
					if (vds != null && !vds.isEmpty()) {
						long max = vds.stream().map(ProjectVersion::new).mapToLong(ProjectVersion::getLongNumber).max().orElse(0L);
						modelVersion = max > 0 ? String.valueOf(max) : "Local/No Version";
					}
				}
			}
		} catch (Exception ex) {
			modelVersion = "Unknown";
		}
		
		// Get justification from user
		@SuppressWarnings("deprecation")
		String justification = JOptionPane.showInputDialog(
			MDDialogParentProvider.getProvider().getDialogParent(),
			"Please provide justification for this " + propertyType + " change:",
			"Change Justification Required",
			JOptionPane.QUESTION_MESSAGE
		);
		
		// Build Comment Body
		String commentBody = String.format("[Property ConfigItemChange] User: %s\nTime Changed: %s\nModelVersion: %s\nSpec Type: %s\nOld Value: %s\nNew Value: %s\nJustification: %s",
				twcUser,
				timestamp,
				modelVersion,
				propertyType,
				oldValue,
				newValue,
				justification
				);
		
		
		// Use a timer to delay the logging by 500ms to avoid UI conflicts
	    Timer timer = new Timer(500, e -> {
	        SessionManager sm = SessionManager.getInstance();
	        try {
	            sm.createSession(project, "Log name change");

	            // Add owned comment for the change log
	            Comment comment = addOwnedComment(project, element, commentBody);
	            
	            setToDoField(comment, "Configuration Item " + propertyType + " changed");

	            sm.closeSession(project);

	            System.out.println("Name change logged for element: " + element.getName());

	        } catch (Exception ex) {
	            try { sm.cancelSession(project); } catch (Exception ignore) {}
	            System.err.println("Could not log name change: " + ex.getMessage());
	            ex.printStackTrace();
	        } finally {
	            // Reset the confirming flag AFTER the comment has been added
	            if (confirming != null) {
	                confirming.set(false);
	            }
	        }
	    });

	    timer.setRepeats(false); // Only execute once
	    timer.start();
	}
	
    /** Creates a Comment owned by the element and annotating it. Returns the created Comment. */
    private static Comment addOwnedComment(Project project, NamedElement element, String commentBody) {
        Comment comment = project.getElementsFactory().createCommentInstance();
        comment.setBody(commentBody);

        // Own the comment under the renamed element
        element.getOwnedElement().add(comment);
        // Also annotate the element
        comment.getAnnotatedElement().add(element);

        System.out.println("Owned comment added: " + commentBody);
        return comment;
    }
    
    /** Set/append "To Do" on the given element (now used with Comment). */
    private static void setToDoField(Element target, String value) {
        if (target == null || value == null || value.isEmpty()) return;

        Project project = Project.getProject(target);
        SessionManager sm = SessionManager.getInstance();
        boolean own = false;

        try {
            if (!sm.isSessionCreated(project)) {
                sm.createSession(project, "Set To Do");
                own = true;
            }

            Stereotype todo = findTodoOwner(project);
            if (todo == null) {
                System.err.println("Todo_Owner stereotype not found in project.");
                if (own) sm.closeSession(project);
                return;
            }

            // Apply stereotype to the COMMENT (or whatever element is passed)
            if (!StereotypesHelper.hasStereotypeOrDerived(target, todo)) {
                StereotypesHelper.addStereotype(target, todo);
            }

            // Append the value to the TODO tagged value
            StereotypesHelper.setStereotypePropertyValue(target, todo, "TODO", value, true);

            if (own) sm.closeSession(project);
        } catch (Exception ex) {
            try { if (own) sm.cancelSession(project); } catch (Exception ignore) {}
            System.err.println("Failed to set To Do: " + ex.getMessage());
        }
    }
    
    /** Locate the MagicDraw Profile's Todo_Owner stereotype robustly. */
    @SuppressWarnings("deprecation")
	private static Stereotype findTodoOwner(Project project) {
        Stereotype st = StereotypesHelper.getStereotype(project, "Todo_Owner");
        if (st != null) return st;

        st = StereotypesHelper.getStereotype(project, "TODO_Owner");
        if (st != null) return st;

        Profile mdProfile = StereotypesHelper.getProfile(project, "MagicDraw Profile");
        if (mdProfile != null) {
            st = StereotypesHelper.getStereotype(project, "Todo_Owner", mdProfile);
            if (st == null) st = StereotypesHelper.getStereotype(project, "TODO_Owner", mdProfile);
        }
        return st;
    }
}