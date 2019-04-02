/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.rawrepo.dao;

import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.dump.Params;
import dk.dbc.rawrepo.dump.RecordItem;
import dk.dbc.rawrepo.dump.RecordStatus;
import dk.dbc.util.StopwatchInterceptor;
import dk.dbc.util.Timed;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.interceptor.Interceptors;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Interceptors(StopwatchInterceptor.class)
@Stateless
public class RawRepoBean {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(RawRepoBean.class);

    private static final String QUERY_BIBLIOGRAPHICRECORDID_BY_AGENCY_ALL = "SELECT bibliographicrecordid, mimetype FROM records WHERE agencyid=?";
    private static final String QUERY_AGENCIES = "SELECT DISTINCT(agencyid) FROM records";
    private static final String SET_SERVER_URL_CONFIGURATION = "INSERT INTO configurations (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING";

    @Resource(lookup = "jdbc/rawrepo")
    private DataSource dataSource;

    @Timed
    public HashMap<String, String> getBibliographicRecordIdForAgency(int agencyId, RecordStatus recordStatus) throws RawRepoException {
        try {
            HashMap<String, String> ret = new HashMap<>();

            String query = QUERY_BIBLIOGRAPHICRECORDID_BY_AGENCY_ALL;

            if (recordStatus == RecordStatus.DELETED) {
                query += " AND deleted = 't'";
            }

            if (recordStatus == RecordStatus.ACTIVE) {
                query += " AND deleted = 'f'";
            }

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, agencyId);
                try (ResultSet resultSet = stmt.executeQuery()) {
                    while (resultSet.next()) {
                        String bibliographicRecordId = resultSet.getString(1);
                        String mimeType = resultSet.getString(2);

                        ret.put(bibliographicRecordId, mimeType);
                    }
                }
            }

