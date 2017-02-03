@Commands
Feature: IndexCreate
  The Index Create command allows a user to add spatial index to a specified layer
  The command must be executed using the HTTP PUT method

  Scenario: Create index fails when feature tree does not exist
    Given There is an empty repository named repo1
    And I have a transaction as "@txId" on the "repo1" repo
    And I have staged "Point.1" on the "repo1" repo in the "@txId" transaction
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Point"
    Then the response body should contain "Can't find feature tree"

  Scenario: Verify success after adding spatial index
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    Then the response body should contain "<success>true</success>"

  Scenario: Verify creating index with attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&attribute=sp"
    Then the response body should contain "<success>true</success><index><treeName>Points</treeName>"

  Scenario: Verify creating index with extra attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp&extraAttribute=ip"
    Then the response body should contain "<success>true</success><index><treeName>Points</treeName>"

  Scenario: Verify creating index with full history
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&indexHistory=true"
    Then the response body should contain "<success>true</success><index><treeName>Points</treeName>"

