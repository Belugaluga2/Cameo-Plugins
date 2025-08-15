package com.example.csvactivityplugin;

import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.*;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.*;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import java.awt.Rectangle;
import java.util.*;

/**
 * Lays out ActivityNode shapes in sub-activity diagrams within their swimlanes.
 * Similar to DiagramGridLayouter but specific to sub-activity diagrams.
 */
public final class SubdiagramGridLayouter {
    private SubdiagramGridLayouter() {}

    /** Layout entry point for sub-activity diagrams */
    public static void layout(Element context,
                              DiagramPresentationElement dpe,
                              Map<String,ActivityPartition> partitions,
                              int startY,
                              int yStep)
            throws ReadOnlyElementException {

        // Collect all nodes to layout (exclude the parent node itself)
        List<ActivityNode> nodesToLayout = new ArrayList<>();
        
        // For StructuredActivityNode context, get its direct children
        if (context instanceof StructuredActivityNode san) {
            for (Element child : san.getOwnedElement()) {
                if (child instanceof ActivityNode node) {
                    // Skip the parent node itself if it somehow appears
                    if (!node.equals(context)) {
                        nodesToLayout.add(node);
                    }
                }
            }
        }

        // Sort nodes: InitialNode first, ActivityFinalNode last, others in between
        nodesToLayout.sort((a, b) -> {
            if (a instanceof InitialNode) return -1;
            if (b instanceof InitialNode) return 1;
            if (a instanceof ActivityFinalNode) return 1;
            if (b instanceof ActivityFinalNode) return -1;
            return 0;
        });

        layoutNodeList(nodesToLayout, dpe, partitions, startY, yStep);
    }
    
    /** Layout a specific list of nodes */
    public static void layoutNodeList(List<ActivityNode> nodesToLayout,
                                     DiagramPresentationElement dpe,
                                     Map<String,ActivityPartition> partitions,
                                     int startY,
                                     int yStep)
            throws ReadOnlyElementException {
        
        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        
        final int DIAGRAM_WIDTH = 1200;
        final int LANE_WIDTH    = 480;
        final int ACTION_WIDTH  = 180;
        final int ACTION_HEIGHT = 80;
        final int CONTROL_NODE_SIZE = 20;

        // Layout calculations
        boolean noLanes = partitions.isEmpty();
        int centreColumnX = (DIAGRAM_WIDTH - ACTION_WIDTH) / 2;

        // Pre-compute lane positions
        List<String> actorsOrdered = new ArrayList<>(partitions.keySet());
        Map<String,Integer> actorIndex = new HashMap<>();
        for (int i = 0; i < actorsOrdered.size(); i++) {
            actorIndex.put(actorsOrdered.get(i), i);
        }

        int swimlaneStartX = noLanes ? 0 : (DIAGRAM_WIDTH - LANE_WIDTH * actorsOrdered.size()) / 2;
        
        // Use universal Y tracker like DiagramGridLayouter
        int y = startY;
        
        // Track last actor like DiagramGridLayouter
        String lastActor = actorsOrdered.isEmpty() ? null : actorsOrdered.get(0);

        // Layout each node in the order they appear in the list
        for (ActivityNode node : nodesToLayout) {
            PresentationElement pe = dpe.findPresentationElement(node, PresentationElement.class);
            if (!(pe instanceof ShapeElement se)) continue;

            // Determine size
            int width = ACTION_WIDTH;
            int height = ACTION_HEIGHT;
            boolean isAction = node instanceof OpaqueAction
                            || node instanceof CallBehaviorAction
                            || node instanceof StructuredActivityNode;
            
            if (!isAction) {
                width = height = CONTROL_NODE_SIZE;
            }

            // Determine actor/lane - SAME LOGIC AS DiagramGridLayouter
            String actor = null;
            if (!noLanes) {
                if (node instanceof ActivityFinalNode && lastActor != null) {
                    actor = lastActor;
                } else {
                    // lane mode
                    actor = node.getInPartition().isEmpty()
                                 ? actorsOrdered.get(0)  // fall back
                                 : node.getInPartition().iterator().next().getName();
                    lastActor = actor;    
                }
            }

            // Calculate X position
            int nodeX;
            if (noLanes) {
                nodeX = isAction ? centreColumnX : centreColumnX + (ACTION_WIDTH - width) / 2;
            } else if (actor != null) {
                // Get actual swimlane bounds if possible
                ActivityPartition partition = partitions.get(actor);
                if (partition != null) {
                    PresentationElement partitionPE = dpe.findPresentationElement(partition, PresentationElement.class);
                    if (partitionPE instanceof ShapeElement partitionShape) {
                        Rectangle partitionBounds = partitionShape.getBounds();
                        nodeX = partitionBounds.x + (partitionBounds.width - width) / 2;
                    } else {
                        // Fallback to calculated position
                        int idx = actorIndex.getOrDefault(actor, 0);
                        int laneLeft = swimlaneStartX + idx * LANE_WIDTH;
                        nodeX = laneLeft + (LANE_WIDTH - width) / 2;
                    }
                } else {
                    // Fallback
                    nodeX = centreColumnX;
                }
            } else {
                nodeX = centreColumnX;
            }

            // Reshape the node - using universal Y like DiagramGridLayouter
            Rectangle rect = new Rectangle(nodeX, y, width, height);
            pem.reshapeShapeElement(se, rect);

            // Handle pins
            List<InputPin> inPins = new ArrayList<>();
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

            // Adjust height if many pins
            if (inPins.size() > 3 || outPins.size() > 3) {
                height += (Math.max(inPins.size(), outPins.size()) - 3) * 25;
                rect = new Rectangle(nodeX, y, width, height);
                pem.reshapeShapeElement(se, rect);
            }

            // Position pins
            if (!inPins.isEmpty() || !outPins.isEmpty()) {
                positionPins(inPins, outPins, dpe, pem,
                           rect.x, rect.y, rect.width, rect.height);
            }
            
            // Increment Y for next node - universal tracker
            y += height + yStep;
        }
    }

    /* Helper to place pins on left (input) and right (output) sides */
    private static void positionPins(List<InputPin> inPins,
                                   List<OutputPin> outPins,
                                   DiagramPresentationElement dpe,
                                   PresentationElementsManager pem,
                                   int actionX, int actionY,
                                   int actionW, int actionH)
            throws ReadOnlyElementException {

        final int pinW = 20, pinH = 20, pinGap = 5;

        // Left side - inputs
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

        // Right side - outputs
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