/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.datasource.ftk;

import gpinf.dev.data.Item;
import gpinf.dev.filetypes.GenericFileType;
import iped3.ICaseData;
import iped3.IItem;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import dpf.sp.gpinf.indexer.datasource.FTK3ReportReader;
import dpf.sp.gpinf.indexer.util.TimeConverter;

/*
 * Classe que obtém informações do banco de dados do FTK4.2+.
 */
public class FTK42Database extends FTKDatabase {

    String schema; // Alterado
    String schemaBase; // Alterado
    String schemaPrefix; // Alterado
    String deletedStr; // Alterado

    protected FTK42Database(Properties properties, String caseName, File report) throws SQLException {

        super(properties, caseName, report);
        schemaBase = schemaVersion;
        setDatabaseParams(serviceName);

    }

    private void setDatabaseParams(String databaseName) throws SQLException {

        if ("oracle".equalsIgnoreCase(databaseType)) { //$NON-NLS-1$
            try {
                Class<?> oracleClass = Class.forName("oracle.jdbc.pool.OracleDataSource"); //$NON-NLS-1$
                DataSource oSource = (DataSource) oracleClass.newInstance();
                oracleClass.getMethod("setUser", String.class).invoke(oSource, user); //$NON-NLS-1$
                oracleClass.getMethod("setPassword", String.class).invoke(oSource, password); //$NON-NLS-1$
                oracleClass.getMethod("setDriverType", String.class).invoke(oSource, driverType); //$NON-NLS-1$
                oracleClass.getMethod("setServiceName", String.class).invoke(oSource, serviceName); //$NON-NLS-1$
                oracleClass.getMethod("setServerName", String.class).invoke(oSource, serverName); //$NON-NLS-1$
                oracleClass.getMethod("setPortNumber", String.class).invoke(oSource, portNumber); //$NON-NLS-1$
                ods = oSource;

            } catch (Exception e) {
                throw new SQLException(e);
            }

            schemaPrefix = schemaBase; // Alterado
            deletedStr = "0"; // Alterado //$NON-NLS-1$

        } else if ("postgreSQL".equalsIgnoreCase(databaseType)) { //$NON-NLS-1$
            org.postgresql.ds.PGSimpleDataSource oSource = new org.postgresql.ds.PGSimpleDataSource();
            oSource.setUser(user);
            oSource.setPassword(password);
            oSource.setServerName(serverName);
            oSource.setPortNumber(portNumber);
            oSource.setDatabaseName(databaseName);

            ods = oSource;

            schemaPrefix = serviceName + "_" + schemaBase; // Alterado //$NON-NLS-1$
            deletedStr = "false"; // Alterado //$NON-NLS-1$

        } else if ("sqlserver".equalsIgnoreCase(databaseType)) { //$NON-NLS-1$
            SQLServerDataSource oSource = new SQLServerDataSource();
            oSource.setUser(user);
            oSource.setPassword(password);
            oSource.setServerName(serverName);
            oSource.setPortNumber(portNumber);
            oSource.setDatabaseName(databaseName);

            ods = oSource;

            schemaPrefix = schemaBase; // Alterado
            deletedStr = "0"; // Alterado //$NON-NLS-1$
        }
    }

    @Override
    protected void loadTableSpace() throws SQLException {

        schema = schemaPrefix + "_" + String.format("%04d", getCaseID(conn)); // Alterado //$NON-NLS-1$ //$NON-NLS-2$

    }

