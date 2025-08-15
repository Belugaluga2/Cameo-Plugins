package com.example.csvactivityplugin;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.ui.dialogs.SelectElementInfo;
import com.nomagic.magicdraw.ui.dialogs.SelectElementTypes;
import com.nomagic.magicdraw.ui.dialogs.selection.ElementSelectionDlg;
import com.nomagic.magicdraw.ui.dialogs.selection.ElementSelectionDlgFactory;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import java.awt.Frame;
import java.util.Arrays;
import java.util.List;

/**
 * Shows a modal tree browser so the user can pick a Package **or** Activity
 * that will own the imported Activity (and, in turn, the diagram).
 */
public class DiagramParentChooser {

    /**
     * Displays the chooser and returns the user's selection.
     *
     * @param project the current MagicDraw/Cameo project
     * @return the selected element, or {@code null} if the dialog was canceled
     */
    public static Element chooseParent(Project project) {
    	Frame parentFrame = MDDialogParentProvider.getProvider().getDialogParent();

        // 1. Create the empty dialog
        ElementSelectionDlg dlg = ElementSelectionDlgFactory.create(
                parentFrame,
                "Select Diagram Parent",
                null); // no help page

     // 2. Allowed metaclasses (both selectable *and* visible)
        List<Class<?>> allowed = Arrays.asList(
            Package.class, 
            Activity.class,
            com.nomagic.uml2.ext.magicdraw.auxiliaryconstructs.mdmodels.Model.class
        );
        SelectElementTypes types = new SelectElementTypes(allowed, allowed); // display + select

        // 3. Basic UI options – rooted at the primary model, no diagrams, no "None" entry
        SelectElementInfo info = new SelectElementInfo(
                false,                       // showNone
                false,                       // showDiagrams
                project.getPrimaryModel(),   // browser root
                false                        // sortable
        );

        // 4. Configure for single-selection mode
        ElementSelectionDlgFactory.initSingle(dlg, types, info, null /* no initial selection */);

        // 5. Block until the user closes the dialog
        dlg.show();

        Object sel = dlg.getSelectedElement();
        return (sel instanceof Element) ? (Element) sel : null;
    }
}
