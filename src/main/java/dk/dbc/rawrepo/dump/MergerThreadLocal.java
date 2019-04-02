/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.rawrepo.dump;

import dk.dbc.jsonb.JSONBException;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.marc.writer.MarcWriterException;
import dk.dbc.rawrepo.dao.RawRepoBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

public class MergerThreadLocal implements Callable<Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergerThreadLocal.class);

    private RawRepoBean bean;
    private HashMap<String, String> recordSet;
    private RecordByteWriter writer;
    private int agencyId;
    private Params params;

    MergerThreadLocal(RawRepoBean bean, HashMap<String, String> recordSet, RecordByteWriter writer, int agencyId, Params params) {
        this.bean = bean;
        this.recordSet = recordSet;
        this.writer = writer;
        this.agencyId = agencyId;
        this.params = params;
    }

    @Override
    public Boolean call() throws Exception {
        try {
            List<String> bibliographicRecordIdList;
            byte[] result;

            bibliographicRecordIdList = new ArrayList<>(recordSet.keySet());

            if (bibliographicRecordIdList.size() > 0) {
                List<RecordItem> recordItemList = bean.getDecodedContent(bibliographicRecordIdList, null, agencyId, params);
                LOGGER.info("Got {} RecordItems", recordItemList.size());
                for (RecordItem item : recordItemList) {
                    if (item != null) {
                        result = item.getLocal();
                        writer.write(result);
                    }
                }
            }

            return true;
        } catch (IOException | MarcReaderException | MarcWriterException | JSONBException ex) {
            LOGGER.error("Caught exception while merging record: ", ex);
        }

        return null;
    }
}
