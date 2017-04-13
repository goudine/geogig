Feature: GeoGig DataStore Feature read/write validation
  The GeoGig DataStore is the integration point between GeoServer/OGC services
  and GeoGig repositories. These scenarios are meant to ensure data/feature
  integrity via interactions with the DataStore.
   @newTest
   Scenario: Ensure Point Features can be retrieved from a DataStore
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
   @newTest
   Scenario: Ensure Polygon Features can be retrieved from a DataStore
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
   @newTest
   Scenario: Ensure Point Features can be retrieved from a DataStore with an indexed repo
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
   @newTest
   Scenario: Ensure Polygon Features can be retrieved from a DataStore with an indexed repo
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
   @newTest
   Scenario: Ensure indexed Point Features match non-indexed Features
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      And datastore "dataStore1" and datastore "dataStore2" both have the same features
   @newTest
   Scenario: Ensure indexed Polygon Features match non-indexed Features
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      And datastore "dataStore1" and datastore "dataStore2" both have the same features
   @newTest
   Scenario: Ensure Point Features can be retrieved from a DataStore after editing
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature
   @newTest
   Scenario: Ensure Polygon Features can be retrieved from a DataStore after editing
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature
   @newTest
   Scenario: Ensure Point Features can be retrieved from a DataStore after editing with index
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature
   @newTest
   Scenario: Ensure Polygon Features can be retrieved from a DataStore after editing with index
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      When I create a spatial index on "dataStore1"
      Then I should be able to retrieve data from "dataStore1" using 4 threads and 40 reads per thread
      When I make an edit to "dataStore1"
      Then datastore "dataStore1" has the edited feature
   @newTest
   Scenario: Ensure edited indexed Point Features match edited non-indexed Features
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      And I make an edit to "dataStore1"
      And I make the same edit to "dataStore2"
      Then datastore "dataStore1" and datastore "dataStore2" both have the same features
   @newTest
   Scenario: Ensure edited indexed Polygon Features match edited non-indexed Features
      Given I am working with the "polygon" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads
      And I have a datastore named "dataStore2" backed by a GeoGig repo
      And datastore "dataStore2" has the same data as "dataStore1"
      When I create a spatial index on "dataStore1"
      And I make an edit to "dataStore1"
      And I make the same edit to "dataStore2"
      Then datastore "dataStore1" and datastore "dataStore2" both have the same features

   @newTest
   Scenario: Ensure concurent edits function as expected
      Given I am working with the "point" layer
      And I have a datastore named "dataStore1" backed by a GeoGig repo
      And datastore "dataStore1" has 200 features per thread inserted using 4 threads concurrently
      And I create a spatial index on "dataStore1"
      Then I make a feature edit to "dataStore1" using 20 threads
      Then the datastore "dataStore1" has the 20 edited features



      #  this step edits 1 feature per thread (need to know how many threads are making edits)
      # ***Then I make 1 feature edit using 20 threads***
      #  this step can use RANDOM to generate 20 unique numbers (0-800)
      #  populates a 'Map<SimpleFeature>' with features to edit (size 20)
      #  iterate over the features in store, make edits, save it back
      #