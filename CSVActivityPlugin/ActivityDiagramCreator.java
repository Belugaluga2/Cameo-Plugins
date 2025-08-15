package com.example.csvactivityplugin;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.*;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.*;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.*;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.*;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.impl.ElementsFactory;

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.*;

/**
 * CSV‑to‑Activity importer that creates a main Activity diagram
 * plus one diagram for every StructuredActivityNode that has sub‑actions.
 */
public class ActivityDiagramCreator {

    /* ------------- layout constants ------------ */
    private static final int DIAGRAM_WIDTH = 1200;
    private static final int START_Y       = 100;
    private static final int LANE_WIDTH    = 480;
    private static final int ROW_HEIGHT    = 225;
    private static final int Y_STEP        = 60;

    /* =============================================================
                             PUBLIC ENTRY
       ============================================================= */
    @SuppressWarnings("deprecation")
	public void createActivityDiagram(Project project, List<ActivityData> rows)
            throws Exception {

        /* choose action‑type mappings */
        Frame frame = MDDialogParentProvider.getProvider().getDialogParent();
        Map<String,ActionTypeChooser.ActionType> actionTypes =
                ActionTypeChooser.chooseActionTypes(frame, rows);
        if (actionTypes == null) throw new Exception("Cancelled.");

        /* run inside a single MagicDraw session */
        SessionManager sm = SessionManager.getInstance();
        sm.createSession(project, "Import CSV as Activity Diagram");

        try {
            Element parentPkg = DiagramParentChooser.chooseParent(project);
            if (parentPkg == null) throw new Exception("No parent chosen.");

            /* -------- main model root -------- */
            Activity rootActivity = createActivityElement(project);
            if (rootActivity.getOwner() != parentPkg)
                ModelElementsManager.getInstance().moveElement(rootActivity, parentPkg);

            /* -------- partitions (lanes) for the main diagram -------- */
            Map<String,ActivityPartition> partitions =
                    createActivityPartitions(project, rootActivity, rows);

            /* -------- main activity diagram -------- */
            DiagramPresentationElement mainDpe =
                    createAndOpenDiagram(project, rootActivity);

            int centerX = (DIAGRAM_WIDTH - LANE_WIDTH * partitions.size()) / 2;

            /* one diagram ⇒ its own laneShapes map */
            Map<ActivityPartition,ShapeElement> laneShapes = new HashMap<>();

            buildAndPlaceSwimlanes(mainDpe, partitions,
                                   countActionsPerActor(rootActivity),
                                   centerX, START_Y - 50,
                                   laneShapes);

            /* -------- model nodes & edges -------- */
            Map<String,StructuredActivityNode> mainActionMap =
                    createActivityNodes(project, rootActivity, rows,
                                        actionTypes, partitions);

            populateDiagramNodes(rootActivity, mainDpe, laneShapes);

            DiagramGridLayouter.layout(rootActivity, mainDpe,
                                       partitions, START_Y, Y_STEP);

            populateDiagramPaths(rootActivity, mainDpe);

            /* -------- sub‑action diagrams -------- */
            createSubactionDiagrams(project, rows,
                                    mainActionMap, actionTypes);

            sm.closeSession(project);
        } catch (Exception ex) {
            sm.cancelSession(project);
            throw ex;
        }
    }

    /* =============================================================
                         MAIN‑DIAGRAM HELPERS
       ============================================================= */

    private Activity createActivityElement(Project project)
            throws ReadOnlyElementException {

        Activity act = project.getElementsFactory().createActivityInstance();
        act.setName("Imported Activities");
        ModelElementsManager.getInstance().addElement(act, project.getPrimaryModel());
        return act;
    }

    /** top‑level partitions: only actors from non‑sub rows */
    private Map<String,ActivityPartition> createActivityPartitions(Project project,
                                                                   Activity activity,
                                                                   List<ActivityData> rows)
            throws ReadOnlyElementException {

        ElementsFactory f   = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        Map<String,ActivityPartition> parts = new LinkedHashMap<>();

        Set<String> actors = new LinkedHashSet<>();
        for (ActivityData d : rows)
            if (!d.isSubAction()) actors.add(actorName(d.getActor()));

        /* optional SysML «allocateActivityPartition» */
        Profile sysml  = StereotypesHelper.getProfile(project, "SysML");
        Stereotype stereo = (sysml == null)
                          ? null
                          : StereotypesHelper.getStereotype(project,
                                                            "AllocateActivityPartition", sysml);

        for (String actor : actors) {
            ActivityPartition p = f.createActivityPartitionInstance();
            p.setName(actor);  p.setDimension(true);
            mgr.addElement(p, activity);   activity.getPartition().add(p);
            if (stereo != null) StereotypesHelper.addStereotype(p, stereo);
            parts.put(actor, p);
        }
        return parts;
    }

