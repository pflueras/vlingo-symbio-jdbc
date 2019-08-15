// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.symbio.store.object.jdbc.jpa;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;

import io.vlingo.actors.World;
import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.common.Outcome;
import io.vlingo.symbio.Entry;
import io.vlingo.symbio.EntryAdapterProvider;
import io.vlingo.symbio.State;
import io.vlingo.symbio.StateAdapterProvider;
import io.vlingo.symbio.store.DataFormat;
import io.vlingo.symbio.store.Result;
import io.vlingo.symbio.store.StorageException;
import io.vlingo.symbio.store.common.MockDispatcher;
import io.vlingo.symbio.store.common.jdbc.Configuration;
import io.vlingo.symbio.store.common.jdbc.ConnectionProvider;
import io.vlingo.symbio.store.common.jdbc.postgres.PostgresConfigurationProvider;
import io.vlingo.symbio.store.object.ObjectStoreReader.QueryMultiResults;
import io.vlingo.symbio.store.object.ObjectStoreReader.QueryResultInterest;
import io.vlingo.symbio.store.object.ObjectStoreReader.QuerySingleResult;
import io.vlingo.symbio.store.object.ObjectStoreWriter.PersistResultInterest;
import io.vlingo.symbio.store.object.jdbc.JDBCObjectStoreEntryJournalQueries;
import io.vlingo.symbio.store.object.jdbc.PostgresObjectStoreEntryJournalQueries;
import io.vlingo.symbio.store.object.jdbc.jpa.PersonEntryAdapters.PersonAddedAdapter;
import io.vlingo.symbio.store.object.jdbc.jpa.PersonEntryAdapters.PersonRenamedAdapter;
import io.vlingo.symbio.store.object.jdbc.jpa.PersonEvents.PersonAdded;
import io.vlingo.symbio.store.object.jdbc.jpa.PersonEvents.PersonRenamed;

