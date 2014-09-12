/*******************************************************************************
 * Copyright (c) 2014 Federal Institute for Risk Assessment (BfR), Germany 
 * 
 * Developers and contributors are 
 * Christian Thoens (BfR)
 * Armin A. Weiser (BfR)
 * Matthias Filter (BfR)
 * Alexander Falenski (BfR)
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
package de.bund.bfr.knime.nls.difffitting;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
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
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.nfunk.jep.ParseException;

import com.google.common.primitives.Doubles;

import de.bund.bfr.knime.IO;
import de.bund.bfr.knime.KnimeUtils;
import de.bund.bfr.knime.nls.Function;
import de.bund.bfr.knime.nls.NlsConstants;
import de.bund.bfr.knime.nls.functionport.FunctionPortObject;
import de.bund.bfr.knime.nls.functionport.FunctionPortObjectSpec;
import de.bund.bfr.math.Integrator;
import de.bund.bfr.math.MathUtils;
import de.bund.bfr.math.ParameterOptimizer;

/**
 * This is the model implementation of DiffFunctionFitting.
 * 
 * 
 * @author Christian Thoens
 */
public class DiffFunctionFittingNodeModel extends NodeModel implements
		ParameterOptimizer.ProgressListener {

	private DiffFunctionFittingSettings set;

	private ExecutionContext currentExec;

	/**
	 * Constructor for the node model.
	 */
	protected DiffFunctionFittingNodeModel() {
		super(new PortType[] { FunctionPortObject.TYPE, BufferedDataTable.TYPE,
				BufferedDataTable.TYPE }, new PortType[] {
				BufferedDataTable.TYPE, BufferedDataTable.TYPE });
		set = new DiffFunctionFittingSettings();
		currentExec = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected PortObject[] execute(PortObject[] inObjects, ExecutionContext exec)
			throws Exception {
		currentExec = exec;

		FunctionPortObject functionObject = (FunctionPortObject) inObjects[0];
		BufferedDataTable dataTable = (BufferedDataTable) inObjects[1];
		BufferedDataTable conditionTable = (BufferedDataTable) inObjects[2];
		Map<String, ParameterOptimizer> values = doEstimation(
				functionObject.getFunction(), dataTable, conditionTable);
		PortObjectSpec[] outSpec = configure(new PortObjectSpec[] {
				functionObject.getSpec(), dataTable.getSpec(),
				conditionTable.getSpec() });
		DataTableSpec outSpec1 = (DataTableSpec) outSpec[0];
		DataTableSpec outSpec2 = (DataTableSpec) outSpec[1];
		BufferedDataContainer container1 = exec.createDataContainer(outSpec1);
		BufferedDataContainer container2 = exec.createDataContainer(outSpec2);
		int i1 = 0;
		int i2 = 0;

		for (String id : values.keySet()) {
			ParameterOptimizer result = values.get(id);
			DataCell[] cells1 = new DataCell[outSpec1.getNumColumns()];

			for (String param1 : functionObject.getFunction().getParameters()) {
				cells1[outSpec1.findColumnIndex(param1)] = IO.createCell(result
						.getParameterValues().get(param1));

				DataCell[] cells2 = new DataCell[outSpec2.getNumColumns()];

				cells2[outSpec2.findColumnIndex(NlsConstants.ID_COLUMN)] = IO
						.createCell(id);
				cells2[outSpec2.findColumnIndex(NlsConstants.PARAM_COLUMN)] = IO
						.createCell(param1);

				for (String param2 : functionObject.getFunction()
						.getParameters()) {
					cells2[outSpec2.findColumnIndex(param2)] = IO
							.createCell(result.getCovariances().get(param1)
									.get(param2));
				}

				container2.addRowToTable(new DefaultRow(i2 + "", cells2));
				i2++;
			}

			cells1[outSpec1.findColumnIndex(NlsConstants.ID_COLUMN)] = IO
					.createCell(id);
			cells1[outSpec1.findColumnIndex(NlsConstants.SSE_COLUMN)] = IO
					.createCell(result.getSSE());
			cells1[outSpec1.findColumnIndex(NlsConstants.MSE_COLUMN)] = IO
					.createCell(result.getMSE());
			cells1[outSpec1.findColumnIndex(NlsConstants.RMSE_COLUMN)] = IO
					.createCell(result.getRMSE());
			cells1[outSpec1.findColumnIndex(NlsConstants.R2_COLUMN)] = IO
					.createCell(result.getR2());
			cells1[outSpec1.findColumnIndex(NlsConstants.AIC_COLUMN)] = IO
					.createCell(result.getAIC());
			cells1[outSpec1.findColumnIndex(NlsConstants.DOF_COLUMN)] = IO
					.createCell(result.getDOF());

			container1.addRowToTable(new DefaultRow(i1 + "", cells1));
			i1++;
		}

		container1.close();
		container2.close();

		return new PortObject[] { container1.getTable(), container2.getTable() };
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
	@SuppressWarnings("unchecked")
	@Override
	protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs)
			throws InvalidSettingsException {
		Function function = ((FunctionPortObjectSpec) inSpecs[0]).getFunction();
		DataTableSpec dataSpec = (DataTableSpec) inSpecs[1];
		DataTableSpec conditionSpec = (DataTableSpec) inSpecs[2];

		List<String> dataStringColumns = KnimeUtils.getColumnNames(KnimeUtils
				.getColumns(dataSpec, StringValue.class));
		List<String> dataDoubleColumns = KnimeUtils.getColumnNames(KnimeUtils
				.getColumns(dataSpec, DoubleValue.class));
		List<String> conditionStringColumns = KnimeUtils
				.getColumnNames(KnimeUtils.getColumns(conditionSpec,
						StringValue.class));
		List<String> conditionDoubleColumns = KnimeUtils
				.getColumnNames(KnimeUtils.getColumns(conditionSpec,
						DoubleValue.class));

		if (!dataStringColumns.contains(NlsConstants.ID_COLUMN)) {
			throw new InvalidSettingsException(
					"Data Table must contain String Column named \""
							+ NlsConstants.ID_COLUMN + "\"");
		}

		if (!dataDoubleColumns.contains(function.getTimeVariable())) {
			throw new InvalidSettingsException(
					"Data Table must contain Double Column named \""
							+ function.getTimeVariable() + "\"");
		}

		if (!dataDoubleColumns.contains(function.getDependentVariable())) {
			throw new InvalidSettingsException(
					"Data Table must contain Double Column named \""
							+ function.getDependentVariable() + "\"");
		}

		if (!conditionStringColumns.contains(NlsConstants.ID_COLUMN)) {
			throw new InvalidSettingsException(
					"Condition Table must contain String Column named \""
							+ NlsConstants.ID_COLUMN + "\"");
		}

		for (String var : function.getVariables()) {
			if (!var.equals(function.getDependentVariable())
					&& !conditionDoubleColumns.contains(var)) {
				throw new InvalidSettingsException(
						"Condition Table must contain Double Column named \""
								+ var + "\"");
			}
		}

		List<DataColumnSpec> specs1 = new ArrayList<>();
		List<DataColumnSpec> specs2 = new ArrayList<>();

		specs1.add(new DataColumnSpecCreator(NlsConstants.ID_COLUMN,
				StringCell.TYPE).createSpec());
		specs2.add(new DataColumnSpecCreator(NlsConstants.ID_COLUMN,
				StringCell.TYPE).createSpec());
		specs2.add(new DataColumnSpecCreator(NlsConstants.PARAM_COLUMN,
				StringCell.TYPE).createSpec());

		for (String param : function.getParameters()) {
			specs1.add(new DataColumnSpecCreator(param, DoubleCell.TYPE)
					.createSpec());
			specs2.add(new DataColumnSpecCreator(param, DoubleCell.TYPE)
					.createSpec());
		}

		specs1.add(new DataColumnSpecCreator(NlsConstants.SSE_COLUMN,
				DoubleCell.TYPE).createSpec());
		specs1.add(new DataColumnSpecCreator(NlsConstants.MSE_COLUMN,
				DoubleCell.TYPE).createSpec());
		specs1.add(new DataColumnSpecCreator(NlsConstants.RMSE_COLUMN,
				DoubleCell.TYPE).createSpec());
		specs1.add(new DataColumnSpecCreator(NlsConstants.R2_COLUMN,
				DoubleCell.TYPE).createSpec());
		specs1.add(new DataColumnSpecCreator(NlsConstants.AIC_COLUMN,
				DoubleCell.TYPE).createSpec());
		specs1.add(new DataColumnSpecCreator(NlsConstants.DOF_COLUMN,
				IntCell.TYPE).createSpec());

		return new PortObjectSpec[] {
				new DataTableSpec(specs1.toArray(new DataColumnSpec[0])),
				new DataTableSpec(specs2.toArray(new DataColumnSpec[0])) };
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

	private Map<String, ParameterOptimizer> doEstimation(Function function,
			BufferedDataTable dataTable, BufferedDataTable conditionTable)
			throws ParseException {
		DataTableSpec dataSpec = dataTable.getSpec();
		DataTableSpec conditionSpec = conditionTable.getSpec();

		Map<String, Double> minParameterValues = new LinkedHashMap<>();
		Map<String, Double> maxParameterValues = new LinkedHashMap<>();

		for (String param : function.getParameters()) {
			Double min = null;
			Double max = null;

			if (set.getParameterGuesses().containsKey(param)) {
				Point2D.Double range = set.getParameterGuesses().get(param);

				if (!Double.isNaN(range.x)) {
					min = range.x;
				}

				if (!Double.isNaN(range.y)) {
					max = range.y;
				}
			}

			minParameterValues.put(param, min);
			maxParameterValues.put(param, max);
		}

		Map<String, List<Double>> timeValues = new LinkedHashMap<>();
		Map<String, List<Double>> targetValues = new LinkedHashMap<>();

		for (DataRow row : dataTable) {
			String id = IO.getString(row.getCell(dataSpec
					.findColumnIndex(NlsConstants.ID_COLUMN)));
			Double time = IO.getDouble(row.getCell(dataSpec
					.findColumnIndex(function.getTimeVariable())));
			Double target = IO.getDouble(row.getCell(dataSpec
					.findColumnIndex(function.getDependentVariable())));

			if (id == null || !MathUtils.isValidDouble(time)
					|| !MathUtils.isValidDouble(target)) {
				continue;
			}

			if (!timeValues.containsKey(id)) {
				timeValues.put(id, new ArrayList<Double>());
				targetValues.put(id, new ArrayList<Double>());
			}

			timeValues.get(id).add(time);
			targetValues.get(id).add(target);
		}

		Map<String, Map<String, List<Double>>> argumentValues = new LinkedHashMap<>();

		for (DataRow row : conditionTable) {
			String id = IO.getString(row.getCell(conditionSpec
					.findColumnIndex(NlsConstants.ID_COLUMN)));
			Map<String, Double> values = new LinkedHashMap<>();

			for (String var : function.getIndependentVariables()) {
				values.put(var, IO.getDouble(row.getCell(conditionSpec
						.findColumnIndex(var))));
			}

			if (id == null || MathUtils.containsInvalidDouble(values.values())) {
				continue;
			}

			if (!argumentValues.containsKey(id)) {
				argumentValues.put(id,
						new LinkedHashMap<String, List<Double>>());

				for (String indep : function.getIndependentVariables()) {
					argumentValues.get(id).put(indep, new ArrayList<Double>());
				}
			}

			for (String indep : function.getIndependentVariables()) {
				argumentValues.get(id).get(indep).add(values.get(indep));
			}
		}

		Map<String, ParameterOptimizer> results = new LinkedHashMap<>();

		for (String id : targetValues.keySet()) {
			if (function.getTimeVariable() == null) {
				continue;
			}

			ParameterOptimizer optimizer;
			double[] timeArray = Doubles.toArray(timeValues.get(id));
			double[] targetArray = Doubles.toArray(targetValues.get(id));
			Map<String, double[]> argumentArrays = new LinkedHashMap<>();

			for (Map.Entry<String, List<Double>> entry : argumentValues.get(id)
					.entrySet()) {
				argumentArrays.put(entry.getKey(),
						Doubles.toArray(entry.getValue()));
			}

			int n = function.getTerms().size();
			String[] terms = new String[n];
			String[] valueVariables = new String[n];
			Double[] initValues = new Double[n];
			String[] initParameters = new String[n];
			int i = 0;

			for (String var : function.getTerms().keySet()) {
				terms[i] = function.getTerms().get(var);
				valueVariables[i] = var;
				initValues[i] = function.getInitValues().get(var);
				initParameters[i] = function.getInitParameters().get(var);
				i++;
			}

			optimizer = new ParameterOptimizer(terms, valueVariables,
					initValues, initParameters, function.getParameters()
							.toArray(new String[0]), minParameterValues,
					maxParameterValues, timeArray, targetArray,
					function.getDependentVariable(),
					function.getTimeVariable(), argumentArrays, new Integrator(
							set.getIntegratorType(), set.getStepSize(),
							set.getMinStepSize(), set.getMaxStepSize(),
							set.getAbsTolerance(), set.getRelTolerance()));
			optimizer.addProgressListener(this);
			optimizer.optimize(set.getnParameterSpace(), set.getnLevenberg(),
					set.isStopWhenSuccessful());
			results.put(id, optimizer);
		}

		return results;
	}

	@Override
	public void progressChanged(double progress) {
		if (currentExec != null) {
			currentExec.setProgress(progress);
		}
	}

}