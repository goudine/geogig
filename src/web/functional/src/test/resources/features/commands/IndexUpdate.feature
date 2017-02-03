@Commands
Feature: IndexUpdate
  The Index Update command allows a user to update the spatial index with an attribute
  The command must be executed using the HTTP POST method

  Scenario: Verify updating spatial index by adding attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=sp"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&attribute=ip&add=true"
    Then the response body should contain "<success>true</success>"

  Scenario: Verify success after updating spatial index on attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&extraAttributes=sp"
    Then the response body should contain "<success>true</success>"

  Scenario: Verify success when over-writing spatial index on attribute
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points&extraAttributes=sp"
    When I call "POST /repos/repo1/index/update?treeRefSpec=Points&overwrite=true"
    Then the response body should contain "<success>true</success>"


