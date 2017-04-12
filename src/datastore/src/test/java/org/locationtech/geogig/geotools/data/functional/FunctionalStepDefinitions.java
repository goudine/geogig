/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.functional;

import static com.google.common.collect.ImmutableList.copyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.geotools.data.GeoGigDataStore;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.test.integration.TestContextBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

/**
 *
 */
public class FunctionalStepDefinitions {

    // Thread Pool counts
    private static final int WRITE_POOL_THREAD_COUNT = 4, READ_POOL_THREAD_COUNT = 4;

    private static final Random RANDOM = new Random(System.nanoTime());

    // Feature Types
    private static final SimpleFeatureType POINT_TYPE, POLY_TYPE;

    static {
        final String pointsTypeSpec = "the_geom:Point:srid=4326,sp:String,ip:Integer";
        final String polyTypeSpec = "the_geom:MultiPolygon:srid=4326,sp:String,ip:Integer";
        try {
            POINT_TYPE = DataUtilities.createType("point", pointsTypeSpec);
            POLY_TYPE = DataUtilities.createType("polygon", polyTypeSpec);
        } catch (SchemaException e) {
            throw Throwables.propagate(e);
        }
    }

    // Temp Directory
    private TemporaryFolder tmp;

    // Home directory for GeoGig user
    private File userHomeDirectry;

    // Test repos
    private HashMap<String, Repository> repoMap;

    // DataStores
    private HashMap<String, GeoGigDataStore> datastoreMap;

    // Executor services
    private ExecutorService writeService, readService;

    // Write counts
    // These should be set by methods that take them as arguments
    private int writesPerThread, writeThreads;

    private SimpleFeature postEditedFeature;
    private SimpleFeature preEditedFeature;

    // commit count
    private HashMap<String, Integer> initialCommitCountMap;

    // working layer
    private SimpleFeatureType currentLayer;

    @Before
    public void before() throws Exception {
        tmp = new TemporaryFolder();
        tmp.create();

        userHomeDirectry = tmp.newFile("home");

        writeService = Executors.newFixedThreadPool(WRITE_POOL_THREAD_COUNT,
                new ThreadFactoryBuilder().setNameFormat("edit-thread-%d").build());

        readService = Executors.newFixedThreadPool(READ_POOL_THREAD_COUNT,
                new ThreadFactoryBuilder().setNameFormat("read-thread-%d").build());

        initialCommitCountMap = new HashMap<>(2);
        repoMap = new HashMap<>(2);
        datastoreMap = new HashMap<>(2);
    }

    @After
    public void after() {
        for (GeoGigDataStore store : datastoreMap.values()) {
            if (store != null) {
                store.dispose();
            }
        }
        for (Repository repo : repoMap.values()) {
            if (repo != null) {
                repo.close();
            }
        }
        if (writeService != null) {
            writeService.shutdownNow();
        }
        if (readService != null) {
            readService.shutdownNow();
        }
        if (tmp != null) {
            tmp.delete();
        }
    }

    @Given("^I am working with the \"([^\"]*)\" layer$")
    public void i_am_working_with_the_layer(String layerName) throws Throwable {
        assertNotNull("Layer name must exist", layerName);
        if (POINT_TYPE.getTypeName().equals(layerName)) {
            this.currentLayer = POINT_TYPE;
        } else if (POLY_TYPE.getTypeName().equals(layerName)) {
            this.currentLayer = POLY_TYPE;
        } else {
            fail(String.format("Layer name does not exist in test data: %s", layerName));
        }
    }

    @Given("^I have a datastore named \"([^\"]*)\" backed by a GeoGig repo$")
    public void i_have_a_datastore_backed_by_a_GeoGig_repo(String repoName) throws Throwable {
        Repository repo = initRepo(repoName);
        repoMap.put(repoName, repo);
        GeoGigDataStore store = new GeoGigDataStore(repo);
        store.createSchema(currentLayer);
        datastoreMap.put(repoName, store);
    }

