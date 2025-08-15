package com.example.csvactivityplugin;

import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.*;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lays out ActivityNode shapes in a single centred column, at regular vertical
 * intervals.  Also positions input pins on the left and output pins on the right.
 * Supports OpaqueAction, CallBehaviorAction, StructuredActivityNode, and the
 * small control nodes (Initial, Final, etc.).
 */
public final class DiagramGridLayouter {
    private DiagramGridLayouter() {}

    /** Column‑layout entry point (diagramWidth = 1200px, laneWidth = 420px). */
    public static void layout(Activity activity,
                              DiagramPresentationElement dpe,
                              Map<String,ActivityPartition> partitions,
                              int startY,
                              int yStep)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        int y = startY;

        final int DIAGRAM_WIDTH = 1200;
        final int LANE_WIDTH    = 480;
        final int ACTION_WIDTH  = 180;

        /* ----- single‑column fallback ---------------------------------- */
        boolean noLanes = partitions.isEmpty();
        int centreColumnX = (DIAGRAM_WIDTH - ACTION_WIDTH) / 2;

        /* ----- pre‑compute lane left‑edges if we have actors ----------- */
        List<String> actorsOrdered = new ArrayList<>(partitions.keySet());  // insertion order
        Map<String,Integer> actorIndex = new java.util.HashMap<>();
        for (int i = 0; i < actorsOrdered.size(); i++) actorIndex.put(actorsOrdered.get(i), i);

        // FIX: Use the same calculation as ActivityDiagramCreator
        int swimlaneStartX = noLanes ? 0 : (DIAGRAM_WIDTH - LANE_WIDTH * actorsOrdered.size()) / 2;
        
        String lastActor = actorsOrdered.isEmpty() ? null : actorsOrdered.get(0);

