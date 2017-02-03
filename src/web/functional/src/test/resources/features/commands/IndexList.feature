@Commands
Feature: IndexList
  The Index List command allows a user to display spatial index info for a feature tree
  The command must be executed using the HTTP GET method

  Scenario: Verify index list return for all feature trees
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "GET /repos/repo1/index/list"
    Then the response body should contain "<success>true</success><index><treeName>Points</treeName>"

  Scenario: Verify correct index list return for a feature tree
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    When I call "GET /repos/repo1/index/list?treeName=Points"
    Then the response body should contain "<success>true</success><index><treeName>Points</treeName>"

  Scenario: Verify failed index list return for non-existent feature tree
    Given There is a repo with some data
    When I call "GET /repos/repo1/index/list?treeName=does_not_exist"
    Then the response body should contain "<error>The provided tree name was not found in the HEAD commit.</error>"