    @Given("^datastore \"([^\"]*)\" has (\\d+) features per thread inserted using (\\d+) threads$")
    public void datastore_has_features_inserted(String storeName, int numPointsPerWriteThread,
            int writeThreadCount) throws Throwable {

        // set the counts for later verification
        writesPerThread = numPointsPerWriteThread;
        writeThreads = writeThreadCount;

        List<Future<List<SimpleFeature>>> inserts = runInserts(writeThreadCount,
                numPointsPerWriteThread, datastoreMap.get(storeName));
        assertEquals(String.format("Expected exactly %s Insert task(s)", writeThreadCount),
                writeThreadCount, inserts.size());
        for (Future<List<SimpleFeature>> future : inserts) {
            List<SimpleFeature> featureList = future.get();
            assertEquals(numPointsPerWriteThread, featureList.size());
        }
    }

    @Then("^I should be able to retrieve data from \"([^\"]*)\"" +
            " using (\\d+) threads and (\\d+) reads per thread$")
    public void datastore_should_have_some_data(String storeName, int readThreadCount,
            int numReadsPerThread) throws Throwable {
        List<Future<List<SimpleFeature>>> reads = runReads(readThreadCount, numReadsPerThread,
                datastoreMap.get(storeName));
        assertEquals(readThreadCount, reads.size());
        for (Future<List<SimpleFeature>> future : reads) {
            List<SimpleFeature> featureList = future.get();
            assertEquals(numReadsPerThread * writesPerThread * writeThreads, featureList.size());
        }
    }

    @Given("^datastore \"([^\"]*)\" has the same data as \"([^\"]*)\"$")
    public void datastore_has_same_data(String targetStore, String srcStore) throws Throwable {

        // get the target datastore
        SimpleFeatureStore destFeatureSource = getFeatureStore(targetStore);

        // clone the source feature collection into the target
        try (SimpleFeatureIterator srcFeatures = getIterator(getFeatureStore(srcStore))) {

            while (srcFeatures.hasNext()) {
                SimpleFeature clonedFeature =
                        (SimpleFeature) DataUtilities.duplicate(srcFeatures.next());
                Transaction tx = new DefaultTransaction();
                destFeatureSource.setTransaction(tx);
                try {
                    destFeatureSource.addFeatures(DataUtilities.collection(clonedFeature));
                    tx.commit();
                } finally {
                    tx.close();
                }
            }
        }
        datastore_should_have_some_data(targetStore, 4, 20);
    }

    @When("^I create a spatial index on \"([^\"]*)\"$")
    public void i_create_a_spatial_index_on(String storeName) throws Throwable {
        GeoGigDataStore store = datastoreMap.get(storeName);
        Optional<ObjectId> createOrUpdateIndex =
                store.createOrUpdateIndex(currentLayer.getTypeName());
        assertTrue("Expected an Index to be created", createOrUpdateIndex.isPresent());
        Repository repo = repoMap.get(storeName);
        IndexDatabase indexDatabase = repo.indexDatabase();
        List<IndexInfo> resolveIndexInfo = indexDatabase.getIndexInfos(currentLayer.getTypeName());
        assertEquals("Expected exactly 1 IndexInfo", 1, resolveIndexInfo.size());
        IndexInfo info = resolveIndexInfo.get(0);
        assertEquals("Unexpected Index type", IndexType.QUADTREE, info.getIndexType());
        assertEquals("Unexpected Index spatial attribute", "the_geom", info.getAttributeName());
        assertEquals("Unexpected Index Path name", currentLayer.getTypeName(), info.getTreeName());
    }

    @Then("^datastore \"([^\"]*)\" and datastore \"([^\"]*)\" both have the same features$")
    public void datastores_have_the_same_features(String store1, String store2) throws Throwable {
        // build a feature reader from the datastore
        SimpleFeatureStore featureStore1 = getFeatureStore(store1);
        SimpleFeatureStore featureStore2 = getFeatureStore(store2);
        SimpleFeatureCollection featureCollection1 = featureStore1.getFeatures();
        SimpleFeatureCollection featureCollection2 = featureStore2.getFeatures();
        assertEquals(
                String.format("Expected the same number of features in %s as in %s", store2, store1),
                featureCollection1.size(), featureCollection2.size());
        try (SimpleFeatureIterator featureIterator1 = featureCollection1.features()) {
            while (featureIterator1.hasNext()) {
                SimpleFeature feature1 = featureIterator1.next();
                assertTrue(String.format("Expected %s to contain all features from %s", store2,
                        store1),
                        featureCollection2.contains(feature1));
            }
        }
    }

