// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.symbio.store.common.jdbc.yugabyte;

import io.vlingo.symbio.store.DataFormat;
import io.vlingo.symbio.store.common.jdbc.Configuration;
import io.vlingo.symbio.store.common.jdbc.DatabaseType;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

public class YugaByteConfigurationProvider {
    public static final Configuration.ConfigurationInterest interest = new Configuration.ConfigurationInterest() {
        private Configuration configuration;

        @Override public void afterConnect(final Connection connection) { }

        @Override public void beforeConnect(final Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void createDatabase(final Connection connection, final String databaseName) throws Exception {
            try (final Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.executeUpdate("CREATE DATABASE " + databaseName + " WITH OWNER = " + configuration.connectionProvider.username);
                connection.setAutoCommit(false);
            } catch (Exception e) {
                final List<String> message = Arrays.asList(e.getMessage().split(" "));
                if (message.contains("database") && message.contains("already") && message.contains("exists")) return;
                System.out.println("YugaByte database " + databaseName + " could not be created because: " + e.getMessage());

                throw e;
            }
        }

        @Override
        public void dropDatabase(final Connection connection, final String databaseName) throws Exception {
            try (final Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.executeUpdate("DROP DATABASE " + databaseName);
                connection.setAutoCommit(false);
            } catch (Exception e) {
                System.out.println("YugaByte database " + databaseName + " could not be dropped because: " + e.getMessage());
            }
        }
    };

    public static Configuration configuration(
            final DataFormat format,
            final String url,
            final String databaseName,
            final String username,
            final String password,
            final String originatorId,
            final boolean createTables) throws Exception {
        return new Configuration(
                DatabaseType.Postgres,
                interest,
                "org.postgresql.Driver",
                format,
                url,
                databaseName,
                username,
                password,
                false,
                originatorId,
                createTables);
    }

    public static Configuration.TestConfiguration testConfiguration(final DataFormat format) throws Exception {
        return new Configuration.TestConfiguration(
                DatabaseType.Postgres,
                interest,
                "org.postgresql.Driver",
                format,
                "jdbc:postgresql://localhost:5433/",
                "vlingo_test",  // database name
                "postgres",  // username
                "postgres",    // password
                false,          // useSSL
                "TEST",         // originatorId
                true);          // create tables
    }
}