    private int getCaseID(Connection conn) throws SQLException {

        int caseID;
        Statement stmt = conn.createStatement();
        String sql = "select CASEID from " + schemaBase + ".CMN_CASES where CASENAME='" + caso + "'"; // Alterado //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        ResultSet rset;
        try {
            rset = stmt.executeQuery(sql);
        } catch (SQLException e) {
            throw new SQLException("Detected/Configured FTK version may be wrong!"); //$NON-NLS-1$
        }

        // Se não retornou nada no ResultSet, sai
        if (!rset.next()) {
            throw new CaseNameException(
                    "Case name not found into database:" + sql + ". Did you connect to the right database?"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        caseID = rset.getInt(1);

        if (rset.next()) {
            throw new CaseNameException("Case name duplicated into database. Remove or rename the other case."); //$NON-NLS-1$
        }

        // Close the RseultSet
        rset.close();
        rset = null;

        // Close the Statement
        stmt.close();
        stmt = null;

        return caseID;
    }

    private Map<Integer, ArrayList<String>> getFileToBookmarksMap(String objectIDs) throws Exception {

        Statement stmt = conn.createStatement();
        String sql = "select a.OBJECTID, a.BOOKMARKID from " + schema + ".FTK_BOOKMARKOBJECTS a where a.OBJECTID in (" //$NON-NLS-1$ //$NON-NLS-2$
                + objectIDs + ") AND a.ISDELETED = " + deletedStr; // Alterado //$NON-NLS-1$

        ResultSet rset = stmt.executeQuery(sql);
        rset.setFetchSize(1000);

        HashMap<Integer, ArrayList<String>> result = new HashMap<Integer, ArrayList<String>>();

        while (rset.next()) {
            int fileId = rset.getInt("OBJECTID"); //$NON-NLS-1$
            ArrayList<String> bookmarkNames = result.get(fileId);
            if (bookmarkNames == null) {
                bookmarkNames = new ArrayList<String>();
            }
            String bookmark = bookmarksMap.get(rset.getString("BOOKMARKID")); //$NON-NLS-1$
            if (bookmark != null) {
                bookmarkNames.add(bookmark);
            }
            result.put(fileId, bookmarkNames);
        }

        return result;
    }

    @Override
    protected void addFileListToCaseData(ICaseData caseData, Map<Integer, ArrayList<String>> fileList)
            throws Exception {
        StringBuffer fileIds = new StringBuffer();
        int i = 1;
        for (Integer ID : fileList.keySet()) {
            fileIds.append(ID);
            if (i++ < fileList.size()) {
                fileIds.append(","); //$NON-NLS-1$
            }
        }
        String objectIDs = fileIds.toString();

        // obtém nomes dos bookmarks de cada item
        Map<Integer, ArrayList<String>> fileToBookmarkMap = getFileToBookmarksMap(objectIDs);

        // Create a Statement
        Statement stmt = conn.createStatement();
        String sql = "select a.objectid, c.parentid, c.objectname, b.md5, a.filecategory, a.filepath, a.isdeleted, a.isfromfreespace, a.logicalsize, a.creationdateft, a.modificationdateft, a.accessdateft, a.fataccessdate from " //$NON-NLS-1$
                + schema + ".CMN_OBJECTFILES a INNER JOIN " + schema //$NON-NLS-1$
                + ".CMN_OBJECTS c ON c.objectid = a.objectid LEFT OUTER JOIN " + schema //$NON-NLS-1$
                + ".CMN_OBJECTHASHES b on b.objectid = a.objectid where a.objectid in (" + objectIDs + ")"; // ALTERADO //$NON-NLS-1$ //$NON-NLS-2$

        ResultSet rset;
        String PATH_COL_NAME = "filepath"; //$NON-NLS-1$
        try {
            rset = stmt.executeQuery(sql);

            // Tratamento p/ FTK 5.6
        } catch (SQLException e) {
            sql = "select a.objectid, c.parentid, c.objectname, b.md5, c.filecategory, c.objectpath, a.isdeleted, a.isfromfreespace, a.logicalsize, a.creationdateft, a.modificationdateft, a.accessdateft, a.fataccessdate from " //$NON-NLS-1$
                    + schema + ".CMN_OBJECTFILES a INNER JOIN " + schema //$NON-NLS-1$
                    + ".CMN_OBJECTS c ON c.objectid = a.objectid LEFT OUTER JOIN " + schema //$NON-NLS-1$
                    + ".CMN_OBJECTHASHES b on b.objectid = a.objectid where a.objectid in (" + objectIDs + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            PATH_COL_NAME = "objectpath"; //$NON-NLS-1$
            try {
                rset = stmt.executeQuery(sql);

                // FTK 6.0
            } catch (SQLException e2) {
                sql = "select a.objectid, c.parentid, c.objectname, b.md5, c.filecategory, c.objectpath, a.isdeleted, a.isfromfreespace, c.logicalsize, a.creationdateft, a.modificationdateft, a.accessdateft, a.fataccessdate from " //$NON-NLS-1$
                        + schema + ".CMN_OBJECTFILES a INNER JOIN " + schema //$NON-NLS-1$
                        + ".CMN_OBJECTS c ON c.objectid = a.objectid LEFT OUTER JOIN " + schema //$NON-NLS-1$
                        + ".CMN_OBJECTHASHES b on b.objectid = a.objectid where a.objectid in (" + objectIDs + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                PATH_COL_NAME = "objectpath"; //$NON-NLS-1$
                rset = stmt.executeQuery(sql);
            }

        }

        rset.setFetchSize(1000);

        int addedEvidences = 0;
        while (rset.next()) {
            ArrayList<String> paths = fileList.get(rset.getInt("OBJECTID")); //$NON-NLS-1$
            for (String path : paths) {
                IItem item = new Item();
                item.setDataSource(ipedDataSource);
                int ftkId = rset.getInt("OBJECTID"); //$NON-NLS-1$
                item.setFtkID(ftkId);
                int parentId = rset.getInt("PARENTID"); //$NON-NLS-1$
                item.setParentId(parentId);
                item.setName(rset.getString("OBJECTNAME")); //$NON-NLS-1$
                item.setExportedFile(path);
                item.setPath(rset.getString(PATH_COL_NAME)); // Alterado
                if (rset.getBoolean("ISDELETED") || rset.getBoolean("ISFROMFREESPACE")) { //$NON-NLS-1$ //$NON-NLS-2$
                    item.setDeleted(true);
                }
                String hash = rset.getString("md5"); //$NON-NLS-1$
                if (hash != null) {
                    item.setHash(hash);
                }
                long logicalSize = rset.getLong("LOGICALSIZE"); //$NON-NLS-1$
                if (logicalSize > -1) {
                    item.setLength(logicalSize);
                }
                String fileType = FTK42FileTypes.getTypeDesc(rset.getInt("FILECATEGORY")); //$NON-NLS-1$
                if (fileType != null) {
                    item.setType(new GenericFileType(fileType)); // Alterado
                }
                long createdDate = rset.getLong("CREATIONDATEFT"); // Alterado //$NON-NLS-1$
                if (createdDate > 0) {
                    item.setCreationDate(TimeConverter.fileTimeToDate(createdDate));
                }
                long modifiedDate = rset.getLong("MODIFICATIONDATEFT"); // Alterado //$NON-NLS-1$
                if (modifiedDate > 0) {
                    item.setModificationDate(TimeConverter.fileTimeToDate(modifiedDate));
                }
                long accessedDate = rset.getLong("ACCESSDATEFT"); // Alterado //$NON-NLS-1$
                if (accessedDate > 0) {
                    item.setAccessDate(TimeConverter.fileTimeToDate(accessedDate));
                } else {
                    String fatDate = rset.getString("FATACCESSDATE"); // Alterado //$NON-NLS-1$
                    if (fatDate != null) {
                        Calendar calendar = new GregorianCalendar();
                        calendar.clear();
                        calendar.set(Integer.parseInt(fatDate.substring(0, 4)),
                                Integer.parseInt(fatDate.substring(5, 7)) - 1,
                                Integer.parseInt(fatDate.substring(8, 10)));
                        item.setAccessDate(calendar.getTime());
                    }
                }

                ArrayList<String> bookmarks = fileToBookmarkMap.get(rset.getInt("OBJECTID")); //$NON-NLS-1$
                if (bookmarks != null) {
                    for (String bookmarkName : bookmarks) {
                        item.addCategory(bookmarkName);
                    }
                }

                caseData.addItem(item);
            }

            addedEvidences++;
        }
        // Close the ResultSet
        rset.close();
        rset = null;
        // Close the Statement
        stmt.close();
        stmt = null;

        if (fileList.size() != addedEvidences) {
            throw new Exception("Found only " + addedEvidences + " of " + fileList.size() //$NON-NLS-1$ //$NON-NLS-2$
                    + " item ID's in databse. The case name may be wrong!"); //$NON-NLS-1$
        }
    }

    @Override
    protected Map<String, String> getBookmarksMap(File report) throws Exception {

        HashSet<String> bookmarks = FTK3ReportReader.getBookmarks(report);
        HashMap<String, String> result = new HashMap<String, String>();

        Statement stmt = conn.createStatement();
        String sql;
        ResultSet rset = null;

        sql = "select a.BOOKMARKID, a.BOOKMARKNAME from " + schema + ".FTK_BOOKMARKS a"; //$NON-NLS-1$ //$NON-NLS-2$

        try {
            rset = stmt.executeQuery(sql);

        } catch (Exception e) {

            String str = (schema + ".FTK_BOOKMARKS").toLowerCase(); //$NON-NLS-1$
            if (e.toString().toLowerCase().contains(str)) {
                stmt.close();
                conn.close();
                setDatabaseParams("case_" + schema.toLowerCase()); //$NON-NLS-1$
                conn = ods.getConnection();
                stmt = conn.createStatement();
                rset = stmt.executeQuery(sql);
            }
        }

        rset.setFetchSize(1000);

        while (rset.next()) {
            String bookName = rset.getString(2);
            if (bookmarks.contains(bookName)) {
                result.put(rset.getString(1), bookName);
            }
        }

        // Close the ResultSet
        rset.close();
        rset = null;

        // Close the Statement
        stmt.close();
        stmt = null;

        return result;
    }

}