    private static String actorName(String raw) {
        return (raw == null || raw.trim().isEmpty()) ? "<Unassigned>" : raw.trim();
    }

    /* -------- per‑diagram counting helper -------- */
    private Map<String,Integer> countActionsPerActor(Element ctx) {
        Map<String,Integer> map = new HashMap<>();
        for (ActivityNode n : getNodesOfContext(ctx)) {
            if (!n.getInPartition().isEmpty()) {
                String actor = n.getInPartition().iterator().next().getName();
                map.merge(actor, 1, Integer::sum);
            }
        }
        return map;
    }

    /* -------- model node creation (returns map main action name → SAN) */
    private Map<String,StructuredActivityNode> createActivityNodes(Project project,
                                         Activity activity,
                                         List<ActivityData> rows,
                                         Map<String,ActionTypeChooser.ActionType> actionTypes,
                                         Map<String,ActivityPartition> partitions)
            throws ReadOnlyElementException {

        ElementsFactory f   = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();

        InitialNode start = f.createInitialNodeInstance();
        start.setName("Start");
        mgr.addElement(start, activity);
        partitions.values().iterator().next().getNode().add(start);
        ActivityNode prev = start;

        Map<String,StructuredActivityNode> mainMap = new HashMap<>();

        for (ActivityData d : rows) {
            ActionTypeChooser.ActionType t =
                    actionTypes.getOrDefault(d.getName(),
                                             ActionTypeChooser.ActionType.STRUCTURED_ACTIVITY);
            String actor = actorName(d.getActor());
            ActivityPartition lane = partitions.get(actor);

            if (d.isSubAction()) {
                StructuredActivityNode parent = mainMap.get(d.getParentName());
                if (parent != null) createSubAction(project, parent, d, t);
            } else {
                StructuredActivityNode main =
                        createMainAction(project, activity, d, t);
                if (lane != null) lane.getNode().add(main);
                mainMap.put(d.getName(), main);

                ControlFlow cf = f.createControlFlowInstance();
                cf.setSource(prev); cf.setTarget(main);
                mgr.addElement(cf, activity);
                prev = main;
            }
        }

        ActivityFinalNode end = f.createActivityFinalNodeInstance();
        end.setName("End");
        mgr.addElement(end, activity);
        partitions.values().iterator().next().getNode().add(end);

        ControlFlow tail = f.createControlFlowInstance();
        tail.setSource(prev); tail.setTarget(end);
        mgr.addElement(tail, activity);

        return mainMap;
    }

    /* =============================================================
                        SUB‑DIAGRAM GENERATION
       ============================================================= */

    private void createSubactionDiagrams(Project project,
                                         List<ActivityData> rows,
                                         Map<String,StructuredActivityNode> mainActionMap,
                                         Map<String,ActionTypeChooser.ActionType> actionTypes)
            throws ReadOnlyElementException {

        /* bucket subactions by their parent action name */
        Map<String,List<ActivityData>> byParent = new HashMap<>();
        for (ActivityData d : rows)
            if (d.isSubAction())
                byParent.computeIfAbsent(d.getParentName(), k -> new ArrayList<>()).add(d);

        for (var e : byParent.entrySet()) {
            StructuredActivityNode parentSAN = mainActionMap.get(e.getKey());
            if (parentSAN == null) continue;

            createSubactionDiagram(project, parentSAN,
                                   e.getValue(), actionTypes);
        }
    }

