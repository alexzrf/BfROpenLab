/*******************************************************************************
 * Copyright (c) 2014 Federal Institute for Risk Assessment (BfR), Germany 
 * 
 * Developers and contributors are
 * Armin A. Weiser (BfR) 
 * Christian Thoens (BfR)
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
package de.bund.bfr.knime.openkrise.util.cluster;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.clustering.MultiKMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelDouble;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

import de.bund.bfr.knime.gis.geocode.GeocodingNodeModel;

/**
 * This is the model implementation of DBSCAN.
 * 
 *
 * @author BfR
 */
public class DBSCANNodeModel extends NodeModel {
    
    static final String MINPTS = "minPts";
    static final String EPS = "eps";
    static final String DOUBLETTES = "doublettes";
    static final String CHOSENMODEL = "chosenmodel";

    private final SettingsModelInteger m_minPts = new SettingsModelInteger(MINPTS, 2);
    private final SettingsModelDouble m_eps = new SettingsModelDouble(EPS, 2.0);
    private final SettingsModelBoolean m_doublettes = new SettingsModelBoolean(DOUBLETTES, false);
    private final SettingsModelString m_chosenModel = new SettingsModelString(CHOSENMODEL, "DBSCAN");
    /**
     * Constructor for the node model.
     */
    public DBSCANNodeModel() {
        super(1, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {
    	BufferedDataTable data = inData[0];
    	BufferedDataContainer buf = exec.createDataContainer(createSpec(data.getSpec()));
    	
    	int latCol = -1;
    	int lonCol = -1;
    	int doItCol = -1;
    	for (int i=0;i<data.getSpec().getNumColumns();i++) {
    		if (data.getSpec().getColumnNames()[i].equals(GeocodingNodeModel.LATITUDE_COLUMN)) latCol = i;
    		else if (data.getSpec().getColumnNames()[i].equals(GeocodingNodeModel.LONGITUDE_COLUMN)) lonCol = i;
    		else if (data.getSpec().getColumnNames()[i].equals("Clusterable")) doItCol = i;
    	}
    	if (latCol >= 0 && lonCol >= 0) {
    		HashMap<Integer, DoublePoint> idp = new HashMap<>(); 
    		HashMap<Double, Integer> dim = new HashMap<>();
    	    List<DoublePoint> points = new ArrayList<>();
            int rowNumber = 0;
        	for (DataRow row : data) {
            	exec.checkCanceled();
            	if (doItCol < 0 || !row.getCell(doItCol).isMissing() && ((BooleanCell) row.getCell(doItCol)).getBooleanValue()) {
            		if (!row.getCell(latCol).isMissing() && !row.getCell(lonCol).isMissing()) {
    	        		DoubleCell latCell = (DoubleCell) row.getCell(latCol);
    	        		DoubleCell lonCell = (DoubleCell) row.getCell(lonCol);
            	        double[] d = new double[2];
            	        d[0] = latCell.getDoubleValue();
            	        d[1] = lonCell.getDoubleValue();
            	        d[0] *= 111.32; // 1� of latitude is ca. 111 km
            	        d[1] *= Math.cos(d[1]*Math.PI/180) * 111.32;
    	        		Double dblKey = d[0] * 100000 + d[1];
    	        		if (!m_doublettes.getBooleanValue() || !dim.containsKey(dblKey.doubleValue())) {
    	        	        DoublePoint dp = new DoublePoint(d);
    	        	        idp.put(rowNumber, dp);
    	        	        points.add(dp);
    	        	        dim.put(dblKey, rowNumber);
    	        		}
    	        		else {
    	        			idp.put(rowNumber, idp.get(dim.get(dblKey)));
    	        		}
            		}
            	}
                rowNumber++;
        	}
        	List<?> cluster = null;
    		if (m_chosenModel.getStringValue().equals("DBSCAN")) {
    			cluster = dbScan(points);
    		}
    		else if (m_chosenModel.getStringValue().equals("KMeans")) {
    			cluster = kMeans(points);
    		}
        	
            rowNumber = 0;
            for (DataRow row : data) {
            	exec.checkCanceled();
                RowKey key = RowKey.createRowKey(rowNumber);
                DataCell[] cells = new DataCell[data.getSpec().getNumColumns() + 1];
                int i = 0;
                for (;i < row.getNumCells(); i++) {
                    DataCell cell = row.getCell(i);
                    cells[i] = cell;
                }
                cells[i] = DataType.getMissingCell();
                for (int j=0;j<cluster.size();j++) {
                	Object o = cluster.get(j);
                	@SuppressWarnings("unchecked")
					Cluster<DoublePoint> c = (Cluster<DoublePoint>) o;
                	if (c.getPoints().contains(idp.get(rowNumber))) {
                        cells[i] = new IntCell(j);
                        break;
                	}
                }                
                DataRow outputRow = new DefaultRow(key, cells);
                buf.addRowToTable(outputRow);
                rowNumber++;
            }

            buf.close();
            return new BufferedDataTable[]{buf.getTable()};
    	}
    	
    	return new BufferedDataTable[]{null};    	
    }
    private DataTableSpec createSpec(DataTableSpec inSpec) {
        DataColumnSpec[] spec = new DataColumnSpec[inSpec.getNumColumns() + 1];
        for (int i=0;i<inSpec.getNumColumns();i++) {
            spec[i] = inSpec.getColumnSpec(i);
        }
        spec[inSpec.getNumColumns()] = new DataColumnSpecCreator("ClusterID",IntCell.TYPE).createSpec();
        return new DataTableSpec(spec);
       }

	private List<CentroidCluster<DoublePoint>> kMeans(List<DoublePoint> points) {
	    KMeansPlusPlusClusterer<DoublePoint> km = new KMeansPlusPlusClusterer<>(m_minPts.getIntValue());
	    MultiKMeansPlusPlusClusterer<DoublePoint> mkm = new MultiKMeansPlusPlusClusterer<>(km, 5);
	    List<CentroidCluster<DoublePoint>> cluster = mkm.cluster(points);
	    return cluster;
	}
	private List<Cluster<DoublePoint>> dbScan(List<DoublePoint> points) {
	    DBSCANClusterer<DoublePoint> dbscan = new DBSCANClusterer<>(m_eps.getDoubleValue(), m_minPts.getIntValue());
	    List<Cluster<DoublePoint>> cluster = dbscan.cluster(points);
	    return cluster;
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
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {

        return new DataTableSpec[]{createSpec(inSpecs[0])};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
    	m_eps.saveSettingsTo(settings);
    	m_minPts.saveSettingsTo(settings);
    	m_doublettes.saveSettingsTo(settings);
    	m_chosenModel.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	m_eps.loadSettingsFrom(settings);
    	m_minPts.loadSettingsFrom(settings);
    	m_doublettes.loadSettingsFrom(settings);
    	m_chosenModel.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	m_eps.validateSettings(settings);
    	m_minPts.validateSettings(settings);
    	m_doublettes.validateSettings(settings);
    	m_chosenModel.validateSettings(settings);
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

}
