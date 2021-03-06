/*******************************************************************************
 * Copyright (c) 2017 German Federal Institute for Risk Assessment (BfR)
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
 *
 * Contributors:
 *     Department Biological Safety - BfR
 *******************************************************************************/
package de.bund.bfr.knime.openkrise.util.json;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NoSettingsNodeModel;

import com.google.common.collect.Iterables;

import de.bund.bfr.knime.openkrise.TracingColumns;

/**
 * @author Christian Thoens
 */
public class FromJsonNodeModel extends NoSettingsNodeModel {

	/**
	 * Constructor for the node model.
	 */
	protected FromJsonNodeModel() {
		super(1, 3);
	}

	@Override
	protected BufferedDataTable[] execute(BufferedDataTable[] inData, ExecutionContext exec) throws Exception {
		DataRow row = Iterables.getFirst(inData[0], null);
		DataCell cell = row.getCell(inData[0].getSpec().findColumnIndex(JsonConstants.JSON_COLUMN));

		if (cell.isMissing()) {
			throw new Exception("Cell in " + JsonConstants.JSON_COLUMN + " is missing");
		}

		JsonObject json = (JsonObject) ((JSONValue) cell).getJsonValue();

		if (!json.containsKey(JsonConstants.ELEMENTS)) {
			throw new Exception("Wrong json format. Must be exported from FoodChain-Lab Web App");
		}

		JsonObject elements = (JsonObject) json.get(JsonConstants.ELEMENTS);
		JsonArray stations = elements.getJsonArray(JsonConstants.STATIONS);
		JsonArray deliveries = elements.getJsonArray(JsonConstants.DELIVERIES);
		Map<String, DataType> stationColumns = new LinkedHashMap<>();
		Map<String, DataType> deliveryColumns = new LinkedHashMap<>();

		for (JsonValue s : stations) {
			for (JsonValue prop : ((JsonObject) s).getJsonArray(JsonConstants.PROPERTIES)) {
				stationColumns.put(((JsonObject) prop).getString(JsonConstants.NAME), StringCell.TYPE);
			}
		}

		for (JsonValue d : deliveries) {
			for (JsonValue prop : ((JsonObject) d).getJsonArray(JsonConstants.PROPERTIES)) {
				deliveryColumns.put(((JsonObject) prop).getString(JsonConstants.NAME), StringCell.TYPE);
			}
		}

		stationColumns.put(TracingColumns.ID, StringCell.TYPE);
		stationColumns.put(TracingColumns.NAME, StringCell.TYPE);
		deliveryColumns.put(TracingColumns.ID, StringCell.TYPE);
		deliveryColumns.put(TracingColumns.NAME, StringCell.TYPE);
		deliveryColumns.put(TracingColumns.FROM, StringCell.TYPE);
		deliveryColumns.put(TracingColumns.TO, StringCell.TYPE);

		DataTableSpec stationsSpec = toTableSpec(stationColumns);
		DataTableSpec deliveriesSpec = toTableSpec(deliveryColumns);
		DataTableSpec deliveryRelationsSpec = new DataTableSpec(
				new DataColumnSpecCreator(TracingColumns.FROM, StringCell.TYPE).createSpec(),
				new DataColumnSpecCreator(TracingColumns.TO, StringCell.TYPE).createSpec());
		BufferedDataContainer stationsContainer = exec.createDataContainer(stationsSpec);
		BufferedDataContainer deliveriesContainer = exec.createDataContainer(deliveriesSpec);
		BufferedDataContainer deliveryRelationsContainer = exec.createDataContainer(deliveryRelationsSpec);
		long stationsIndex = 0;
		long deliveriesIndex = 0;
		long deliveryRelationsIndex = 0;

		for (JsonValue s : stations) {
			DataCell[] cells = new DataCell[stationsSpec.getNumColumns()];

			Arrays.fill(cells, DataType.getMissingCell());

			JsonObject station = (JsonObject) s;

			for (JsonValue prop : station.getJsonArray(JsonConstants.PROPERTIES)) {
				String name = ((JsonObject) prop).getString(JsonConstants.NAME);
				JsonValue value = ((JsonObject) prop).get(JsonConstants.VALUE);

				if (stationsSpec.containsName(name)) {
					cells[stationsSpec.findColumnIndex(name)] = value instanceof JsonString
							? new StringCell(((JsonString) value).getString()) : DataType.getMissingCell();
				}
			}

			cells[stationsSpec.findColumnIndex(TracingColumns.ID)] = new StringCell(
					readStationId(station.getString(JsonConstants.ID)));
			cells[stationsSpec.findColumnIndex(TracingColumns.NAME)] = new StringCell(
					station.getString(JsonConstants.NAME));

			stationsContainer.addRowToTable(new DefaultRow(RowKey.createRowKey(stationsIndex++), cells));

			JsonValue connections = station.get(JsonConstants.CONNECTIONS);

			if (connections instanceof JsonArray) {
				for (JsonValue c : (JsonArray) connections) {
					if (c instanceof JsonObject) {
						deliveryRelationsContainer.addRowToTable(new DefaultRow(
								RowKey.createRowKey(deliveryRelationsIndex++),
								new StringCell(readDeliveryId(((JsonObject) c).getString(JsonConstants.SOURCE))),
								new StringCell(readDeliveryId(((JsonObject) c).getString(JsonConstants.TARGET)))));
					}
				}
			}
		}

		for (JsonValue d : deliveries) {
			DataCell[] cells = new DataCell[deliveriesSpec.getNumColumns()];

			Arrays.fill(cells, DataType.getMissingCell());

			JsonObject delivery = (JsonObject) d;

			for (JsonValue prop : delivery.getJsonArray(JsonConstants.PROPERTIES)) {
				String name = ((JsonObject) prop).getString(JsonConstants.NAME);
				JsonValue value = ((JsonObject) prop).get(JsonConstants.VALUE);

				if (deliveriesSpec.containsName(name)) {
					cells[deliveriesSpec.findColumnIndex(name)] = value instanceof JsonString
							? new StringCell(((JsonString) value).getString()) : DataType.getMissingCell();
				}
			}

			cells[deliveriesSpec.findColumnIndex(TracingColumns.ID)] = new StringCell(
					readDeliveryId(delivery.getString(JsonConstants.ID)));
			cells[deliveriesSpec.findColumnIndex(TracingColumns.NAME)] = new StringCell(
					delivery.getString(JsonConstants.NAME));
			cells[deliveriesSpec.findColumnIndex(TracingColumns.FROM)] = new StringCell(
					readStationId(delivery.getString(JsonConstants.SOURCE)));
			cells[deliveriesSpec.findColumnIndex(TracingColumns.TO)] = new StringCell(
					readStationId(delivery.getString(JsonConstants.TARGET)));

			deliveriesContainer.addRowToTable(new DefaultRow(RowKey.createRowKey(deliveriesIndex++), cells));
		}

		stationsContainer.close();
		deliveriesContainer.close();
		deliveryRelationsContainer.close();

		return new BufferedDataTable[] { stationsContainer.getTable(), deliveriesContainer.getTable(),
				deliveryRelationsContainer.getTable() };
	}