            return ret;
        } catch (SQLException ex) {
            throw new RawRepoException("Error getting bibliographicrecordids", ex);
        }
    }

    @Timed
    public HashMap<String, String> getBibliographicRecordIdForAgencyInterval(int agencyId, RecordStatus recordStatus, String createdBefore, String createdAfter, String modifiedBefore, String modifiedAfter) throws RawRepoException {
        try {
            HashMap<String, String> ret = new HashMap<>();

            String query = QUERY_BIBLIOGRAPHICRECORDID_BY_AGENCY_ALL;

            if (recordStatus == RecordStatus.DELETED) {
                query += " AND deleted = 't'";
            }

            if (recordStatus == RecordStatus.ACTIVE) {
                query += " AND deleted = 'f'";
            }

            if (hasValue(createdBefore)) {
                query += " AND created < ? ::timestamp AT TIME ZONE 'CET'";
            }

            if (hasValue(createdAfter)) {
                query += " AND created >= ? ::timestamp AT TIME ZONE 'CET'";
            }

            if (hasValue(modifiedBefore)) {
                query += " AND modified < ? ::timestamp AT TIME ZONE 'CET'";
            }

            if (hasValue(modifiedAfter)) {
                query += " AND modified >= ? ::timestamp AT TIME ZONE 'CET'";
            }

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                int i = 0;
                stmt.setInt(++i, agencyId);
                if (hasValue(createdBefore))
                    stmt.setTimestamp(++i, Timestamp.valueOf(createdBefore));
                if (hasValue(createdAfter))
                    stmt.setTimestamp(++i, Timestamp.valueOf(createdAfter));
                if (hasValue(modifiedBefore))
                    stmt.setTimestamp(++i, Timestamp.valueOf(modifiedBefore));
                if (hasValue(modifiedAfter))
                    stmt.setTimestamp(++i, Timestamp.valueOf(modifiedAfter));
                try (ResultSet resultSet = stmt.executeQuery()) {
                    while (resultSet.next()) {
                        String bibliographicRecordId = resultSet.getString(1);
                        String mimeType = resultSet.getString(2);

                        ret.put(bibliographicRecordId, mimeType);
                    }
                }
            }

            return ret;
        } catch (SQLException ex) {
            throw new RawRepoException("Error getting bibliographicrecordids", ex);
        }
    }

    @Timed
    public List<Integer> getAgencies() throws RawRepoException {
        try {
            ArrayList<Integer> ret = new ArrayList<>();

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(QUERY_AGENCIES)) {
                try (ResultSet resultSet = stmt.executeQuery()) {
                    while (resultSet.next()) {
                        int agencyId = resultSet.getInt(1);
                        ret.add(agencyId);
                    }
                }
            }

            return ret;
        } catch (SQLException ex) {
            throw new RawRepoException("Error getting agencies", ex);
        }
    }

    public void setConfigurations(String key, String value) throws RawRepoException {
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(SET_SERVER_URL_CONFIGURATION)) {
                stmt.setString(1, key);
                stmt.setString(2, value);
                stmt.execute();
            }
        } catch (SQLException ex) {
            LOGGER.info("Caught exception: {}", ex);
            throw new RawRepoException("Error updating configurations", ex);
        }
    }

    public Set<String> getRawrepoRecordsIdsWithHoldings(Set<String> bibliographicRecordIds, int agencyId) throws RawRepoException {
        Set<String> res = new HashSet<>();

        final int sliceSize = 500;
        int index = 0;

        LOGGER.info("Checking a total of {} holdingsitems in rawrepo", bibliographicRecordIds.size());

        while (index < bibliographicRecordIds.size()) {
            LOGGER.info("Checking holdings slice: {} to {}", index, index + sliceSize);
            Set<String> slice = bibliographicRecordIds.stream()
                    .skip(index)
                    .limit(sliceSize)
                    .collect(Collectors.toSet());

            List<String> placeHolders = new ArrayList<>();
            for (int i = 0; i < slice.size(); i++) {
                placeHolders.add("?");
            }

            String query = "SELECT bibliographicrecordid" +
                    "         FROM records " +
                    "        WHERE bibliographicrecordid IN (" + String.join(",", placeHolders) + ")" +
                    "          AND agencyid in (870970, ?)";

            int pos = 1;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {

                for (String bibliographicRecordId : slice) {
                    preparedStatement.setString(pos++, bibliographicRecordId);
                }

                preparedStatement.setInt(pos++, agencyId);

                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    res.add(resultSet.getString(1));
                }
            } catch (SQLException ex) {
                LOGGER.info("Caught exception: {}", ex);
                throw new RawRepoException("Error during getBibliographicRecordIdsForEnrichmentAgency", ex);
            }
            index += sliceSize;
        }

        LOGGER.info("Found {} holdings item records in rawrepo", res.size());

        return res;
    }

    public List<RecordItem> getDecodedContent(List<String> bibliographicRecordIds, Integer commonAgencyId, Integer localAgencyId, Params params) throws RawRepoException {
        List<RecordItem> res = new ArrayList<>();
        List<String> placeHolders = new ArrayList<>();
        for (int i = 0; i < bibliographicRecordIds.size(); i++) {
            placeHolders.add("?");
        }

        String query;

        // Local record
        if (commonAgencyId == null) {
            query = " SELECT local.bibliographicrecordid, " +
                    "        null, " +
                    "        convert_from(decode(local.content, 'base64'), 'UTF-8')" +
                    "   FROM records as local" +
                    "  WHERE local.agencyid=?";
        } else { // Enrichment record
            query = " SELECT common.bibliographicrecordid, " +
                    "        convert_from(decode(common.content, 'base64'), 'UTF-8')," +
                    "        convert_from(decode(local.content, 'base64'), 'UTF-8')" +
                    "   FROM records as common, records as local" +
                    "  WHERE common.agencyid=?" +
                    "    AND local.agencyid=?" +
                    "    AND common.bibliographicrecordid = local.bibliographicrecordid";
        }

        query += "       AND local.bibliographicrecordid in (" + String.join(",", placeHolders) + ")";

        int pos = 1;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            if (commonAgencyId != null) {
                preparedStatement.setInt(pos++, commonAgencyId);
            }
            preparedStatement.setInt(pos++, localAgencyId);

            for (String bibliographicRecordId : bibliographicRecordIds) {
                preparedStatement.setString(pos++, bibliographicRecordId);
            }

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                res.add(new RecordItem(resultSet.getString(1), resultSet.getBytes(2), resultSet.getBytes(3)));
            }
        } catch (SQLException ex) {
            LOGGER.info("Caught exception: {}", ex);
            throw new RawRepoException("Error during getBibliographicRecordIdsForEnrichmentAgency", ex);
        }

        return res;
    }

    private boolean hasValue(String s) {
        return !(s == null || s.isEmpty());
    }

}