        /* ----- walk through nodes one by one --------------------------- */
        for (ActivityNode node : activity.getNode()) {
            PresentationElement pe = dpe.findPresentationElement(node, PresentationElement.class);
            if (!(pe instanceof ShapeElement se)) continue;

            /* size defaults */
            int width  = ACTION_WIDTH;
            int height = 80;
            boolean isAction = node instanceof OpaqueAction
                             || node instanceof CallBehaviorAction
                             || node instanceof StructuredActivityNode;
            if (!isAction) width = height = 20;

            /* choose X --------------------------------------------------- */
            int nodeX;
            if (noLanes) {
                /* centre everything */
                nodeX = isAction
                      ? centreColumnX
                      : centreColumnX + (ACTION_WIDTH - width)/2;
            } else {
                String actor;
                
                if (node instanceof ActivityFinalNode && lastActor != null) {
                    actor = lastActor;
                } else {
                    /* lane mode */
                    actor = node.getInPartition().isEmpty()
                                 ? actorsOrdered.get(0)  // fall back
                                 : node.getInPartition().iterator().next().getName();
                    lastActor = actor;    
                }
                int idx = actorIndex.getOrDefault(actor, 0);
                
                // FIX: Try to get the actual swimlane bounds if possible
                ActivityPartition partition = partitions.get(actor);
                if (partition != null) {
                    // Try to get the actual presentation element for this partition
                    PresentationElement partitionPE = dpe.findPresentationElement(partition, PresentationElement.class);
                    if (partitionPE instanceof ShapeElement partitionShape) {
                        Rectangle partitionBounds = partitionShape.getBounds();
                        // Center within the actual swimlane bounds
                        nodeX = partitionBounds.x + (partitionBounds.width - width) / 2;
                    } else {
                        // Fallback to calculated position
                        int laneLeft = swimlaneStartX + idx * LANE_WIDTH;
                        nodeX = laneLeft + (LANE_WIDTH - width) / 2;
                    }
                } else {
                    // Fallback to calculated position
                    int laneLeft = swimlaneStartX + idx * LANE_WIDTH;
                    nodeX = laneLeft + (LANE_WIDTH - width) / 2;
                }
            }

            /* reshape node */
            Rectangle rect = new Rectangle(nodeX, y, width, height);
            pem.reshapeShapeElement(se, rect);

            /* ---- pin logic (unchanged, copied from your version) ------ */
            List<InputPin>  inPins  = new ArrayList<>();
            List<OutputPin> outPins = new ArrayList<>();
            if (node instanceof OpaqueAction oa) {
                oa.getInput().stream().filter(p -> p instanceof InputPin)
                  .map(p -> (InputPin)p).forEach(inPins::add);
                oa.getOutput().stream().filter(p -> p instanceof OutputPin)
                  .map(p -> (OutputPin)p).forEach(outPins::add);
            } else if (node instanceof CallBehaviorAction cba) {
                cba.getArgument().stream().filter(p -> p instanceof InputPin)
                  .map(p -> (InputPin)p).forEach(inPins::add);
                cba.getResult().stream().filter(p -> p instanceof OutputPin)
                  .map(p -> (OutputPin)p).forEach(outPins::add);
            } else if (node instanceof StructuredActivityNode san) {
                san.getStructuredNodeInput().stream().filter(p -> p instanceof InputPin)
                  .map(p -> (InputPin)p).forEach(inPins::add);
                san.getStructuredNodeOutput().stream().filter(p -> p instanceof OutputPin)
                  .map(p -> (OutputPin)p).forEach(outPins::add);
            }

            if (inPins.size() > 3 || outPins.size() > 3) {
                height += (Math.max(inPins.size(), outPins.size()) - 3) * 25;
                pem.reshapeShapeElement(se, new Rectangle(nodeX, y, width, height));
            }

            if (!inPins.isEmpty() || !outPins.isEmpty()) {
                Rectangle actual = se.getBounds();
                positionPins(inPins, outPins, dpe, pem,
                             actual.x, actual.y, actual.width, actual.height);
            }
            if (node instanceof CallBehaviorAction || node instanceof StructuredActivityNode) {
            pem.reshapeShapeElement(
                      se,
                      new Rectangle(rect.x, rect.y, ACTION_WIDTH, rect.height)
                    );
            }

            y += height + yStep;
        }
    }
    
    
    
    

    /* --------------------------------------------------------------- */
    /* helper to place pins                                            */
    /* --------------------------------------------------------------- */
    private static void positionPins(List<InputPin>  inPins,
                                     List<OutputPin> outPins,
                                     DiagramPresentationElement dpe,
                                     PresentationElementsManager pem,
                                     int actionX, int actionY,
                                     int actionW, int actionH)
            throws ReadOnlyElementException {

        final int pinW = 20, pinH = 20, pinGap = 5;

        /* left side – inputs */
        int totInH = inPins.size() * pinH + Math.max(0, inPins.size() - 1) * pinGap;
        int inStartY = actionY + (actionH - totInH) / 2;

        for (int i = 0; i < inPins.size(); i++) {
            ShapeElement ps = (ShapeElement)
                    dpe.findPresentationElement(inPins.get(i), PresentationElement.class);
            if (ps == null) continue;
            int px = actionX - pinW / 2;
            int py = inStartY + i * (pinH + pinGap);
            pem.reshapeShapeElement(ps, new Rectangle(px, py, pinW, pinH));
        }

        /* right side – outputs */
        int totOutH = outPins.size() * pinH + Math.max(0, outPins.size() - 1) * pinGap;
        int outStartY = actionY + (actionH - totOutH) / 2;

        for (int i = 0; i < outPins.size(); i++) {
            ShapeElement ps = (ShapeElement)
                    dpe.findPresentationElement(outPins.get(i), PresentationElement.class);
            if (ps == null) continue;
            int px = actionX + actionW - pinW / 2;
            int py = outStartY + i * (pinH + pinGap);
            pem.reshapeShapeElement(ps, new Rectangle(px, py, pinW, pinH));
        }
    }
}