    private void createSubactionDiagram(Project project,
                                        StructuredActivityNode parentNode,
                                        List<ActivityData> subRows,
                                        Map<String,ActionTypeChooser.ActionType> actionTypes)
            throws ReadOnlyElementException {

        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();

        Activity parentAct = findOwningActivity(parentNode);   // already in your code
        Map<String,ActivityPartition> subpartitions =
                createPartitionsInMainOrder(project, parentAct, subRows);
     

        Diagram subDiag =
                ModelElementsManager.getInstance()
                    .createDiagram("SysML Activity Diagram", parentNode);
        subDiag.setName(parentNode.getName());
        DiagramPresentationElement subDpe = project.getDiagram(subDiag);
        
        /* Setup swimlanes */
        int centerX = (DIAGRAM_WIDTH - LANE_WIDTH * subpartitions.size()) / 2;
        Map<ActivityPartition,ShapeElement> laneShapes = new HashMap<>();

        buildAndPlaceSwimlanes(subDpe, subpartitions,
                               countActionsPerActor(parentNode),
                               centerX, START_Y - 50,
                               laneShapes);

        /* Build the list of nodes to layout */
        List<ActivityNode> nodesToLayout = new ArrayList<>();
        
        /* 1. Create Initial Node if it doesn't exist */
        InitialNode startNode = null;
        for (Element child : parentNode.getOwnedElement()) {
            if (child instanceof InitialNode) {
                startNode = (InitialNode) child;
                break;
            }
        }
        if (startNode == null) {
            startNode = f.createInitialNodeInstance();
            startNode.setName("Start");
            mgr.addElement(startNode, parentNode);
            // Add to first partition
            if (!subpartitions.isEmpty()) {
                subpartitions.values().iterator().next().getNode().add(startNode);
            }
        }
        nodesToLayout.add(startNode);
        
        /* 2. Collect all sub-action nodes (already created in createActivityNodes) */
        List<ActivityNode> subActionNodes = new ArrayList<>();
        for (ActivityData subData : subRows) {
            // Find the existing sub-action node by name
            for (Element child : parentNode.getOwnedElement()) {
                if (child instanceof ActivityNode node && 
                    !node.equals(parentNode) &&
                    node.getName() != null && 
                    node.getName().equals(subData.getName())) {
                    subActionNodes.add(node);
                    break;
                }
            }
        }
        nodesToLayout.addAll(subActionNodes);
        
        /* 3. Create Activity Final Node if it doesn't exist */
        ActivityFinalNode endNode = null;
        for (Element child : parentNode.getOwnedElement()) {
            if (child instanceof ActivityFinalNode) {
                endNode = (ActivityFinalNode) child;
                break;
            }
        }
        if (endNode == null) {
            endNode = f.createActivityFinalNodeInstance();
            endNode.setName("End");
            mgr.addElement(endNode, parentNode);
            // Add to first partition
            if (!subpartitions.isEmpty()) {
                subpartitions.values().iterator().next().getNode().add(endNode);
            }
        }
        nodesToLayout.add(endNode);
        
        /* Create control flows if they don't exist */
        createControlFlowsIfNeeded(project, parentNode, startNode, subActionNodes, endNode);


        PresentationElementsManager pem = PresentationElementsManager.getInstance();

        for (ActivityNode node : nodesToLayout) {
            if (subDpe.findPresentationElement(node, ShapeElement.class) == null) {
                // ALWAYS create the shape directly in the diagram, never in the lane
                pem.createShapeElement(node, subDpe);
            }
        }

        /* Layout the nodes */
        SubdiagramGridLayouter.layoutNodeList(nodesToLayout, subDpe, subpartitions, START_Y, Y_STEP);
        
        /* Create control flow presentations */
        populateDiagramPaths(parentNode, subDpe);
        
        // We leave the diagram closed; user can open it from the browser
    }
    
    /**
     * Create brand‑new partitions for the sub‑diagram **in the same order**
     * as they appear in the parent Activity (main diagram), then append any
     * extra actors found only in the sub‑rows.
     *
     * @param project         your current Project
     * @param parentActivity  the Activity that owns the parent SAN
     * @param subRows         CSV rows for this sub‑diagram
     * @return LinkedHashMap <actorName , new ActivityPartition>
     */
    private Map<String,ActivityPartition> createPartitionsInMainOrder(
            Project project,
            Activity parentActivity,
            List<ActivityData> subRows) throws ReadOnlyElementException {

        ElementsFactory      factory = project.getElementsFactory();
        ModelElementsManager mgr     = ModelElementsManager.getInstance();

        /* 1 — which actors do we need in this sub‑diagram? */
        LinkedHashSet<String> actorsNeeded = new LinkedHashSet<>();
        for (ActivityData row : subRows)
            actorsNeeded.add(actorName(row.getActor()));   // actorName(...) already exists in this class

        /* 2 — preserve the main‑diagram order first */
        List<String> finalOrder = new ArrayList<>();
        for (ActivityPartition p : parentActivity.getPartition()) {
            String name = p.getName();
            if (actorsNeeded.contains(name)) {
                finalOrder.add(name);
                actorsNeeded.remove(name);    // so we don’t add twice
            }
        }

        /* 3 — append any brand‑new actors that weren’t in the main diagram */
        finalOrder.addAll(actorsNeeded);

        /* 4 — create fresh partitions following that exact order */
        Map<String,ActivityPartition> out = new LinkedHashMap<>();
        for (String actor : finalOrder) {
            ActivityPartition p = factory.createActivityPartitionInstance();  // API you already use elsewhere
            p.setName(actor);
            p.setDimension(true);               // vertical swim‑lane

            mgr.addElement(p, parentActivity);  // model ownership
            parentActivity.getPartition().add(p);

            out.put(actor, p);
        }
        return out;
    }


