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
package de.bund.bfr.knime.openkrise.views.tracingview2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.image.ImagePortObject;

import com.thoughtworks.xstream.XStream;

import de.bund.bfr.knime.IO;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.GraphNode;
import de.bund.bfr.knime.openkrise.MyDelivery;
import de.bund.bfr.knime.openkrise.MyNewTracing;
import de.bund.bfr.knime.openkrise.views.TracingConstants;
import de.bund.bfr.knime.openkrise.views.TracingUtilities;

/**
 * This is the model implementation of TracingVisualizer.
 * 
 * 
 * @author Christian Thoens
 */
public class TracingView2NodeModel extends NodeModel {

	private TracingView2Settings set;

	/**
	 * Constructor for the node model.
	 */
	protected TracingView2NodeModel() {
		super(new PortType[] { BufferedDataTable.TYPE, BufferedDataTable.TYPE,
				BufferedDataTable.TYPE }, new PortType[] {
				BufferedDataTable.TYPE, BufferedDataTable.TYPE,
				ImagePortObject.TYPE });
		set = new TracingView2Settings();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected PortObject[] execute(PortObject[] inObjects, ExecutionContext exec)
			throws Exception {
		BufferedDataTable nodeTable = (BufferedDataTable) inObjects[0];
		BufferedDataTable edgeTable = (BufferedDataTable) inObjects[1];
		HashMap<Integer, MyDelivery> tracing = getDeliveries((BufferedDataTable) inObjects[2]);
		TracingCanvas canvas = new TracingView2CanvasCreator(nodeTable,
				edgeTable, tracing, set).createGraphCanvas();
		TracingCanvas allEdgesCanvas = createAllEdgesCanvas(nodeTable,
				edgeTable, tracing, set);

		int index = 0;
		DataTableSpec nodeOutSpec = createNodeOutSpec(nodeTable.getSpec());
		BufferedDataContainer nodeContainer = exec
				.createDataContainer(nodeOutSpec);

		for (GraphNode node : allEdgesCanvas.getNodes()) {
			DataCell[] cells = new DataCell[nodeOutSpec.getNumColumns()];

			for (int i = 0; i < cells.length; i++) {
				cells[i] = DataType.getMissingCell();
			}

			for (String property : allEdgesCanvas.getNodeProperties().keySet()) {
				int column = nodeOutSpec.findColumnIndex(property);

				if (column != -1) {
					Class<?> type = allEdgesCanvas.getNodeProperties().get(
							property);

					if (type == String.class) {
						cells[column] = IO.createCell((String) node
								.getProperties().get(property));
					} else if (type == Integer.class) {
						cells[column] = IO.createCell((Integer) node
								.getProperties().get(property));
					} else if (type == Double.class) {
						cells[column] = IO.createCell((Double) node
								.getProperties().get(property));
					} else if (type == Boolean.class) {
						cells[column] = IO.createCell((Boolean) node
								.getProperties().get(property));
					}
				}
			}

			nodeContainer.addRowToTable(new DefaultRow(index + "", cells));
			exec.checkCanceled();
			exec.setProgress((double) index
					/ (double) (allEdgesCanvas.getNodes().size() + allEdgesCanvas
							.getEdges().size()));
			index++;
		}

		nodeContainer.close();

		DataTableSpec edgeOutSpec = createEdgeOutSpec(edgeTable.getSpec());
		BufferedDataContainer edgeContainer = exec
				.createDataContainer(edgeOutSpec);

		for (Edge<GraphNode> edge : allEdgesCanvas.getEdges()) {
			DataCell[] cells = new DataCell[edgeOutSpec.getNumColumns()];

			for (int i = 0; i < cells.length; i++) {
				cells[i] = DataType.getMissingCell();
			}

			for (String property : allEdgesCanvas.getEdgeProperties().keySet()) {
				int column = edgeOutSpec.findColumnIndex(property);

				if (column != -1) {
					Class<?> type = allEdgesCanvas.getEdgeProperties().get(
							property);

					if (type == String.class) {
						cells[column] = IO.createCell((String) edge
								.getProperties().get(property));
					} else if (type == Integer.class) {
						cells[column] = IO.createCell((Integer) edge
								.getProperties().get(property));
					} else if (type == Double.class) {
						cells[column] = IO.createCell((Double) edge
								.getProperties().get(property));
					} else if (type == Boolean.class) {
						cells[column] = IO.createCell((Boolean) edge
								.getProperties().get(property));
					}
				}
			}

			edgeContainer.addRowToTable(new DefaultRow((index - allEdgesCanvas
					.getNodes().size()) + "", cells));
			exec.checkCanceled();
			exec.setProgress((double) index
					/ (double) (allEdgesCanvas.getNodes().size() + allEdgesCanvas
							.getEdges().size()));
			index++;
		}

		edgeContainer.close();

		return new PortObject[] { nodeContainer.getTable(),
				edgeContainer.getTable(),
				TracingUtilities.getImage(canvas, set.isExportAsSvg()) };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs)
			throws InvalidSettingsException {
		DataTableSpec nodeSpec = (DataTableSpec) inSpecs[0];
		DataTableSpec edgeSpec = (DataTableSpec) inSpecs[1];

		return new PortObjectSpec[] { createNodeOutSpec(nodeSpec),
				createEdgeOutSpec(edgeSpec),
				TracingUtilities.getImageSpec(set.isExportAsSvg()) };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		set.saveSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		set.loadSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings)
			throws InvalidSettingsException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
			CanceledExecutionException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
			CanceledExecutionException {
	}

	protected static HashMap<Integer, MyDelivery> getDeliveries(
			BufferedDataTable dataTable) throws NotConfigurableException {
		if (dataTable.getRowCount() == 0) {
			throw new NotConfigurableException("Tracing Table is empty");
		}

		DataRow row = null;

		for (DataRow r : dataTable) {
			row = r;
			break;
		}

		DataCell cell = row.getCell(0);
		String xml = ((StringValue) cell).getStringValue();
		XStream xstream = MyNewTracing.getXStream();

		return ((MyNewTracing) xstream.fromXML(xml)).getAllDeliveries();
	}

	private static DataTableSpec createNodeOutSpec(DataTableSpec nodeSpec) {
		List<DataColumnSpec> newNodeSpec = new ArrayList<DataColumnSpec>();
		Set<String> columnNames = new LinkedHashSet<String>();

		for (DataColumnSpec column : nodeSpec) {
			newNodeSpec.add(column);
			columnNames.add(column.getName());
		}

		if (!columnNames.contains(TracingConstants.CASE_WEIGHT_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.CASE_WEIGHT_COLUMN, DoubleCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.CROSS_CONTAMINATION_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.CROSS_CONTAMINATION_COLUMN,
					BooleanCell.TYPE).createSpec());
		}

		if (!columnNames.contains(TracingConstants.SCORE_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.SCORE_COLUMN, DoubleCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.FILTER_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.FILTER_COLUMN, BooleanCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.BACKWARD_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.BACKWARD_COLUMN, BooleanCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.FORWARD_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.FORWARD_COLUMN, BooleanCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.SIMPLE_SUPPLIER_COLUMN)) {
			newNodeSpec.add(new DataColumnSpecCreator(
					TracingConstants.SIMPLE_SUPPLIER_COLUMN, BooleanCell.TYPE)
					.createSpec());
		}

		return new DataTableSpec(newNodeSpec.toArray(new DataColumnSpec[0]));
	}

