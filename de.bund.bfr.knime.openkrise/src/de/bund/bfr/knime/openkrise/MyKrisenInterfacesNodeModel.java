/*******************************************************************************
 * Copyright (c) 2015 Federal Institute for Risk Assessment (BfR), Germany
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
package de.bund.bfr.knime.openkrise;

import static de.bund.bfr.knime.openkrise.generated.public_.Tables.CHARGEN;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.CHARGENVERBINDUNGEN;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.EXTRAFIELDS;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.LIEFERUNGEN;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.PRODUKTKATALOG;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.STATION;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.InvalidPathException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeModelWarningListener;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

import com.google.common.base.Splitter;

import de.bund.bfr.knime.KnimeUtils;
import de.bund.bfr.knime.openkrise.db.DBKernel;
import de.bund.bfr.knime.openkrise.db.MyDBI;
import de.bund.bfr.knime.openkrise.db.MyDBTablesNew;

/**
 * This is the model implementation of MyKrisenInterfaces.
 * 
 * 
 * @author draaw
 */
public class MyKrisenInterfacesNodeModel extends NodeModel implements NodeModelWarningListener {

	private MyKrisenInterfacesSettings set;
	private boolean warningsOccured;

	/**
	 * Constructor for the node model.
	 */
	protected MyKrisenInterfacesNodeModel() {
		super(0, 3);
		set = new MyKrisenInterfacesSettings();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {
		warningsOccured = false;
		addWarningListener(this);

		Connection conn = set.isUseExternalDb()
				? createLocalConnection("SA", "",
						KnimeUtils.getFile(removeNameOfDB(set.getDbPath())).getAbsolutePath())
				: DBKernel.getLocalConn(true);
		boolean useSerialAsID = !set.isAnonymize() && isSerialPossible(conn);
		Map<Integer, String> stationIds = new LinkedHashMap<>();
		Map<Integer, String> deliveryIds = new LinkedHashMap<>();

		for (Record r : DSL.using(conn, SQLDialect.HSQLDB).select().from(STATION)) {
			stationIds.put(r.getValue(STATION.ID),
					useSerialAsID ? r.getValue(STATION.SERIAL) : r.getValue(STATION.ID).toString());
		}

		for (Record r : DSL.using(conn, SQLDialect.HSQLDB).select().from(LIEFERUNGEN)) {
			deliveryIds.put(r.getValue(LIEFERUNGEN.ID),
					useSerialAsID ? r.getValue(LIEFERUNGEN.SERIAL) : r.getValue(LIEFERUNGEN.ID).toString());
		}

		Map<String, Delivery> deliveries = getDeliveries(conn, stationIds, deliveryIds);
		BufferedDataTable stationTable = getStationTable(conn, stationIds, deliveries, exec);
		BufferedDataTable deliveryTable = getDeliveryTable(conn, stationIds, deliveryIds, exec);
		BufferedDataTable deliveryConnectionsTable = getDeliveryConnectionsTable(conn, deliveries, exec);

		checkDates(deliveries);
		checkAmounts(deliveries);

		if (warningsOccured) {
			setWarningMessage("Look into the console - there are plausibility issues...");
			warningsOccured = false;
		}

		return new BufferedDataTable[] { stationTable, deliveryTable, deliveryConnectionsTable };
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
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
		return new DataTableSpec[] { null, null, null };
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
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		set.loadSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	protected static String removeNameOfDB(String path) throws InvalidPathException, MalformedURLException {
		if ((path.endsWith("\\DB") || path.endsWith("/DB")) && KnimeUtils.getFile(path + ".properties").exists()) {
			return path.substring(0, path.length() - 3);
		}

		return path;
	}

	private DataTableSpec getStationSpec(Connection conn) {
		List<DataColumnSpec> columns = new ArrayList<>();
		columns.add(new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_NAME, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_STREET, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_HOUSENO, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_ZIP, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_CITY, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DISTRICT, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_STATE, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_COUNTRY, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_SERIAL, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_SIMPLESUPPLIER, BooleanCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DEADSTART, BooleanCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DEADEND, BooleanCell.TYPE).createSpec());

		if (containsValues(conn, STATION.VATNUMBER))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_VAT, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.BETRIEBSART))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_TOB, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.ANZAHLFAELLE))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_NUMCASES, IntCell.TYPE).createSpec());
		if (containsValues(conn, STATION.DATUMBEGINN))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DATESTART, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.DATUMHOEHEPUNKT))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DATEPEAK, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.DATUMENDE))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DATEEND, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.IMPORTSOURCES))
			columns.add(new DataColumnSpecCreator(TracingColumns.FILESOURCES, StringCell.TYPE).createSpec());

		// Backward Compatibility Stuff
		if (set.isEnsureBackwardCompatibility()) {
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_NODE, StringCell.TYPE).createSpec());
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_COUNTY, StringCell.TYPE).createSpec());
		}

		// ExtraFields
		for (Record1<String> r : DSL.using(conn, SQLDialect.HSQLDB).selectDistinct(EXTRAFIELDS.ATTRIBUTE)
				.from(EXTRAFIELDS).where(EXTRAFIELDS.TABLENAME.equal(STATION.getName()))) {
			columns.add(new DataColumnSpecCreator("_" + r.value1(), StringCell.TYPE).createSpec());
		}

		return new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
	}

	private DataTableSpec getDeliverySpec(Connection conn) {
		List<DataColumnSpec> columns = new ArrayList<>();
		columns.add(new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.FROM, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.TO, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ITEMNUM, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ITEMNAME, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_DEPARTURE, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ARRIVAL, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_SERIAL, StringCell.TYPE).createSpec());

		if (containsValues(conn, PRODUKTKATALOG.PROZESSIERUNG))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_PROCESSING, StringCell.TYPE).createSpec());
		if (containsValues(conn, PRODUKTKATALOG.INTENDEDUSE))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_USAGE, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.CHARGENNR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_LOTNUM, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.MHD_DAY, CHARGEN.MHD_MONTH, CHARGEN.MHD_YEAR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_DATEEXP, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.PD_DAY, CHARGEN.PD_MONTH, CHARGEN.PD_YEAR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_DATEMANU, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.UNITMENGE))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_AMOUNT, DoubleCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.NUMPU)) {
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_NUM_PU, DoubleCell.TYPE).createSpec());
			if (containsValues(conn, LIEFERUNGEN.TYPEPU))
				columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_TYPE_PU, StringCell.TYPE).createSpec());
		}
		if (containsValues(conn, CHARGEN.ORIGINCOUNTRY))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ORIGIN, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.ENDCHAIN))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ENDCHAIN, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.EXPLANATION_ENDCHAIN))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ENDCHAINWHY, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.CONTACT_QUESTIONS_REMARKS))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_REMARKS, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.FURTHER_TRACEBACK))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_FURTHERTB, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.MICROBIOSAMPLE))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_MICROSAMPLE, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.IMPORTSOURCES))
			columns.add(new DataColumnSpecCreator(TracingColumns.FILESOURCES, StringCell.TYPE).createSpec());

		// Backward Compatibility Stuff
		if (set.isEnsureBackwardCompatibility()) {
			if (containsValues(conn, CHARGEN.CHARGENNR))
				columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_CHARGENUM, StringCell.TYPE).createSpec());
		}

		// ExtraFields
		for (Record2<String, String> r : DSL.using(conn, SQLDialect.HSQLDB)
				.selectDistinct(EXTRAFIELDS.TABLENAME, EXTRAFIELDS.ATTRIBUTE).from(EXTRAFIELDS)
				.where(EXTRAFIELDS.TABLENAME.equal(PRODUKTKATALOG.getName()))
				.or(EXTRAFIELDS.TABLENAME.equal(CHARGEN.getName()))
				.or(EXTRAFIELDS.TABLENAME.equal(LIEFERUNGEN.getName()))) {
			columns.add(new DataColumnSpecCreator("_" + r.value1() + "." + r.value2(), StringCell.TYPE).createSpec());
		}

		return new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
	}

	private BufferedDataTable getStationTable(Connection conn, Map<Integer, String> stationIds,
			Map<String, Delivery> deliveries, ExecutionContext exec) throws CanceledExecutionException {
		DataTableSpec spec = getStationSpec(conn);
		BufferedDataContainer container = exec.createDataContainer(spec);
		int index = 0;
		boolean bvlFormat = false;

		for (Record r : DSL.using(conn, SQLDialect.HSQLDB).select().from(STATION)) {
			String id = stationIds.get(r.getValue(STATION.ID));
			String district = clean(r.getValue(STATION.DISTRICT));
			String state = clean(r.getValue(STATION.BUNDESLAND));
			String country = clean(r.getValue(STATION.LAND));
			String zip = clean(r.getValue(STATION.PLZ));

			// TODO: Remove BVL-Format stuff. Corrupt databases should not be
			// fixed here
			if (index == 0 && state != null && (state.equals("Altenburger Land") || state.equals("Wesel"))) {
				bvlFormat = true;
			}

			if (bvlFormat) {
				district = state;
				state = country;

				if (zip != null && zip.length() == 4) {
					country = "BE";
				} else {
					country = "DE";
				}
			}

			String company = r.getValue(STATION.NAME) == null || set.isAnonymize()
					? getISO3166_2(country, state) + "#" + r.getValue(STATION.ID) : clean(r.getValue(STATION.NAME));
			DataCell[] cells = new DataCell[spec.getNumColumns()];

			fillCell(spec, cells, TracingColumns.ID, createCell(id));
			fillCell(spec, cells, TracingColumns.STATION_NODE, createCell(company));
			fillCell(spec, cells, TracingColumns.STATION_NAME, createCell(company));
			fillCell(spec, cells, TracingColumns.STATION_STREET,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(r.getValue(STATION.STRASSE)));
			fillCell(spec, cells, TracingColumns.STATION_HOUSENO,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(r.getValue(STATION.HAUSNUMMER)));
			fillCell(spec, cells, TracingColumns.STATION_ZIP, createCell(zip));
			fillCell(spec, cells, TracingColumns.STATION_CITY,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(r.getValue(STATION.ORT)));
			fillCell(spec, cells, TracingColumns.STATION_DISTRICT,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(district));
			fillCell(spec, cells, TracingColumns.STATION_STATE,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(state));
			fillCell(spec, cells, TracingColumns.STATION_COUNTRY,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(country));
			fillCell(spec, cells, TracingColumns.STATION_VAT,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(r.getValue(STATION.VATNUMBER)));
			fillCell(spec, cells, TracingColumns.STATION_TOB, createCell(r.getValue(STATION.BETRIEBSART)));
			fillCell(spec, cells, TracingColumns.STATION_NUMCASES, createCell(r.getValue(STATION.ANZAHLFAELLE)));
			fillCell(spec, cells, TracingColumns.STATION_DATESTART, createCell(r.getValue(STATION.DATUMBEGINN)));
			fillCell(spec, cells, TracingColumns.STATION_DATEPEAK, createCell(r.getValue(STATION.DATUMHOEHEPUNKT)));
			fillCell(spec, cells, TracingColumns.STATION_DATEEND, createCell(r.getValue(STATION.DATUMENDE)));
			fillCell(spec, cells, TracingColumns.STATION_SERIAL, createCell(r.getValue(STATION.SERIAL)));
			fillCell(spec, cells, TracingColumns.STATION_SIMPLESUPPLIER,
					isSimpleSupplier(deliveries, id) ? BooleanCell.TRUE : BooleanCell.FALSE);
			fillCell(spec, cells, TracingColumns.STATION_DEADSTART,
					isStationStart(deliveries, id) ? BooleanCell.TRUE : BooleanCell.FALSE);
			fillCell(spec, cells, TracingColumns.STATION_DEADEND,
					isStationEnd(deliveries, id) ? BooleanCell.TRUE : BooleanCell.FALSE);
			fillCell(spec, cells, TracingColumns.FILESOURCES, createCell(r.getValue(STATION.IMPORTSOURCES)));
			fillCell(spec, cells, TracingColumns.STATION_COUNTY,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(district));

			// Extras
			for (String extraCol : spec.getColumnNames()) {
				if (extraCol.startsWith("_")) {
					String attribute = extraCol.substring(1);
					Result<Record1<String>> result = DSL.using(conn, SQLDialect.HSQLDB).select(EXTRAFIELDS.VALUE)
							.from(EXTRAFIELDS)
							.where(EXTRAFIELDS.TABLENAME.equal(STATION.getName()),
									EXTRAFIELDS.ID.equal(r.getValue(STATION.ID)),
									EXTRAFIELDS.ATTRIBUTE.equal(attribute))
							.fetch();

					if (!result.isEmpty()) {
						fillCell(spec, cells, extraCol, createCell(result.get(0).value1()));
					} else {
						fillCell(spec, cells, extraCol, DataType.getMissingCell());
					}
				}
			}

			container.addRowToTable(new DefaultRow("Row" + index++, cells));
			exec.checkCanceled();
		}

		container.close();

		return container.getTable();
	}

	private BufferedDataTable getDeliveryTable(Connection conn, Map<Integer, String> stationIds,
			Map<Integer, String> deliveryIds, ExecutionContext exec) throws CanceledExecutionException {
		DataTableSpec spec = getDeliverySpec(conn);
		BufferedDataContainer container = exec.createDataContainer(spec);
		int index = 0;

		for (Record r : DSL.using(conn, SQLDialect.HSQLDB).select().from(LIEFERUNGEN).leftOuterJoin(CHARGEN)
				.on(LIEFERUNGEN.CHARGE.equal(CHARGEN.ID)).leftOuterJoin(PRODUKTKATALOG)
				.on(CHARGEN.ARTIKEL.equal(PRODUKTKATALOG.ID)).orderBy(PRODUKTKATALOG.ID)) {
			String id = deliveryIds.get(r.getValue(LIEFERUNGEN.ID));
			String fromId = stationIds.get(r.getValue(PRODUKTKATALOG.STATION));
			String toId = stationIds.get(r.getValue(LIEFERUNGEN.EMPFÄNGER));
			DataCell[] cells = new DataCell[spec.getNumColumns()];

			fillCell(spec, cells, TracingColumns.ID, createCell(id));
			fillCell(spec, cells, TracingColumns.FROM, createCell(fromId));
			fillCell(spec, cells, TracingColumns.TO, createCell(toId));

			fillCell(spec, cells, TracingColumns.DELIVERY_ITEMNAME, createCell(r.getValue(PRODUKTKATALOG.BEZEICHNUNG)));
			fillCell(spec, cells, TracingColumns.DELIVERY_ITEMNUM, set.isAnonymize() ? DataType.getMissingCell()
					: createCell(r.getValue(PRODUKTKATALOG.ARTIKELNUMMER)));
			fillCell(spec, cells, TracingColumns.DELIVERY_DEPARTURE,
					createCell(formatDate(r.getValue(LIEFERUNGEN.DD_DAY), r.getValue(LIEFERUNGEN.DD_MONTH),
							r.getValue(LIEFERUNGEN.DD_YEAR))));
			fillCell(spec, cells, TracingColumns.DELIVERY_ARRIVAL, createCell(formatDate(r.getValue(LIEFERUNGEN.AD_DAY),
					r.getValue(LIEFERUNGEN.AD_MONTH), r.getValue(LIEFERUNGEN.AD_YEAR))));
			fillCell(spec, cells, TracingColumns.DELIVERY_SERIAL, createCell(r.getValue(LIEFERUNGEN.SERIAL)));
			fillCell(spec, cells, TracingColumns.DELIVERY_PROCESSING,
					createCell(r.getValue(PRODUKTKATALOG.PROZESSIERUNG)));
			fillCell(spec, cells, TracingColumns.DELIVERY_USAGE, createCell(r.getValue(PRODUKTKATALOG.INTENDEDUSE)));
			fillCell(spec, cells, TracingColumns.DELIVERY_LOTNUM,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(r.getValue(CHARGEN.CHARGENNR)));
			fillCell(spec, cells, TracingColumns.DELIVERY_DATEEXP, createCell(formatDate(r.getValue(CHARGEN.MHD_DAY),
					r.getValue(CHARGEN.MHD_MONTH), r.getValue(CHARGEN.MHD_YEAR))));
			fillCell(spec, cells, TracingColumns.DELIVERY_DATEMANU, createCell(
					formatDate(r.getValue(CHARGEN.PD_DAY), r.getValue(CHARGEN.PD_MONTH), r.getValue(CHARGEN.PD_YEAR))));
			fillCell(spec, cells, TracingColumns.DELIVERY_AMOUNT,
					createCell(getAmountInKg(r.getValue(LIEFERUNGEN.UNITMENGE), r.getValue(LIEFERUNGEN.UNITEINHEIT))));
			fillCell(spec, cells, TracingColumns.DELIVERY_NUM_PU, createCell(r.getValue(LIEFERUNGEN.NUMPU)));
			fillCell(spec, cells, TracingColumns.DELIVERY_TYPE_PU, createCell(r.getValue(LIEFERUNGEN.TYPEPU)));
			fillCell(spec, cells, TracingColumns.DELIVERY_ENDCHAIN, createCell(r.getValue(LIEFERUNGEN.ENDCHAIN)));
			fillCell(spec, cells, TracingColumns.DELIVERY_ORIGIN, createCell(r.getValue(CHARGEN.ORIGINCOUNTRY)));
			fillCell(spec, cells, TracingColumns.DELIVERY_ENDCHAINWHY,
					createCell(r.getValue(LIEFERUNGEN.EXPLANATION_ENDCHAIN)));
			fillCell(spec, cells, TracingColumns.DELIVERY_REMARKS,
					createCell(r.getValue(LIEFERUNGEN.CONTACT_QUESTIONS_REMARKS)));
			fillCell(spec, cells, TracingColumns.DELIVERY_FURTHERTB,
					createCell(r.getValue(LIEFERUNGEN.FURTHER_TRACEBACK)));
			fillCell(spec, cells, TracingColumns.DELIVERY_MICROSAMPLE, createCell(r.getValue(CHARGEN.MICROBIOSAMPLE)));
			fillCell(spec, cells, TracingColumns.FILESOURCES, createCell(r.getValue(LIEFERUNGEN.IMPORTSOURCES)));
			fillCell(spec, cells, TracingColumns.DELIVERY_CHARGENUM,
					set.isAnonymize() ? DataType.getMissingCell() : createCell(r.getValue(CHARGEN.CHARGENNR)));

			// Extras
			for (String extraCol : spec.getColumnNames()) {
				if (extraCol.startsWith("_")) {
					String attribute = extraCol.substring(1);
					int i = attribute.indexOf(".");
					String tn = attribute.substring(0, i);
					String fn = attribute.substring(i + 1);

					Result<Record1<String>> result = DSL.using(conn, SQLDialect.HSQLDB).select(EXTRAFIELDS.VALUE)
							.from(EXTRAFIELDS).where(EXTRAFIELDS.TABLENAME.equal(tn),
									EXTRAFIELDS.ID.equal(r.getValue(LIEFERUNGEN.ID)), EXTRAFIELDS.ATTRIBUTE.equal(fn))
							.fetch();

					if (!result.isEmpty()) {
						fillCell(spec, cells, extraCol, createCell(result.get(0).value1()));
					} else {
						fillCell(spec, cells, extraCol, DataType.getMissingCell());
					}
				}
			}

			DataRow outputRow = new DefaultRow("Row" + index++, cells);

			container.addRowToTable(outputRow);
			exec.checkCanceled();
		}

		container.close();

		return container.getTable();
	}

	private BufferedDataTable getDeliveryConnectionsTable(Connection conn, Map<String, Delivery> deliveries,
			ExecutionContext exec) throws CanceledExecutionException {
		BufferedDataContainer container = exec.createDataContainer(
				new DataTableSpec(new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec(),
						new DataColumnSpecCreator(TracingColumns.NEXT, StringCell.TYPE).createSpec()));
		int index = 0;

		for (Delivery delivery : deliveries.values()) {
			for (String next : delivery.getAllNextIds()) {
				container.addRowToTable(new DefaultRow(index++ + "", createCell(delivery.getId()), createCell(next)));
				exec.checkCanceled();
			}
		}

		container.close();

		return container.getTable();
	}

	private Map<String, Delivery> getDeliveries(Connection conn, Map<Integer, String> stationIds,
			Map<Integer, String> deliveryIds) {
		Map<String, Delivery> allDeliveries = new LinkedHashMap<>();

		Select<Record> deliverySelect = DSL.using(conn, SQLDialect.HSQLDB).select().from(LIEFERUNGEN)
				.leftOuterJoin(CHARGEN).on(LIEFERUNGEN.CHARGE.equal(CHARGEN.ID)).leftOuterJoin(PRODUKTKATALOG)
				.on(CHARGEN.ARTIKEL.equal(PRODUKTKATALOG.ID));

		for (Record r : deliverySelect) {
			Integer id = r.getValue(LIEFERUNGEN.ID);
			Integer from = r.getValue(PRODUKTKATALOG.STATION);
			Integer to = r.getValue(LIEFERUNGEN.EMPFÄNGER);
			boolean invalid = false;

			if (from == null) {
				setWarningMessage("Delivery \"" + id + "\" has no supplier");
				invalid = true;
			}

			if (to == null) {
				setWarningMessage("Delivery \"" + id + "\" has no recipient");
				invalid = true;
			}

			if (invalid) {
				continue;
			}

			String lotNumber = r.getValue(CHARGEN.CHARGENNR) != null ? r.getValue(CHARGEN.CHARGENNR)
					: r.getValue(CHARGEN.ID).toString();
			Double amountInKg1 = getAmountInKg(r.getValue(LIEFERUNGEN.UNITMENGE), r.getValue(LIEFERUNGEN.UNITEINHEIT));
			Double amountInKg2 = getAmountInKg(r.getValue(LIEFERUNGEN.NUMPU), r.getValue(LIEFERUNGEN.TYPEPU));

			if (amountInKg1 != null && amountInKg2 != null && !amountInKg1.equals(amountInKg2)) {
				setWarningMessage("Delivery \"" + id + "\" has two different amounts: " + amountInKg1 + "kg vs. "
						+ amountInKg2 + "kg");
			}

			Delivery d = new Delivery(deliveryIds.get(id), stationIds.get(from), stationIds.get(to),
					r.getValue(LIEFERUNGEN.DD_DAY), r.getValue(LIEFERUNGEN.DD_MONTH), r.getValue(LIEFERUNGEN.DD_YEAR),
					r.getValue(LIEFERUNGEN.AD_DAY), r.getValue(LIEFERUNGEN.AD_MONTH), r.getValue(LIEFERUNGEN.AD_YEAR),
					lotNumber, r.getValue(LIEFERUNGEN.NUMPU), r.getValue(LIEFERUNGEN.TYPEPU),
					amountInKg1 != null ? amountInKg1 : amountInKg2);

			allDeliveries.put(d.getId(), d);
		}

		Select<Record> deliveryToDeliverySelect = DSL.using(conn, SQLDialect.HSQLDB).select().from(CHARGENVERBINDUNGEN)
				.leftOuterJoin(LIEFERUNGEN).on(CHARGENVERBINDUNGEN.PRODUKT.equal(LIEFERUNGEN.CHARGE));

		for (Record r : deliveryToDeliverySelect) {
			Delivery from = allDeliveries.get(deliveryIds.get(r.getValue(CHARGENVERBINDUNGEN.ZUTAT)));
			Delivery to = allDeliveries.get(deliveryIds.get(r.getValue(LIEFERUNGEN.ID)));

			if (from == null || to == null) {
				continue;
			}

			if (from.getId().equals(to.getId())) {
				setWarningMessage("Delivery \"" + from.getId() + "\" cannot be an ingredient of itself");
				continue;
			}

			from.getAllNextIds().add(to.getId());
			to.getAllPreviousIds().add(from.getId());
		}

		return allDeliveries;
	}

	private void checkDates(Map<String, Delivery> deliveries) {
		for (Delivery d : deliveries.values()) {
			for (String nextId : d.getAllNextIds()) {
				Delivery next = deliveries.get(nextId);

				if (!d.isBefore(next)) {
					setWarningMessage("Dates are incorrect for following deliveries: In: \"" + d.getId() + "\" ("
							+ formatDate(d.getArrivalDay(), d.getArrivalMonth(), d.getArrivalYear()) + ") vs. Out: \""
							+ next.getId() + "\" ("
							+ formatDate(next.getDepartureDay(), next.getDepartureMonth(), next.getDepartureYear())
							+ ")");
				}
			}
		}
	}

	private void checkAmounts(Map<String, Delivery> deliveries) {
		Map<String, Set<Delivery>> deliveriesByLot = new LinkedHashMap<>();

		for (Delivery d : deliveries.values()) {
			if (!deliveriesByLot.containsKey(d.getLotNumber())) {
				deliveriesByLot.put(d.getLotNumber(), new LinkedHashSet<Delivery>());
			}

			deliveriesByLot.get(d.getLotNumber()).add(d);
		}

		for (Map.Entry<String, Set<Delivery>> lot : deliveriesByLot.entrySet()) {
			Set<String> ingredients = new LinkedHashSet<>();
			Double kgOut = 0.0;
			Double amountOut = 0.0;
			String unitOut = null;

			for (Delivery d : lot.getValue()) {
				ingredients = d.getAllPreviousIds();

				if (kgOut != null) {
					if (d.getAmountInKg() != null) {
						kgOut += d.getAmountInKg();
					} else {
						kgOut = null;
					}
				}

				if (amountOut != null) {
					if (d.getAmount() != null && d.getUnit() != null
							&& (unitOut == null || unitOut.equals(d.getUnit()))) {
						amountOut += d.getAmount();
						unitOut = d.getUnit();
					} else {
						amountOut = null;
					}
				}
			}

			if (ingredients.isEmpty()) {
				continue;
			}

			Double kgIn = 0.0;
			Double amountIn = 0.0;
			String unitIn = null;

			for (String prev : ingredients) {
				Delivery d = deliveries.get(prev);

				if (kgIn != null) {
					if (d.getAmountInKg() != null) {
						kgIn += d.getAmountInKg();
					} else {
						kgIn = null;
					}
				}

				if (amountIn != null) {
					if (d.getAmount() != null && d.getUnit() != null
							&& (unitIn == null || unitIn.equals(d.getUnit()))) {
						amountIn += d.getAmount();
						unitIn = d.getUnit();
					} else {
						amountIn = null;
					}
				}
			}

			if (amountIn != null && unitIn != null && amountOut != null && unitOut != null && unitIn.equals(unitOut)
					&& areTooDifferent(amountIn, amountOut)) {
				setWarningMessage("Amounts might be incorrect for lot \"" + lot.getKey() + "\": In = " + amountIn + " "
						+ unitIn + " vs. Out = " + amountOut + " " + unitOut);
			} else if (kgIn != null && kgOut != null && areTooDifferent(kgIn, kgOut)) {
				setWarningMessage("Amounts might be incorrect for lot \"" + lot.getKey() + "\": In = " + kgIn + " "
						+ "kg vs. Out = " + kgOut + " kg");
			}
		}
	}

	private static boolean areTooDifferent(double amount1, double amount2) {
		return Math.max(amount1, amount2) > 2.0 * Math.min(amount1, amount2);
	}

	private static Double getAmountInKg(Double value, String unit) {
		if (value == null || unit == null) {
			return null;
		}

		List<String> units = Arrays.asList("kg", "g", "t");
		String unitPart = null;

		for (String part : Splitter.on(" ").split(unit)) {
			if (part.matches(".*\\d.*") || units.contains(part.toLowerCase())) {
				if (unitPart != null) {
					return null;
				}

				unitPart = part;
			}
		}

		if (unitPart == null) {
			return null;
		}

		String numberPart = null;
		Double factor = null;

		if (unitPart.toLowerCase().endsWith("kg")) {
			numberPart = unitPart.substring(0, unitPart.length() - 2);
			factor = 1.0;
		} else if (unitPart.toLowerCase().endsWith("g")) {
			numberPart = unitPart.substring(0, unitPart.length() - 1);
			factor = 0.001;
		} else if (unitPart.toLowerCase().endsWith("t")) {
			numberPart = unitPart.substring(0, unitPart.length() - 1);
			factor = 1000.0;
		}

		try {
			if (numberPart.isEmpty()) {
				return factor * value;
			}

			return Double.parseDouble(numberPart) * factor * value;
		} catch (NullPointerException | NumberFormatException e) {
			return null;
		}
	}

	private static void fillCell(DataTableSpec spec, DataCell[] cells, String columnname, DataCell value) {
		int index = spec.findColumnIndex(columnname);
		if (index >= 0)
			cells[index] = value;
	}

	private static DataCell createCell(String s) {
		return s != null ? new StringCell(clean(s)) : DataType.getMissingCell();
	}

	private static DataCell createCell(Date d) {
		return d != null ? new StringCell(d.toString()) : DataType.getMissingCell();
	}

	private static DataCell createCell(Integer i) {
		return i != null ? new IntCell(i) : DataType.getMissingCell();
	}

	private static DataCell createCell(Double d) {
		return d != null ? new DoubleCell(d) : DataType.getMissingCell();
	}

	private static String clean(String s) {
		if (s == null || s.equalsIgnoreCase("null")) {
			return null;
		}

		return s.replace("\n", "|").replaceAll("\\p{C}", "").replace("\u00A0", "").replace("\t", " ").trim();
	}

	private static String getISO3166_2(String country, String state) {
		for (String code : Locale.getISOCountries()) {
			if (new Locale("", code).getDisplayCountry(Locale.ENGLISH).equals(country)) {
				return code;
			}
		}

		if (state != null && state.length() >= 2) {
			return state.substring(0, 2);
		}

		return "NN";
	}

	private static String formatDate(Integer day, Integer month, Integer year) {
		if (year == null)
			return null;

		String thisYear = new SimpleDateFormat("yyyy").format(new Date());

		if (year.toString().length() == 2)
			year = year > Integer.parseInt(thisYear.substring(2)) ? 1900 : 2000 + year;

		if (month == null) {
			return year.toString();
		} else if (day == null) {
			return year + "-" + new DecimalFormat("00").format(month);
		}

		return year + "-" + new DecimalFormat("00").format(month) + "-" + new DecimalFormat("00").format(day);
	}

	private static boolean containsValues(Connection conn, TableField<?, ?>... fields) {
		for (TableField<?, ?> field : fields) {
			for (Record1<?> r : DSL.using(conn, SQLDialect.HSQLDB).selectDistinct(field).from(field.getTable())) {
				if (r.value1() != null) {
					return true;
				}
			}
		}

		return false;
	}

	private static Connection createLocalConnection(String dbUsername, String dbPassword, String dbFolder)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		MyDBI db = new MyDBTablesNew();
		db.establishNewConnection(dbUsername, dbPassword, dbFolder + File.separator, false);
		db.updateCheck("");

		Connection result = db.getConn();

		result.setReadOnly(true);

		return result;
	}

	private static boolean isStationStart(Map<String, Delivery> deliveries, String id) {
		for (Delivery d : deliveries.values()) {
			if (d.getRecipientId().equals(id)) {
				return false;
			}
		}

		return true;
	}

	private static boolean isSimpleSupplier(Map<String, Delivery> deliveries, String id) {
		if (isStationStart(deliveries, id)) {
			String recId = null;
			for (Delivery d : deliveries.values()) {
				if (d.getSupplierId().equals(id)) {
					if (recId == null)
						recId = d.getRecipientId();
					else if (!recId.equals(d.getRecipientId()))
						return false;
				}
			}
			return true;
		}
		return false;
	}

	private static boolean isStationEnd(Map<String, Delivery> deliveries, String id) {
		for (Delivery d : deliveries.values()) {
			if (d.getSupplierId().equals(id)) {
				return false;
			}
		}

		return true;
	}

	private static boolean isSerialPossible(Connection conn) {
		Set<String> stationSerials = new LinkedHashSet<>();

		for (Record1<String> r : DSL.using(conn, SQLDialect.HSQLDB).select(STATION.SERIAL).from(STATION)) {
			if (r.value1() == null || !stationSerials.add(r.value1())) {
				return false;
			}
		}

		Set<String> deliverySerials = new LinkedHashSet<>();
		boolean alwaysKg = true;

		for (Record2<String, String> r : DSL.using(conn, SQLDialect.HSQLDB)
				.select(LIEFERUNGEN.SERIAL, LIEFERUNGEN.UNITEINHEIT).from(LIEFERUNGEN)) {
			if (r.value1() == null || !deliverySerials.add(r.value1())) {
				return false;
			}

			if (!"kg".equals(r.value2())) {
				alwaysKg = false;
			}
		}

		if (alwaysKg) {
			// beim EFSA Importer wurde immer kg eingetragen, später beim
			// bfrnewimporter wurde nur noch "numPU" und "typePU" benutzt
			// und UnitEinheit müsste immer NULL sein, daher ist das ein
			// sehr gutes Indiz dafür, dass wir es mit alten Daten zu tun
			// haben
			return false;
		}

		return true;
	}

	@Override
	public void warningChanged(String warning) {
		warningsOccured = true;
	}
}
