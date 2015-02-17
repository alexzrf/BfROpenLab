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
package de.bund.bfr.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lsmp.djep.djep.DJep;
import org.nfunk.jep.Node;
import org.nfunk.jep.ParseException;

public class Evaluator {

	private static Map<Function, double[]> results = new LinkedHashMap<>();
	private static Map<ErrorFunction, double[]> errorResults = new LinkedHashMap<>();

	public static double[] getFunctionPoints(
			Map<String, Double> parserConstants, String formula, String varX,
			double[] valuesX) throws ParseException {
		Function function = new Function(parserConstants, formula, varX,
				valuesX);

		if (results.containsKey(function)) {
			return results.get(function);
		}

		DJep parser = MathUtils.createParser();

		for (Map.Entry<String, Double> entry : parserConstants.entrySet()) {
			parser.addConstant(entry.getKey(), entry.getValue());
		}

		parser.addVariable(varX, 0.0);

		Node f = parser.parse(formula);
		double[] valuesY = new double[valuesX.length];
		boolean containsValidPoint = false;

		Arrays.fill(valuesY, Double.NaN);

		for (int i = 0; i < valuesX.length; i++) {
			parser.setVarValue(varX, valuesX[i]);

			Object y = parser.evaluate(f);

			if (MathUtils.isValidDouble(y)) {
				valuesY[i] = (Double) y;
				containsValidPoint = true;
			} else {
				valuesY[i] = Double.NaN;
			}
		}

		if (!containsValidPoint) {
			valuesY = null;
		}

		results.put(function, valuesY);

		return valuesY;
	}

	public static double[] getFunctionErrors(
			Map<String, Double> parserConstants, String formula, String varX,
			double[] valuesX, Map<String, Map<String, Double>> covariances,
			double extraVariance, int degreesOfFreedom) throws ParseException {
		ErrorFunction function = new ErrorFunction(parserConstants, formula,
				varX, valuesX, covariances, extraVariance, degreesOfFreedom);

		if (errorResults.containsKey(function)) {
			return errorResults.get(function);
		}

		DJep parser = MathUtils.createParser();

		for (Map.Entry<String, Double> entry : parserConstants.entrySet()) {
			parser.addConstant(entry.getKey(), entry.getValue());
		}

		parser.addVariable(varX, 0.0);

		List<String> paramList = new ArrayList<>(covariances.keySet());
		Node f = parser.parse(formula);
		Map<String, Node> derivatives = new LinkedHashMap<>();

		for (String param : paramList) {
			derivatives.put(param, parser.differentiate(f, param));
		}

		double[] valuesY = new double[valuesX.length];
		boolean containsValidPoint = false;
		double conf95 = MathUtils.get95PercentConfidence(degreesOfFreedom);

		Arrays.fill(valuesY, Double.NaN);

		loop: for (int n = 0; n < valuesX.length; n++) {
			parser.setVarValue(varX, valuesX[n]);

			double variance = 0.0;

			for (String param : paramList) {
				Object obj = parser.evaluate(derivatives.get(param));

				if (!MathUtils.isValidDouble(obj)) {
					continue loop;
				}

				variance += (Double) obj * (Double) obj
						* covariances.get(param).get(param);
			}

			for (int i = 0; i < paramList.size() - 1; i++) {
				for (int j = i + 1; j < paramList.size(); j++) {
					String param1 = paramList.get(i);
					String param2 = paramList.get(j);
					Object obj1 = parser.evaluate(derivatives.get(param1));
					Object obj2 = parser.evaluate(derivatives.get(param2));

					if (!MathUtils.isValidDouble(obj1)
							|| !MathUtils.isValidDouble(obj2)) {
						continue loop;
					}

					variance += 2.0 * (Double) obj1 * (Double) obj2
							* covariances.get(param1).get(param2);
				}
			}

			double y = Math.sqrt(variance + extraVariance) * conf95;

			if (!MathUtils.isValidDouble(y)) {
				continue loop;
			}

			valuesY[n] = y;
			containsValidPoint = true;
		}

		if (!containsValidPoint) {
			return null;
		}

		errorResults.put(function, valuesY);

		return valuesY;
	}

	private static class Function {

		private Map<String, Double> parserConstants;
		private String formula;
		private String varX;
		private double[] valuesX;

		public Function(Map<String, Double> parserConstants, String formula,
				String varX, double[] valuesX) {
			this.parserConstants = parserConstants;
			this.formula = formula;
			this.varX = varX;
			this.valuesX = valuesX;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;

			result = prime * result + parserConstants.hashCode();
			result = prime * result + formula.hashCode();
			result = prime * result + varX.hashCode();
			result = prime * result + Arrays.hashCode(valuesX);

			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Function)) {
				return false;
			}

			Function f = (Function) obj;

			return parserConstants.equals(f.parserConstants)
					&& formula.equals(f.formula) && varX.equals(f.varX)
					&& Arrays.equals(valuesX, f.valuesX);
		}
	}

	private static class ErrorFunction extends Function {

		private Map<String, Map<String, Double>> covariances;
		private double extraVariance;
		private int degreesOfFreedom;

		public ErrorFunction(Map<String, Double> parserConstants,
				String formula, String varX, double[] valuesX,
				Map<String, Map<String, Double>> covariances,
				double extraVariance, int degreesOfFreedom) {
			super(parserConstants, formula, varX, valuesX);
			this.covariances = covariances;
			this.extraVariance = extraVariance;
			this.degreesOfFreedom = degreesOfFreedom;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			long temp = Double.doubleToLongBits(extraVariance);

			result = prime * result + covariances.hashCode();
			result = prime * result + degreesOfFreedom;
			result = prime * result + (int) (temp ^ (temp >>> 32));

			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ErrorFunction) || !super.equals(obj)) {
				return false;
			}

			ErrorFunction f = (ErrorFunction) obj;

			return covariances.equals(f.covariances)
					&& extraVariance == f.extraVariance
					&& degreesOfFreedom == f.degreesOfFreedom;
		}
	}
}