    @When("^I make an edit to \"([^\"]*)\"$")
    public void i_make_an_edit_to(String storeName) throws Throwable {
        SimpleFeatureStore featureStore = getFeatureStore(storeName);
        Transaction tx = new DefaultTransaction();
        featureStore.setTransaction(tx);
        int featureIndex = RANDOM.nextInt(writesPerThread * writeThreads);
        int count = 0;
        try (SimpleFeatureIterator featureIterator = getIterator(featureStore)) {
            while (featureIterator.hasNext() && count++ < featureIndex) {
                // advance the iterator
                featureIterator.next();
            }
            // get the next feature
            preEditedFeature = featureIterator.next();
            postEditedFeature = (SimpleFeature) DataUtilities.duplicate(preEditedFeature);
            // edit the feature
            Object attribute = postEditedFeature.getAttribute("sp");
            assertNotNull(attribute);
            String newVal = attribute.toString() + "_edited";
            postEditedFeature.setAttribute("sp", newVal);
            featureStore.addFeatures(DataUtilities.collection(postEditedFeature));
            tx.commit();
        } finally {
            tx.close();
        }
        // make sure the stored editedFeature is a GeogigSimpleFeature, not a SimpleFeatureImpl
        tx = new DefaultTransaction();
        featureStore.setTransaction(tx);
        try (SimpleFeatureIterator featureIterator = getIterator(featureStore)) {
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                if (feature.getIdentifier().equals(preEditedFeature.getIdentifier())) {
                    postEditedFeature = feature;
                    break;
                }
            }
        } finally {
            tx.close();
        }
    }

    @When("^I make the same edit to \"([^\"]*)\"$")
    public void i_make_the_same_edit(String storeName) throws Throwable {
        SimpleFeatureStore featureStore = getFeatureStore(storeName);
        SimpleFeatureCollection featureCollection = featureStore.getFeatures();
        assertTrue("Expected DataStore " + storeName + " to contain feature: " + preEditedFeature,
                featureCollection.contains(preEditedFeature));
        Transaction tx = new DefaultTransaction();
        featureStore.setTransaction(tx);
        try {
            featureStore.addFeatures(DataUtilities.collection(postEditedFeature));
            tx.commit();
        } finally {
            tx.close();
        }
    }

    @Then("^datastore \"([^\"]*)\" has the edited feature$")
    public void datastore_has_the_edited_feature(String storeName) throws Throwable {
        // get the features from the store
        List<Future<List<SimpleFeature>>> reads = runReads(1, 1, datastoreMap.get(storeName));
        assertEquals(1, reads.size());
        Future<List<SimpleFeature>> future = reads.get(0);
        List<SimpleFeature> featureList = future.get();
        // ensure the list contains the edited feature
        final Object editedAttributeValue = postEditedFeature.getAttribute("sp");
        for (SimpleFeature feature : featureList) {
            if (postEditedFeature.getIdentifier().equals(feature.getIdentifier())) {
                // found the FID
                final Object featureAttributeValue = feature.getAttribute("sp");
                assertEquals(
                        String.format(
                                "DataStore %s does not contain edited feature with \"sp\" attribute: %s",
                                storeName, editedAttributeValue),
                        editedAttributeValue, featureAttributeValue);
                assertEquals(postEditedFeature, feature);
                return;
            }
        }
        fail(String.format("DataStore %s does not contain edited feature with \"sp\" attribute: %s",
                storeName, editedAttributeValue));
    }

    @Then("^concurrent edits are made to datastore \"([^\"]*)\" with (\\d+) features per thread using (\\d+) threads$")
    public void concurrent_edits(String storeName, int numPointsPerWriteThread,
        int writeThreadCount) throws Throwable {

        // set the counts for later verification
        writesPerThread = numPointsPerWriteThread;
        writeThreads = writeThreadCount;

        List<Future<List<SimpleFeature>>> inserts = insertWithConcurrentEdits(writeThreadCount,
            numPointsPerWriteThread, datastoreMap.get(storeName));

    }

    @Then("^we insert features into datastore \"([^\"]*)\" with (\\d+) features per thread using (\\d+) threads concurrently$")
    public List<Future<List<SimpleFeature>>> insert_features_concurrently(String storeName, int numPointsPerWriteThread,
        int writeThreadCount) throws Throwable {

        // set the counts for later verification
        writesPerThread = numPointsPerWriteThread;
        writeThreads = writeThreadCount;

        List<Future<List<SimpleFeature>>> inserts = runInserts(writeThreadCount,
            numPointsPerWriteThread, datastoreMap.get(storeName));
        assertEquals(String.format("Expected exactly %s Insert task(s)", writeThreadCount),
            writeThreadCount, inserts.size());
        for (Future<List<SimpleFeature>> future : inserts) {
            List<SimpleFeature> featureList = future.get();
            assertEquals(numPointsPerWriteThread, featureList.size());
        }
//        writesPerThread = numPointsPerWriteThread;
//        writeThreads = writeThreadCount;

        runEdits(writeThreadCount, numPointsPerWriteThread, storeName, inserts);

        assertEquals(String.format("Expected exactly %s Insert task(s)", writeThreadCount),
            writeThreadCount, inserts.size());

        for (Future<List<SimpleFeature>> future : inserts) {
            List<SimpleFeature> featureList = future.get();
            assertEquals(numPointsPerWriteThread, featureList.size());
        }
        return inserts;
    }

    @Then("^concurrent edits are made to the \"([^\"]*)\" datastore with (\\d+) features per thread using (\\d+) threads concurrently \"([^\"]*)\"$")
    public void edit_features_concurrently(String storeName, int numPointsPerWriteThread,
        int writeThreadCount, List<Future<List<SimpleFeature>>> inserts) throws Throwable {

        // edit the features in datastore concurrently
        // set the counts for later verification
        writesPerThread = numPointsPerWriteThread;
        writeThreads = writeThreadCount;

        runEdits(writeThreadCount, numPointsPerWriteThread, storeName, inserts);

        assertEquals(String.format("Expected exactly %s Insert task(s)", writeThreadCount),
            writeThreadCount, inserts.size());

        for (Future<List<SimpleFeature>> future : inserts) {
            List<SimpleFeature> featureList = future.get();
            assertEquals(numPointsPerWriteThread, featureList.size());
        }
    }

    private SimpleFeatureStore getFeatureStore(String storeName)
            throws IOException {
        GeoGigDataStore store = datastoreMap.get(storeName);
        return (SimpleFeatureStore) store.getFeatureSource(currentLayer.getTypeName());
    }

    private SimpleFeatureIterator getIterator(SimpleFeatureStore store) throws IOException {
        return store.getFeatures().features();
    }

    private void setInitialCommitCount(Context context, String repoName) {
        // get the commit count
        int size = copyOf(context.command(LogOp.class).call()).size();
        initialCommitCountMap.put(repoName, size);
    }

    private Repository initRepo(String repoName) throws IOException {
        File workingDirectory = tmp.newFolder(repoName);
        TestPlatform platform = new TestPlatform(workingDirectory, userHomeDirectry);
        GlobalContextBuilder.builder(new TestContextBuilder(platform));
        Context context = GlobalContextBuilder.builder().build(new Hints().platform(platform));
        GeoGIG geogig = new GeoGIG(context);
        geogig.command(InitOp.class).call();
        geogig.command(ConfigOp.class).setAction(ConfigOp.ConfigAction.CONFIG_SET).
                setName("user.name").setValue("geogig_test").call();
        geogig.command(ConfigOp.class).setAction(ConfigOp.ConfigAction.CONFIG_SET).
                setName("user.email").setValue("geogig_test@geogig.org").call();
        setInitialCommitCount(context, repoName);
        return geogig.getRepository();
    }

    private List<Future<List<SimpleFeature>>> runInserts(final int writeThreadCount,
            final int insertsPerTask, GeoGigDataStore store) {
        List<Future<List<SimpleFeature>>> insertResults = Lists.newArrayList();
        for (int i = 0; i < writeThreadCount; i++) {
            insertResults.add(writeService.submit(new InsertTask(store, insertsPerTask,
                    currentLayer)));
        }
        return insertResults;
    }

    private List<Future<List<SimpleFeature>>> runEdits (final int writeThreadCount,
        final int insertsPerTask, String storeName, List<Future<List<SimpleFeature>>> insertResults)
        throws IOException {
        // perform edits on filled in List, and then return

        SimpleFeatureStore featureStore = getFeatureStore(storeName);
        Transaction tx = new DefaultTransaction();
        featureStore.setTransaction(tx);
        int featureIndex = RANDOM.nextInt(writesPerThread * writeThreads);
        int count = 0;
        try (SimpleFeatureIterator featureIterator = getIterator(featureStore)) {
            while (featureIterator.hasNext() && count++ < featureIndex) {
                // advance the iterator
                featureIterator.next();
            }
            // get the next feature
            preEditedFeature = featureIterator.next();
            postEditedFeature = (SimpleFeature) DataUtilities.duplicate(preEditedFeature);
            // edit the feature
            Object attribute = postEditedFeature.getAttribute("sp");
            assertNotNull(attribute);
            String newVal = attribute.toString() + "_edited";
            postEditedFeature.setAttribute("sp", newVal);
            featureStore.addFeatures(DataUtilities.collection(postEditedFeature));
            tx.commit();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            tx.close();
        }
        // make sure the stored editedFeature is a GeogigSimpleFeature, not a SimpleFeatureImpl
        tx = new DefaultTransaction();
        featureStore.setTransaction(tx);
        try (SimpleFeatureIterator featureIterator = getIterator(featureStore)) {
            while (featureIterator.hasNext()) {
                SimpleFeature feature = featureIterator.next();
                if (feature.getIdentifier().equals(preEditedFeature.getIdentifier())) {
                    postEditedFeature = feature;
                    break;
                }
            }
        } finally {
            tx.close();
        }

        return insertResults;
    }

    private List<Future<List<SimpleFeature>>> insertWithConcurrentEdits(final int writeThreadCount,
        final int insertsPerTask, GeoGigDataStore store) {
        List<Future<List<SimpleFeature>>> insertResultsWithEdit = Lists.newArrayList();
        for (int i = 0; i < writeThreadCount; i++) {
            insertResultsWithEdit.add(writeService.submit(new EditFeatureTask(store, insertsPerTask,
                currentLayer)));
        }
        return insertResultsWithEdit;
    }

    private List<Future<List<SimpleFeature>>> runReads(final int readThreadCount,
            final int readsPerTask, GeoGigDataStore store) {
        List<Future<List<SimpleFeature>>> readResults = Lists.newArrayList();
        for (int i = 0; i < readThreadCount; i++) {
            readResults.add(readService.submit(new ReadTask(store, readsPerTask, currentLayer)));
        }
        return readResults;
    }

    private static final GeometryFactory GF = new GeometryFactory();

    private static Coordinate createRandomCoordinate() {
        // keep the Latitude away from the poles, just to be sure
        return createRandomCoordinate(-179.9, 179.9, -89.9, 89.9);
    }

    private static Coordinate createRandomCoordinate(double minLat, double maxLat,
            double minLon, double maxLon) {
        return new Coordinate(RANDOM.nextDouble() * (maxLat - minLat) + minLat,
                RANDOM.nextDouble() * (maxLon - minLon) + minLon);
    }

    private static Point createRandomPoint() {
        return GF.createPoint(createRandomCoordinate());
    }

    private static Coordinate createCoordinate(double lat, double lon) {
        return new Coordinate(lat, lon);
    }

    private static MultiPolygon createRandomPolygon() {
        Polygon[] polygons = new Polygon[1];
        for (int polyCount = 0; polyCount < polygons.length; ++polyCount) {
            // create 4 points and repeat the first to close the box
            Coordinate[] coordinates = new Coordinate[5];
            // make a 3 x 3 box
            final int boxSize = 3;
            // keep top left corner bbox size East of -180 and North of -80
            Coordinate nePoint = createRandomCoordinate(-179.8 + boxSize, 179.9, -77 + boxSize, 80);
            coordinates[0] = nePoint;
            coordinates[1] = createCoordinate(nePoint.x - boxSize, nePoint.y);
            coordinates[2] = createCoordinate(nePoint.x - boxSize, nePoint.y - boxSize);
            coordinates[3] = createCoordinate(nePoint.x, nePoint.y - boxSize);
            coordinates[4] = coordinates[0];
            Polygon newPolygon = GF.createPolygon(coordinates);
            polygons[polyCount] = newPolygon;
        }
        return GF.createMultiPolygon(polygons);
    }

    private static class InsertTask implements Callable<List<SimpleFeature>> {

        private final GeoGigDataStore dataStore;

        private final SimpleFeatureBuilder builder;

        private final int numInserts;

        private final SimpleFeatureType type;

        public InsertTask(GeoGigDataStore store, int numInserts, final SimpleFeatureType type) {
            this.dataStore = store;
            this.numInserts = numInserts;
            this.type = type;
            this.builder = new SimpleFeatureBuilder(type);
        }

        @Override
        public List<SimpleFeature> call() {
            int random;
            final String typeName = type.getTypeName();
            SimpleFeatureStore featureSource;
            List<SimpleFeature> featureList = Lists.newArrayList();
            for (int i = 0; i < numInserts; i++) {
                synchronized (RANDOM) {
                    random = RANDOM.nextInt();
                }
                builder.reset();
                switch (type.getTypeName()) {
                    case "point":
                        builder.set("the_geom", createRandomPoint());
                        break;
                    case "polygon":
                        builder.set("the_geom", createRandomPolygon());
                        break;
                    default:
                        throw new RuntimeException(String.format("Invalid layer name: %s",
                                type.getTypeName()));
                }
                builder.set("sp", String.valueOf(random));
                builder.set("ip", Integer.valueOf(random));
                SimpleFeature feature = builder.buildFeature(String.valueOf(random));
                featureList.add(feature);
            }

            try {
                featureSource = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
                Transaction tx = new DefaultTransaction();
                featureSource.setTransaction(tx);
                try {
                    featureSource.addFeatures(DataUtilities.collection(featureList));
                    tx.commit();
                } finally {
                    tx.close();
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            return featureList;
        }
    }

    private static class EditFeatureTask implements Callable<List<SimpleFeature>> {

        private final GeoGigDataStore dataStore;

        private final SimpleFeatureBuilder builder;

        private final int numInserts;

        private final SimpleFeatureType type;

        public EditFeatureTask(GeoGigDataStore store, int numInserts, final SimpleFeatureType type) {
            this.dataStore = store;
            this.numInserts = numInserts;
            this.type = type;
            this.builder = new SimpleFeatureBuilder(type);
        }

        @Override
        public List<SimpleFeature> call() {
            int random;
            final String typeName = type.getTypeName();
            SimpleFeatureStore featureSource;
            List<SimpleFeature> featureList = Lists.newArrayList();
            for (int i = 0; i < numInserts; i++) {
                synchronized (RANDOM) {
                    random = RANDOM.nextInt();
                }
                builder.reset();
                switch (type.getTypeName()) {
                case "point":
                    builder.set("the_geom", createRandomPoint());
                    break;
                case "polygon":
                    builder.set("the_geom", createRandomPolygon());
                    break;
                default:
                    throw new RuntimeException(String.format("Invalid layer name: %s",
                        type.getTypeName()));
                }
                builder.set("sp", String.valueOf(random));
                builder.set("ip", Integer.valueOf(random));
                SimpleFeature feature = builder.buildFeature(String.valueOf(random));
                featureList.add(feature);
            }

            try {
                featureSource = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
                Transaction tx = new DefaultTransaction();
                featureSource.setTransaction(tx);
                try {
                    featureSource.addFeatures(DataUtilities.collection(featureList));
                    tx.commit();
                } finally {
                    tx.close();
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            return featureList;
        }
    }

    private static class ReadTask implements Callable<List<SimpleFeature>> {

        private final GeoGigDataStore dataStore;

        private final int numReads;

        private final SimpleFeatureType type;

        public ReadTask(GeoGigDataStore store, final int numReads, final SimpleFeatureType type) {
            this.dataStore = store;
            this.numReads = numReads;
            this.type = type;
        }

        @Override
        public List<SimpleFeature> call() {
            List<SimpleFeature> featureList = Lists.newArrayList();
            try {
                for (int i = 0; i < numReads; i++) {
                    featureList.addAll(doRead());
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            return featureList;
        }

        private List<SimpleFeature> doRead() throws IOException {
            final String typeName = type.getTypeName();
            SimpleFeatureSource featureSource;
            featureSource = dataStore.getFeatureSource(typeName);
            SimpleFeatureCollection fc = featureSource.getFeatures();
            List<SimpleFeature> featureList = Lists.newArrayList();
            try (SimpleFeatureIterator features = fc.features()) {
                while (features.hasNext()) {
                    SimpleFeature next = features.next();
                    featureList.add(next);
                }
            }
            return featureList;
        }
    }
}
