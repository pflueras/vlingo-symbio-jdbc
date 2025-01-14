// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.symbio.store.object.jdbc;

import java.sql.Connection;
import java.text.MessageFormat;

/**
 * A {@code JDBCObjectStoreEntryJournalQueries} for YugaByte.
 */
public class YugaByteObjectStoreEntryJournalQueries extends JDBCObjectStoreEntryJournalQueries {
    /**
     * Construct my state.
     * @param connection the Connection
     */
    public YugaByteObjectStoreEntryJournalQueries(final Connection connection) {
        super(connection);
    }

    /*
     * @see io.vlingo.symbio.store.object.jdbc.JDBCObjectStoreEntryJournalQueries#upsertCurrentEntryOffsetQuery(java.lang.String[])
     */
    @Override
    public String upsertCurrentEntryOffsetQuery(final String[] placeholders) {
        return MessageFormat.format(
                "INSERT INTO {0}(O_READER_NAME, O_READER_OFFSET) VALUES({1}, {2}) " +
                        "ON CONFLICT (O_READER_NAME) DO UPDATE SET O_READER_OFFSET={2}",
                EntryReaderOffsetsTableName,
                placeholders[0],
                placeholders[1]);
    }

    /*
     * @see io.vlingo.symbio.store.object.jdbc.JDBCObjectStoreEntryJournalQueries#wideTextDataType()
     */
    @Override
    public String wideTextDataType() {
        return "TEXT";
    }
}
