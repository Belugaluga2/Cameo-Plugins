package com.example.configurationitemstereowarningplugin;

import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.magicdraw.commands.CommandHistory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;

public class ConfigurationItemStereoWarningManager implements PropertyChangeListener {
	
	private static final String CONFIGURATION_USE_STEREOTYPE = "ConfigurationItem";
	
	private final Map<Project, Boolean> monitoredProjects;
	private final Set<Element> reverting = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Set<Element> showingDialog = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Map<String, ElementInfo> elementInfoMap = new ConcurrentHashMap<>();
	
	// booleans to protect from infinite loops
	 // NEW: guard to suppress re-entrant events during undo
    private final AtomicBoolean undoing = new AtomicBoolean(false);
    private final AtomicBoolean confirming = new AtomicBoolean(false);
	
	// change oldValue/newValue to Object
	private static class ElementInfo {
	    final String elementID;
	    final Object oldValue;
	    final Object newValue;
	    final Project project;
	    final String elementName;

	    ElementInfo(String elementID, String elementName, Object oldValue, Object newValue, String propertyType, Project project) {
	        this.elementID = elementID;
	        this.elementName = elementName;
	        this.oldValue = oldValue;
	        this.newValue = newValue;
	        this.project = project;
	    }
	}
	
	public ConfigurationItemStereoWarningManager() {
		this.monitoredProjects = new HashMap<>();
	}
	
	public void attachToProject(Project project) {
		if (project == null || monitoredProjects.containsKey(project))
			return;
		
		Element rootElement = project.getPrimaryModel();
		if (rootElement != null)
			addListenerToElementTree(rootElement);
		
		monitoredProjects.put(project, true);
		System.out.println("Configuration Item Warning attached to project: " + project.getName());
	}

	public void detachFromProject(Project project) {
		if (project == null || !monitoredProjects.containsKey(project))
			return;
		
		Element rootElement = project.getPrimaryModel();
		if (rootElement != null)
			removeListenerFromElementTree(rootElement);
		
		monitoredProjects.remove(project);
		System.out.println("Configuration Item Warning detached from project: " + project.getName());
	}
	
	private void addListenerToElementTree(Element element) {
		// Add listener to all elements, not just NamedElements
		element.addPropertyChangeListener(this);
		
		// Also listen to owned comments (specifications are often stored as comments)
		for (Comment comment : element.getOwnedComment()) {
			comment.addPropertyChangeListener(this);
		}
		
		// Recursively add to children
		for (Element child : element.getOwnedElement()) {
			addListenerToElementTree(child);
		}
	}
	
	private void removeListenerFromElementTree(Element element) {
		element.removePropertyChangeListener(this);
		
		// Remove from comments
		for (Comment comment : element.getOwnedComment()) {
			comment.removePropertyChangeListener(this);
		}
		
		for (Element child : element.getOwnedElement()) {
			removeListenerFromElementTree(child);
		}
	}

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object src = evt.getSource();
        if (!(src instanceof Element)) return;

        // Ignore changes triggered by our own undo
        if (undoing.get() || confirming.get()) return;

        Element changed = (Element) src;
        if (reverting.contains(changed)) return;

        Element target = resolveTargetElement(changed);
        if (!hasConfigUseStereotype(target)) return;

        if (showingDialog.contains(changed)) return;

        String propName = evt.getPropertyName();
        Object oldObj   = evt.getOldValue();
        Object newObj   = evt.getNewValue();

        Project project  = Project.getProject(changed);
        String elementId = changed.getID();
        if (project != null && elementId != null) {
            String elementName = (target instanceof NamedElement)
                ? ((NamedElement) target).getName()
                : target.getHumanType();
            elementInfoMap.put(elementId, new ElementInfo(
                elementId, elementName, oldObj, newObj, propName, project
            ));
        }

        showingDialog.add(changed);
        showWarningDialog(changed, target, shorten(oldObj), shorten(newObj), propName);
    }
	
	private boolean hasConfigUseStereotype(Element element) {
		for (Stereotype stereotype : StereotypesHelper.getStereotypes(element)) {
			if (CONFIGURATION_USE_STEREOTYPE.equals(stereotype.getName())) return true;
		}
		return false;
	}
	
	 // CHANGED: cancel path now performs a universal undo
    @SuppressWarnings("deprecation")
	private void showWarningDialog(Element element, Element targetElement,
                                   String oldValue, String newValue, String propertyType) {
        SwingUtilities.invokeLater(() -> {
            try {
                String elementName = (targetElement instanceof NamedElement)
                    ? ((NamedElement) targetElement).getName()
                    : targetElement.getHumanType();

                String message = String.format(
                    "WARNING: This element is under Configuration Control!\n" +
                    "Are you sure you want to change its specification?\n\n" +
                    "Element: %s\nProperty: %s\nOld Value: %s\nNew Value: %s\n\n" +
                    "Click OK to confirm or Cancel to undo the operation",
                    elementName != null ? elementName : "<unnamed>",
                    propertyType != null ? propertyType : "<no type>",
                    oldValue != null ? (oldValue.length() > 50 ? oldValue.substring(0,50) + "..." : oldValue) : "<empty>",
                    newValue != null ? (newValue.length() > 50 ? newValue.substring(0,50) + "..." : newValue) : "<empty>"
                );

                int result = JOptionPane.showOptionDialog(
                    MDDialogParentProvider.getProvider().getDialogParent(),
                    message,
                    "Configuration Control - Specification Change",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[]{"OK", "Cancel"},
                    "Cancel"
                );

                if (result == JOptionPane.OK_OPTION) {
                    confirming.set(true); // NEW: suppress property change triggered by OK confirmation

                    try {
                        // Pass the confirming flag to SpecChangeLogger
                        SpecChangeLogger.logSpecChange(Project.getProject(element),
                            (NamedElement) targetElement, oldValue, newValue, propertyType, confirming);
                        elementInfoMap.remove(element.getID());
                        // Don't reset confirming here - let SpecChangeLogger do it after the comment is added
                    } catch (Exception e) {
                        confirming.set(false); // Reset on error
                        throw e;
                    }
                } else { // Cancel or window closed
                    undoLastEdit(Project.getProject(element));
                    elementInfoMap.remove(element.getID()); // clean up
                }
            } finally {
                showingDialog.remove(element);
            }
        });
    }
    
 // NEW: universal undo
    @SuppressWarnings({ "deprecation"})
	private void undoLastEdit(Project project) {
        if (project == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                undoing.set(true); // suppress our propertyChange during undo

                // Ensure no open session blocks undo; cancel if needed
                if (SessionManager.getInstance().isSessionCreated(project)) {
                    try { SessionManager.getInstance().cancelSession(project); } catch (Exception ignore) {}
                }

                CommandHistory ch = project.getCommandHistory();
                if (ch != null /* and optionally: ch.canUndo() */) {
                    ch.undo(); // universally revert the last recorded command
                }
            } catch (Throwable t) {
                // no GUI error here per your requirement; log only
                System.err.println("Undo failed: " + t.getMessage());
            } finally {
                undoing.set(false);
            }
        });
    }
	
	
	private Element resolveTargetElement(Element e) {
	    if (e instanceof Comment) {
	        Comment c = (Comment) e;
	        if (!c.getAnnotatedElement().isEmpty()) {
	            return c.getAnnotatedElement().iterator().next();
	        }
	    }
	    return e;
	}

	private String shorten(Object v) {
	    if (v == null) return "<empty>";
	    String s = v.toString();
	    return s.length() > 200 ? s.substring(0, 200) + "..." : s; // keep it readable
	}
	
	
}