	private static DataTableSpec createEdgeOutSpec(DataTableSpec edgeSpec) {
		List<DataColumnSpec> newEdgeSpec = new ArrayList<DataColumnSpec>();
		Set<String> columnNames = new LinkedHashSet<String>();

		for (DataColumnSpec column : edgeSpec) {
			newEdgeSpec.add(column);
			columnNames.add(column.getName());
		}

		if (!columnNames.contains(TracingConstants.SCORE_COLUMN)) {
			newEdgeSpec.add(new DataColumnSpecCreator(
					TracingConstants.SCORE_COLUMN, DoubleCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.BACKWARD_COLUMN)) {
			newEdgeSpec.add(new DataColumnSpecCreator(
					TracingConstants.BACKWARD_COLUMN, BooleanCell.TYPE)
					.createSpec());
		}

		if (!columnNames.contains(TracingConstants.FORWARD_COLUMN)) {
			newEdgeSpec.add(new DataColumnSpecCreator(
					TracingConstants.FORWARD_COLUMN, BooleanCell.TYPE)
					.createSpec());
		}

		return new DataTableSpec(newEdgeSpec.toArray(new DataColumnSpec[0]));
	}

	private static TracingCanvas createAllEdgesCanvas(
			BufferedDataTable nodeTable, BufferedDataTable edgeTable,
			HashMap<Integer, MyDelivery> deliveries, TracingView2Settings set) {
		boolean joinEdges = set.isJoinEdges();

		set.setJoinEdges(false);

		TracingCanvas canvas = new TracingView2CanvasCreator(nodeTable,
				edgeTable, deliveries, set).createGraphCanvas();

		set.setJoinEdges(joinEdges);

		return canvas;
	}

}