public abstract class JPAObjectStoreTest {
  protected Configuration adminConfiguration;
  protected ConnectionProvider connectionProvider;
  protected JPAObjectStoreDelegate delegate;
  protected MockDispatcher<Entry<String>, State<String>> dispatcher;
  protected JPAObjectStore objectStore;
  protected JDBCObjectStoreEntryJournalQueries queries;
  protected String testDatabaseName;
  protected World world;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    world = World.startWithDefaults("jpa-test");
    EntryAdapterProvider.instance(world).registerAdapter(PersonAdded.class, new PersonAddedAdapter());
    EntryAdapterProvider.instance(world).registerAdapter(PersonRenamed.class, new PersonRenamedAdapter());
    final StateAdapterProvider stateAdapterProvider = StateAdapterProvider.instance(world);
    dispatcher = new MockDispatcher<>();
    adminConfiguration = PostgresConfigurationProvider.testConfiguration(DataFormat.Text);
    testDatabaseName = testDatabaseName();
    dropTestDatabase();
    createTestDatabase();
    final Map<String,Object> properties = testDatabaseProperties(testDatabaseName);
    delegate = new JPAObjectStoreDelegate(JPAObjectStoreDelegate.JPA_POSTGRES_PERSISTENCE_UNIT, properties, "TEST", stateAdapterProvider, world.defaultLogger());
//    delegate = new JPAObjectStoreDelegate(JPAObjectStoreDelegate.JPA_HSQLDB_PERSISTENCE_UNIT, "TEST", stateAdapterProvider, world.defaultLogger());
    connectionProvider = new ConnectionProvider("org.postgresql.Driver", "jdbc:postgresql://localhost/", testDatabaseName, "vlingo_test", "vlingo123", false);
//    connectionProvider = new ConnectionProvider("org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:mem", "test", "SA", "", false);
    queries = new PostgresObjectStoreEntryJournalQueries(connectionProvider.connection());
//    queries = new HSQLDBObjectStoreEntryJournalQueries(connectionProvider.connection());
    objectStore = world.actorFor(JPAObjectStore.class, JPAObjectStoreActor.class, delegate, connectionProvider, dispatcher);
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
    objectStore.close();
    world.terminate();
    dropTestDatabase();
  }

  private void createTestDatabase() throws Exception {
    PostgresConfigurationProvider.interest.createDatabase(adminConfiguration.connection, testDatabaseName);
  }

  private void dropTestDatabase() throws Exception {
    PostgresConfigurationProvider.interest.dropDatabase(adminConfiguration.connection, testDatabaseName);
  }

  private static final AtomicInteger databaseNamePostfix = new AtomicInteger(Short.MAX_VALUE);

  protected String testDatabaseName() {
    final int postfix = databaseNamePostfix.incrementAndGet();
    return "vlingo_test_" + Math.abs(postfix);
  }

  protected Map<String,Object> testDatabaseProperties(final String databaseNamePostfix) {
    final Map<String,Object> properties = new HashMap<>();

    properties.put("javax.persistence.jdbc.driver", "org.postgresql.Driver");
    properties.put("javax.persistence.jdbc.url", "jdbc:postgresql://localhost/" + databaseNamePostfix);
    properties.put("javax.persistence.jdbc.user", "vlingo_test");
    properties.put("javax.persistence.jdbc.password", "vlingo123");
    properties.put("javax.persistence.LockModeType", "OPTIMISTIC_FORCE_INCREMENT");
    properties.put("javax.persistence.schema-generation.database.action", "drop-and-create");
    properties.put("javax.persistence.schema-generation.create-database-schemas", "drop-and-create");
    properties.put("javax.persistence.schema-generation.scripts.action", "drop-and-create");
    properties.put("javax.persistence.schema-generation.scripts.create-target", "./target/createDDL.jdbc");
    properties.put("javax.persistence.schema-generation.scripts.drop-target", "./target/dropDDL.jdbc");
    properties.put("javax.persistence.schema-generation.create-source", "metadata");
    properties.put("javax.persistence.schema-generation.drop-source", "metadata");
    properties.put("javax.persistence.schema-generation.create-script-source", "./target/createDDL.jdbc");
    properties.put("javax.persistence.schema-generation.drop-script-source", "./target/dropDDL.jdbc");

    return properties;
  }

  public static class TestQueryResultInterest implements QueryResultInterest {
    public AtomicReference<QueryMultiResults> multiResults = new AtomicReference<>();
    public AtomicReference<QuerySingleResult> singleResult = new AtomicReference<>();
    private AccessSafely access = AccessSafely.afterCompleting(1);

    @Override
    public void queryAllResultedIn(final Outcome<StorageException, Result> outcome, final QueryMultiResults results, final Object object) {
      access.writeUsing( "addAll", results );
    }

    @Override
    public void queryObjectResultedIn(final Outcome<StorageException, Result> outcome, final QuerySingleResult result, final Object object) {
      access.writeUsing( "add", result );
    }

    public AccessSafely afterCompleting( final int times ) {
      access =
        AccessSafely
          .afterCompleting( times )
          .writingWith("add", (value)-> singleResult.set((QuerySingleResult)value))
          .writingWith("addAll", (values) -> multiResults.set((QueryMultiResults)values))
          .readingWith("singleResult", () -> singleResult.get())
          .readingWith("singleResultValue", () -> singleResult.get().persistentObject)
          .readingWith("multiResults", () -> multiResults.get())
          .readingWith("multiResultsValue", () -> multiResults.get().persistentObjects)
          .readingWith("multiResultsSize", () -> multiResults.get().persistentObjects().size())
          .readingWith("multiResultsValue", (index) -> {
            Person person = null;
            if ( multiResults.get().persistentObjects.size() > (int)index )
            {
              person = (Person)multiResults.get().persistentObjects.toArray()[(int)index];
            }
            return person;
          })
          .readingWith("multiResultsValueAsArray", () -> multiResults.get().persistentObjects.toArray());
      return access;
    }
  }

  public static class TestPersistResultInterest implements PersistResultInterest {
    public AtomicReference<Outcome<StorageException, Result>> outcome = new AtomicReference<>();
    public AtomicInteger count = new AtomicInteger(0);
    private AccessSafely access = AccessSafely.afterCompleting(1);

    @Override
    public void persistResultedIn(Outcome<StorageException, Result> outcome, Object persistentObject, int possible, int actual, Object object) {
      access.writeUsing("set", outcome);
    }

    @SuppressWarnings("unchecked")
    public AccessSafely afterCompleting( final int times ) {
      access =
        AccessSafely
          .afterCompleting(times)
          .writingWith("set", (value) -> {
            outcome.set((Outcome<StorageException,Result>)value);
            count.getAndAdd(1);
          })
          .readingWith("result", () -> outcome.get().andThen(success -> success).get())
          .readingWith("outcome", () -> outcome.get())
          .readingWith("count", () -> count.get());
      return access;
    }
  }
}