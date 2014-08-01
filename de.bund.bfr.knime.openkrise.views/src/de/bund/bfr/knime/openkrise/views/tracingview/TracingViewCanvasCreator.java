/*******************************************************************************
 * Copyright (c) 2014 Federal Institute for Risk Assessment (BfR), Germany 
 * 
 * Developers and contributors are 
 * Christian Thoens (BfR)
 * Armin A. Weiser (BfR)
 * Matthias Filter (BfR)
 * Annemarie Kaesbohrer (BfR)
 * Bernd Appel (BfR)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.bund.bfr.knime.openkrise.views.tracingview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.knime.core.node.BufferedDataTable;

import de.bund.bfr.knime.KnimeUtilities;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.GraphNode;
import de.bund.bfr.knime.openkrise.MyDelivery;
import de.bund.bfr.knime.openkrise.TracingConstants;
import de.bund.bfr.knime.openkrise.TracingUtilities;

public class TracingViewCanvasCreator {

	private BufferedDataTable nodeTable;
	private BufferedDataTable edgeTable;
	private HashMap<Integer, MyDelivery> deliveries;
	private TracingViewSettings set;

	public TracingViewCanvasCreator(BufferedDataTable nodeTable,
			BufferedDataTable edgeTable, HashMap<Integer, MyDelivery> tracing,
			TracingViewSettings set) {
		this.nodeTable = nodeTable;
		this.edgeTable = edgeTable;
		this.deliveries = tracing;
		this.set = set;
	}

	public TracingCanvas createGraphCanvas() {
		Map<String, Class<?>> nodeProperties = KnimeUtilities
				.getTableColumns(nodeTable.getSpec());
		Map<String, Class<?>> edgeProperties = KnimeUtilities
				.getTableColumns(edgeTable.getSpec());

		if (!nodeProperties.containsKey(TracingConstants.CASE_WEIGHT_COLUMN)) {
			nodeProperties.put(TracingConstants.CASE_WEIGHT_COLUMN,
					Double.class);
		}

		if (!nodeProperties
				.containsKey(TracingConstants.CROSS_CONTAMINATION_COLUMN)) {
			nodeProperties.put(TracingConstants.CROSS_CONTAMINATION_COLUMN,
					Boolean.class);
		}

		if (!nodeProperties.containsKey(TracingConstants.SCORE_COLUMN)) {
			nodeProperties.put(TracingConstants.SCORE_COLUMN, Double.class);
		}

		if (!nodeProperties.containsKey(TracingConstants.FILTER_COLUMN)) {
			nodeProperties.put(TracingConstants.FILTER_COLUMN, Boolean.class);
		}

		if (!nodeProperties.containsKey(TracingConstants.BACKWARD_COLUMN)) {
			nodeProperties.put(TracingConstants.BACKWARD_COLUMN, Boolean.class);
		}

		if (!nodeProperties.containsKey(TracingConstants.FORWARD_COLUMN)) {
			nodeProperties.put(TracingConstants.FORWARD_COLUMN, Boolean.class);
		}

		if (!edgeProperties.containsKey(TracingConstants.CASE_WEIGHT_COLUMN)) {
			edgeProperties.put(TracingConstants.CASE_WEIGHT_COLUMN,
					Double.class);
		}

		if (!edgeProperties
				.containsKey(TracingConstants.CROSS_CONTAMINATION_COLUMN)) {
			edgeProperties.put(TracingConstants.CROSS_CONTAMINATION_COLUMN,
					Boolean.class);
		}

		if (!edgeProperties.containsKey(TracingConstants.FILTER_COLUMN)) {
			edgeProperties.put(TracingConstants.FILTER_COLUMN, Boolean.class);
		}

		if (!edgeProperties.containsKey(TracingConstants.SCORE_COLUMN)) {
			edgeProperties.put(TracingConstants.SCORE_COLUMN, Double.class);
		}

		if (!edgeProperties.containsKey(TracingConstants.BACKWARD_COLUMN)) {
			edgeProperties.put(TracingConstants.BACKWARD_COLUMN, Boolean.class);
		}

		if (!edgeProperties.containsKey(TracingConstants.FORWARD_COLUMN)) {
			edgeProperties.put(TracingConstants.FORWARD_COLUMN, Boolean.class);
		}

		Map<String, GraphNode> nodes = TracingUtilities.readGraphNodes(
				nodeTable, nodeProperties);

		if (nodes.isEmpty()) {
			return null;
		}

		List<Edge<GraphNode>> edges = TracingUtilities.readEdges(edgeTable,
				edgeProperties, nodes);
		TracingCanvas canvas = new TracingCanvas(
				new ArrayList<>(nodes.values()), edges, nodeProperties,
				edgeProperties, deliveries);

		canvas.setPerformTracing(false);
		canvas.setShowLegend(set.getGraphSettings().isShowLegend());
		canvas.setCanvasSize(set.getGraphSettings().getCanvasSize());
		canvas.setEditingMode(set.getGraphSettings().getEditingMode());
		canvas.setNodeSize(set.getGraphSettings().getNodeSize());
		canvas.setFontSize(set.getGraphSettings().getFontSize());
		canvas.setFontBold(set.getGraphSettings().isFontBold());
		canvas.setJoinEdges(set.getGraphSettings().isJoinEdges());
		canvas.setCollapsedNodes(set.getGraphSettings().getCollapsedNodes());
		canvas.setNodeWeights(set.getNodeWeights());
		canvas.setEdgeWeights(set.getEdgeWeights());
		canvas.setNodeCrossContaminations(set.getNodeCrossContaminations());
		canvas.setEdgeCrossContaminations(set.getEdgeCrossContaminations());
		canvas.setNodeFilter(set.getNodeFilter());
		canvas.setEdgeFilter(set.getEdgeFilter());
		canvas.setEnforceTemporalOrder(set.isEnforeTemporalOrder());
		canvas.setShowConnected(set.isShowConnected());
		canvas.setLabel(set.getLabel());
		canvas.setNodeHighlightConditions(set.getGraphSettings()
				.getNodeHighlightConditions());
		canvas.setEdgeHighlightConditions(set.getGraphSettings()
				.getEdgeHighlightConditions());
		canvas.setSkipEdgelessNodes(set.getGraphSettings()
				.isSkipEdgelessNodes());
		canvas.setPerformTracing(true);
		canvas.setSelectedNodeIds(new LinkedHashSet<>(set.getGraphSettings()
				.getSelectedNodes()));
		canvas.setSelectedEdgeIds(new LinkedHashSet<>(set.getGraphSettings()
				.getSelectedEdges()));

		if (!Double.isNaN(set.getGraphSettings().getScaleX())
				&& !Double.isNaN(set.getGraphSettings().getScaleY())
				&& !Double.isNaN(set.getGraphSettings().getTranslationX())
				&& !Double.isNaN(set.getGraphSettings().getTranslationY())) {
			canvas.setTransform(set.getGraphSettings().getScaleX(), set
					.getGraphSettings().getScaleY(), set.getGraphSettings()
					.getTranslationX(), set.getGraphSettings()
					.getTranslationY());
		}

		canvas.setNodePositions(set.getGraphSettings().getNodePositions());

		return canvas;
	}
}
