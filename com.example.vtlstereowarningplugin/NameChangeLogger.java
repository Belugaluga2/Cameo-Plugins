package com.example.vtlstereowarningplugin;

import com.nomagic.ci.persistence.versioning.IVersionDescriptor;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.teamwork2.ProjectVersion;
import com.nomagic.magicdraw.esi.EsiUtils;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element; // <-- NEW
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;

import javax.swing.Timer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles logging of name changes for elements with VelocityUse stereotype
 */
public class NameChangeLogger {

    public static void logNameChange(Project project, NamedElement element,
                                     String oldName, String newName, String affectedReports) {

        String twcUser = EsiUtils.getLoggedUserName();
        if (twcUser == null) twcUser = System.getProperty("user.name", "<unknown>");

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        String modelVersion = "Local/No Version";
        try {
            ProjectDescriptor descriptor = ProjectDescriptorsFactory.getDescriptorForProject(project);
            if (descriptor != null) {
                long last = EsiUtils.getLastVersion(descriptor);
                if (last > 0) {
                    modelVersion = String.valueOf(last);
                } else {
                    java.util.List<IVersionDescriptor> vds = EsiUtils.getVersions(descriptor);
                    if (vds != null && !vds.isEmpty()) {
                        long max = vds.stream().map(ProjectVersion::new).mapToLong(ProjectVersion::getLongNumber).max().orElse(0L);
                        modelVersion = max > 0 ? String.valueOf(max) : "Local/No Version";
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Could not get model version: " + ex.getMessage());
            modelVersion = "Unknown";
        }

        String commentBody = String.format(
            "[Property NameChange] User: %s%nTime Changed: %s%nModel Version: %s%nOld Name: %s%nNew Name: %s",
            twcUser, timestamp, modelVersion, oldName, newName
        );

        Timer timer = new Timer(500, e -> {
            SessionManager sm = SessionManager.getInstance();
            boolean own = false;
            try {
                if (!sm.isSessionCreated(project)) {
                    sm.createSession(project, "Log name change");
                    own = true;
                }

                // 1) Add owned comment and get a handle to it
                Comment comment = addOwnedComment(project, element, commentBody);

                // 2) Put the "To Do" on the COMMENT (so it appears in the comment's Specification)
                setToDoField(comment, "Update name in following reports: " + affectedReports);

                if (own) sm.closeSession(project);
                System.out.println("Name change logged for element: " + element.getName());

            } catch (Exception ex) {
                try { if (own) sm.cancelSession(project); } catch (Exception ignore) {}
                System.err.println("Could not log name change: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
        timer.setRepeats(false);
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