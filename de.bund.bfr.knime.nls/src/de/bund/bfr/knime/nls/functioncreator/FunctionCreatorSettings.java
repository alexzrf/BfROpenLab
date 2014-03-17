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
package de.bund.bfr.knime.nls.functioncreator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

import de.bund.bfr.knime.nls.NlsNodeSettings;

public class FunctionCreatorSettings extends NlsNodeSettings {

	private static final String CFG_DEPENDENT_VARIABLE = "DependentVariable";
	private static final String CFG_TERM = "Term";
	private static final String CFG_INDEPENDENT_VARIABLES = "IndependentVariables";

	private String dependentVariable;
	private String term;
	private List<String> independentVariables;

	public FunctionCreatorSettings() {
		dependentVariable = null;
		term = null;
		independentVariables = new ArrayList<String>();
	}

	@Override
	public void loadSettings(NodeSettingsRO settings) {
		try {
			dependentVariable = settings.getString(CFG_DEPENDENT_VARIABLE);
		} catch (InvalidSettingsException e) {
		}

		try {
			term = settings.getString(CFG_TERM);
		} catch (InvalidSettingsException e) {
		}

		try {
			independentVariables = new ArrayList<String>(Arrays.asList(settings
					.getStringArray(CFG_INDEPENDENT_VARIABLES)));
		} catch (InvalidSettingsException e) {
		}
	}

	@Override
	public void saveSettings(NodeSettingsWO settings) {
		settings.addString(CFG_DEPENDENT_VARIABLE, dependentVariable);
		settings.addString(CFG_TERM, term);
		settings.addStringArray(CFG_INDEPENDENT_VARIABLES,
				independentVariables.toArray(new String[0]));
	}

	public String getDependentVariable() {
		return dependentVariable;
	}

	public void setDependentVariable(String dependentVariable) {
		this.dependentVariable = dependentVariable;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public List<String> getIndependentVariables() {
		return independentVariables;
	}

	public void setIndependentVariables(List<String> independentVariables) {
		this.independentVariables = independentVariables;
	}
}