    private Activity findOwningActivity(Element e) {
        Element cur = e;
        while (cur != null && !(cur instanceof Activity)) cur = cur.getOwner();
        return (cur instanceof Activity act) ? act : null;
    }

    private void createControlFlowsIfNeeded(Project project,
                                          StructuredActivityNode parentNode,
                                          InitialNode startNode,
                                          List<ActivityNode> subActionNodes,
                                          ActivityFinalNode endNode)
            throws ReadOnlyElementException {
        
        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        
        // Check if flows already exist by building a set of existing connections
        Set<String> existingFlows = new HashSet<>();
        for (Element child : parentNode.getOwnedElement()) {
            if (child instanceof ControlFlow cf) {
                String flowKey = cf.getSource().getName() + "->" + cf.getTarget().getName();
                existingFlows.add(flowKey);
            }
        }
        
        ActivityNode prev = startNode;
        
        // Create flows: start -> first subaction -> ... -> last subaction -> end
        for (ActivityNode subAction : subActionNodes) {
            String flowKey = prev.getName() + "->" + subAction.getName();
            if (!existingFlows.contains(flowKey)) {
                ControlFlow cf = f.createControlFlowInstance();
                cf.setSource(prev);
                cf.setTarget(subAction);
                mgr.addElement(cf, parentNode);
            }
            prev = subAction;
        }
        
        // Final flow to end node
        if (prev != null && endNode != null) {
            String flowKey = prev.getName() + "->" + endNode.getName();
            if (!existingFlows.contains(flowKey)) {
                ControlFlow cf = f.createControlFlowInstance();
                cf.setSource(prev);
                cf.setTarget(endNode);
                mgr.addElement(cf, parentNode);
            }
        }
    }

    /* =============================================================
                         DIAGRAM POPULATION
       ============================================================= */

    /** one shape per node, placed inside its swim‑lane column */
    private void populateDiagramNodes(Element context,
                                      DiagramPresentationElement dpe,
                                      Map<ActivityPartition,ShapeElement> laneShapes)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();