	@Override
	protected DataTableSpec[] configure(DataTableSpec[] inSpecs) throws InvalidSettingsException {
		if (!inSpecs[0].containsName(JsonConstants.JSON_COLUMN)) {
			throw new InvalidSettingsException(JsonConstants.JSON_COLUMN + " is missing");
		} else if (!inSpecs[0].getColumnSpec(JsonConstants.JSON_COLUMN).getType().isCompatible(JSONValue.class)) {
			throw new InvalidSettingsException(JsonConstants.JSON_COLUMN + " must be of type JSON");
		}

		return null;
	}

	@Override
	protected void loadInternals(File nodeInternDir, ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	@Override
	protected void saveInternals(File nodeInternDir, ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	private static DataTableSpec toTableSpec(Map<String, DataType> columns) {
		List<DataColumnSpec> columnSpecs = new ArrayList<>();

		columns.forEach((name, type) -> columnSpecs.add(new DataColumnSpecCreator(name, type).createSpec()));

		return new DataTableSpec(columnSpecs.toArray(new DataColumnSpec[0]));
	}

	private static String readStationId(String id) throws Exception {
		if (id.startsWith(JsonConstants.STATION_ID_PREFIX)) {
			return id.substring(JsonConstants.STATION_ID_PREFIX.length());
		} else {
			throw new Exception("Invalid id " + id);
		}
	}

	private static String readDeliveryId(String id) throws Exception {
		if (id.startsWith(JsonConstants.DELIVERY_ID_PREFIX)) {
			return id.substring(JsonConstants.DELIVERY_ID_PREFIX.length());
		} else {
			throw new Exception("Invalid id " + id);
		}
	}
}
