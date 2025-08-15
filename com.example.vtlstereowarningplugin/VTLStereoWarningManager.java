package com.example.vtlstereowarningplugin;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.uml.BaseElement;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VTLStereoWarningManager implements PropertyChangeListener {

    private static final String VELOCITY_USE_STEREOTYPE = "VelocityUse";
    private static final String NAME_PROPERTY = "name";

    private final Map<Project, Boolean> monitoredProjects;
    /** prevent infinite recursion when we programmatically revert the name */
    private final Set<NamedElement> reverting = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** Track elements currently showing dialog to prevent multiple dialogs */
    private final Set<NamedElement> showingDialog = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** Store element IDs to track them even after name changes */
    private final Map<String, ElementInfo> elementInfoMap = new ConcurrentHashMap<>();

    /** Helper class to store element info for reverting */
    private static class ElementInfo {
        final String elementId;
        final String oldName;
        final String newName;
        final Project project;
        
        ElementInfo(String elementId, String oldName, String newName, Project project) {
            this.elementId = elementId;
            this.oldName = oldName;
            this.newName = newName;
            this.project = project;
        }
    }
    

    public VTLStereoWarningManager() {
        this.monitoredProjects = new HashMap<>();
    }

    public void attachToProject(Project project) {
        if (project == null || monitoredProjects.containsKey(project)) {
            return;
        }

        Element rootElement = project.getPrimaryModel();
        if (rootElement != null) {
            addListenerToElementTree(rootElement);
        }

        monitoredProjects.put(project, true);
        System.out.println("VTL Stereo Warning attached to project: " + project.getName());
    }

    public void detachFromProject(Project project) {
        if (project == null || !monitoredProjects.containsKey(project)) {
            return;
        }

        Element rootElement = project.getPrimaryModel();
        if (rootElement != null) {
            removeListenerFromElementTree(rootElement);
        }

        monitoredProjects.remove(project);
        System.out.println("VTL Stereo Warning detached from project: " + project.getName());
    }

    private void addListenerToElementTree(Element element) {
        if (element instanceof NamedElement) {
            ((NamedElement) element).addPropertyChangeListener(this);
        }
        for (Element child : element.getOwnedElement()) {
            addListenerToElementTree(child);
        }
    }

    private void removeListenerFromElementTree(Element element) {
        if (element instanceof NamedElement) {
            ((NamedElement) element).removePropertyChangeListener(this);
        }
        for (Element child : element.getOwnedElement()) {
            removeListenerFromElementTree(child);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!NAME_PROPERTY.equals(evt.getPropertyName())) return;

        Object src = evt.getSource();
        if (!(src instanceof NamedElement)) return;

        NamedElement elt = (NamedElement) src;

        // If we are the ones changing the name back, ignore this event
        if (reverting.contains(elt)) return;

        // If we're already showing a dialog for this element, ignore
        if (showingDialog.contains(elt)) return;

        if (!hasVelocityUseStereotype(elt)) return;

        String oldName = (String) evt.getOldValue();
        String newName = (String) evt.getNewValue();

        // Store element info for potential revert
        Project project = Project.getProject(elt);
        if (project != null) {
            String elementId = elt.getID();
            elementInfoMap.put(elementId, new ElementInfo(elementId, oldName, newName, project));
        }

        // Mark that we're showing dialog for this element
        showingDialog.add(elt);
        
        showWarningDialog(elt, oldName, newName);
    }

    private boolean hasVelocityUseStereotype(Element element) {
        for (Stereotype stereotype : StereotypesHelper.getStereotypes(element)) {
            if (VELOCITY_USE_STEREOTYPE.equals(stereotype.getName())) {
                return true;
            }
        }
        return false;
    }

    public static String getAffectedReports(Element element) {
        Project project = Project.getProject(element);

        @SuppressWarnings("deprecation")
		Stereotype velocityUse = StereotypesHelper.getStereotype(project, "VelocityUse");
        if (velocityUse == null || !StereotypesHelper.hasStereotype(element, velocityUse)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Object> raw = StereotypesHelper
            .getStereotypePropertyValue(element, velocityUse, "AffectedReports");

        if (raw == null || raw.isEmpty()) {
            return null;
        }

        return raw.stream()
                  .map(Object::toString)
                  .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("deprecation")
	private void showWarningDialog(NamedElement element, String oldName, String newName) {
        SwingUtilities.invokeLater(() -> {
            try {
                String affectedReports = getAffectedReports(element);
                String message = String.format(
                    "WARNING: You are renaming an element with VelocityUse stereotype!\n\n" +
                    "Element Type: %s\n" +
                    "Old Name: %s\n" +
                    "New Name: %s\n\n" +
                    "Referenced in these Velocity Templates:\n%s\n\n" +
                    "Renaming this element will require updating its name in the above templates. Are you sure you want to continue?" + "\n" +
                    "Click OK to confirm or Cancel to undo the rename.",
                    element.getHumanType(),
                    oldName != null ? oldName : "<unnamed>",
                    newName != null ? newName : "<unnamed>",
                    affectedReports != null ? affectedReports : "None specified"
                );

                int result = JOptionPane.showOptionDialog(
                    MDDialogParentProvider.getProvider().getDialogParent(),
                    message,
                    "VTL Stereo Warning – Name Change Detected",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[]{"OK", "Cancel"},
                    "Cancel"  // Default to Cancel for safety
                );

                // Debug: Log the result
                System.out.println("Dialog result: " + result + " (OK=0, Cancel=1, CLOSED=" + JOptionPane.CLOSED_OPTION + ")");
                
                if (result == JOptionPane.OK_OPTION) {
                    // Use the new NameChangeLogger class
                    String affected = getAffectedReports(element);
                    NameChangeLogger.logNameChange(Project.getProject(element), element,
                                                  oldName, newName, affected);
                }

                // Check if user clicked Cancel button (index 1) or closed the dialog (CLOSED_OPTION)
                if (result == 1 || result == JOptionPane.CLOSED_OPTION) {
                    // Use the element ID to find and revert
                    String elementId = element.getID();
                    ElementInfo info = elementInfoMap.get(elementId);
                    if (info != null) {
                        revertNameChange(info);
                        elementInfoMap.remove(elementId);
                    }
                } else {
                    // User confirmed, remove the stored info
                    elementInfoMap.remove(element.getID());
                }
            } finally {
                // Always remove from showingDialog set
                showingDialog.remove(element);
            }
        });
    }
    /** Revert the name by finding the element and renaming it */
    @SuppressWarnings("deprecation")
	private void revertNameChange(ElementInfo info) {
        if (info.oldName == null || info.project == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                // Find the element by ID first (most reliable)
                BaseElement element = info.project.getElementByID(info.elementId);
                
                // If not found by ID, search by the new name
                if (element == null) {
                    element = findElementByName(info.project.getPrimaryModel(), info.newName);
                }
                
                if (element instanceof NamedElement) {
                    NamedElement namedElement = (NamedElement) element;
                    
                    // Mark as reverting to prevent recursion
                    reverting.add(namedElement);
                    
                    // Create session for the revert operation
                    if (!SessionManager.getInstance().isSessionCreated(info.project)) {
                        SessionManager.getInstance().createSession(info.project, "Revert VelocityUse Rename");
                    }
                    
                    try {
                        // Use setName() to change the name
                        namedElement.setName(info.oldName);
                        
                        // Commit the session
                        SessionManager.getInstance().closeSession(info.project);
                        
                        System.out.println("Successfully reverted name from '" + info.newName + "' to '" + info.oldName + "'");
                        
                    } catch (Exception e) {
                        // If something goes wrong, cancel the session
                        try {
                            SessionManager.getInstance().cancelSession(info.project);
                        } catch (Exception ignore) {}
                        
                        System.err.println("Failed to revert name: " + e.getMessage());
                        e.printStackTrace();
                        
                        // Show error to user
                        JOptionPane.showMessageDialog(
                            MDDialogParentProvider.getProvider().getDialogParent(),
                            "Failed to revert the name change. Please manually change it back to: " + info.oldName,
                            "Revert Failed",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                    
                    // Schedule removal from reverting set
                    SwingUtilities.invokeLater(() -> reverting.remove(namedElement));
                    
                } else {
                    System.err.println("Element not found or not a NamedElement");
                }
                
            } catch (Exception ex) {
                System.err.println("Error during revert: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }
    
    /** Helper method to find an element by name in the model tree */
    private Element findElementByName(Element root, String name) {
        if (root instanceof NamedElement) {
            NamedElement named = (NamedElement) root;
            if (name.equals(named.getName())) {
                return root;
            }
        }
        
        // Search children
        for (Element child : root.getOwnedElement()) {
            Element found = findElementByName(child, name);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    
}