        for (ActivityNode node : getNodesOfContext(context)) {
            // Skip the context node itself (don't show parent in its own diagram)
            if (node.equals(context)) continue;

            ShapeElement parentShape = null;
            for (var e : laneShapes.entrySet())
                if (e.getKey().getNode().contains(node)) {
                    parentShape = e.getValue(); break;
                }

            if (dpe.findPresentationElement(node, ShapeElement.class) != null) continue;

            pem.createShapeElement(node,
                    (parentShape != null) ? parentShape : dpe);
        }
    }

    private void populateDiagramPaths(Element context,
                                      DiagramPresentationElement dpe)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        for (ControlFlow cf : getFlowsOfContext(context)) {
            PresentationElement src =
                    dpe.findPresentationElement(cf.getSource(), PresentationElement.class);
            PresentationElement tgt =
                    dpe.findPresentationElement(cf.getTarget(), PresentationElement.class);
            if (src != null && tgt != null)
                pem.createPathElement(cf, src, tgt);
        }
    }

    /* =============================================================
                           SHAPE BUILDERS
       ============================================================= */

    private DiagramPresentationElement createAndOpenDiagram(Project project,
                                                            Activity activity)
            throws ReadOnlyElementException {

        Diagram dgm = ModelElementsManager.getInstance()
                          .createDiagram("SysML Activity Diagram", activity);
        dgm.setName("Imported Activities");
        DiagramPresentationElement dpe = project.getDiagram(dgm);
        dpe.open();
        return dpe;
    }

    private void buildAndPlaceSwimlanes(DiagramPresentationElement dpe,
                                        Map<String,ActivityPartition> parts,
                                        Map<String,Integer> actionsPerActor,
                                        int startX, int startY,
                                        Map<ActivityPartition,ShapeElement> laneShapes)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        List<ActivityPartition> vertical = new ArrayList<>(parts.values());

        ShapeElement wrapper = (ShapeElement)
                pem.createSwimlane(Collections.emptyList(), vertical, dpe);

        int idx = 0, maxRows = 1;
        for (String actor : parts.keySet()) {
            ShapeElement colShape = (ShapeElement)
                    dpe.findPresentationElement(parts.get(actor), ShapeElement.class);

            int rows = actionsPerActor.getOrDefault(actor, 1);
            maxRows = Math.max(maxRows, rows);

            int colX = startX + idx * LANE_WIDTH;
            int colH = rows * (3 * ROW_HEIGHT);

            pem.reshapeShapeElement(colShape,
                    new Rectangle(colX, startY, LANE_WIDTH, colH));

            laneShapes.put(parts.get(actor), colShape);
            idx++;
        }

        pem.reshapeShapeElement(wrapper,
                new Rectangle(startX, startY,
                              LANE_WIDTH * vertical.size(),
                              maxRows * 3 * ROW_HEIGHT));
    }

    /* =============================================================
                        LOW‑LEVEL NODE FACTORIES
       ============================================================= */
    private StructuredActivityNode createMainAction(Project p, Activity owner,
                                                    ActivityData d,
                           ActionTypeChooser.ActionType t) throws ReadOnlyElementException {

        ElementsFactory f = p.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        StructuredActivityNode n = f.createStructuredActivityNodeInstance();
        n.setName(d.getName());
        mgr.addElement(n, owner);
        addPins(n, d, mgr, f);
        return n;
    }

    private ActivityNode createSubAction(Project p, ActivityNode parent,
                                         ActivityData d,
                           ActionTypeChooser.ActionType t) throws ReadOnlyElementException {

        ElementsFactory f = p.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();

        // Find the partition for this subaction's actor
        Activity owningActivity = findOwningActivity(parent);
        ActivityPartition partition = null;
        if (owningActivity != null) {
            String actorName = actorName(d.getActor());
            for (ActivityPartition part : owningActivity.getPartition()) {
                if (part.getName().equals(actorName)) {
                    partition = part;
                    break;
                }
            }
        }

        if (t == ActionTypeChooser.ActionType.CALL_BEHAVIOR) {
            CallBehaviorAction c = f.createCallBehaviorActionInstance();
            c.setName(d.getName());
            mgr.addElement(c, parent);
            addPins(c, d, mgr, f);
            
            // Assign to partition
            if (partition != null) {
                partition.getNode().add(c);
            }
            
            return c;
        } else {
            StructuredActivityNode s = f.createStructuredActivityNodeInstance();
            s.setName(d.getName());
            mgr.addElement(s, parent);
            addPins(s, d, mgr, f);
            
            // Assign to partition
            if (partition != null) {
                partition.getNode().add(s);
            }
            
            return s;
        }
    }

    private void addPins(StructuredActivityNode n, ActivityData d,
                         ModelElementsManager mgr, ElementsFactory f)
            throws ReadOnlyElementException {

        for (String in : d.getInputs()) {
            InputPin p = f.createInputPinInstance(); p.setName(in);
            mgr.addElement(p, n); n.getStructuredNodeInput().add(p);
        }
        for (String out : d.getOutputs()) {
            OutputPin p = f.createOutputPinInstance(); p.setName(out);
            mgr.addElement(p, n); n.getStructuredNodeOutput().add(p);
        }
    }

    private void addPins(CallBehaviorAction n, ActivityData d,
                         ModelElementsManager mgr, ElementsFactory f)
            throws ReadOnlyElementException {

        for (String in : d.getInputs()) {
            InputPin p = f.createInputPinInstance(); p.setName(in);
            mgr.addElement(p, n); n.getArgument().add(p);
        }
        for (String out : d.getOutputs()) {
            OutputPin p = f.createOutputPinInstance(); p.setName(out);
            mgr.addElement(p, n); n.getResult().add(p);
        }
    }

    /* =============================================================
                       GENERIC NODE / EDGE ACCESSORS
       ============================================================= */
    private Collection<ActivityNode> getNodesOfContext(Element ctx) {
        if (ctx instanceof Activity act)                return act.getNode();
        if (ctx instanceof StructuredActivityNode san) {
            List<ActivityNode> l = new ArrayList<>();
            for (Element e : san.getOwnedElement())
                if (e instanceof ActivityNode n) l.add(n);
            return l;
        }
        return Collections.emptyList();
    }

    private Collection<ControlFlow> getFlowsOfContext(Element ctx) {
        List<ControlFlow> out = new ArrayList<>();
        if (ctx instanceof Activity act)
            act.getEdge().stream()
                   .filter(e -> e instanceof ControlFlow)
                   .map(e -> (ControlFlow)e)
                   .forEach(out::add);
        else if (ctx instanceof StructuredActivityNode san)
            san.getOwnedElement().stream()
                   .filter(e -> e instanceof ControlFlow)
                   .map(e -> (ControlFlow)e)
                   .forEach(out::add);
        return out;
    }
}