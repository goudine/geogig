@Commands
Feature: IndexRebuild
  The Index Rebuild command allows a user to rebuild the spatial index for a specified layer
  The command must be executed using the HTTP POST method

  Scenario: Verify quad tree after rebuilding spatial index
    Given There is a repo with some data
    When I call "PUT /repos/repo1/index/create?treeRefSpec=Points"
    Then We change, add and commit some more data
    When I call "POST /repos/repo1/index/rebuild?treeRefSpec=Points"
    Then the response body should contain "<success>true</success><treesRebuilt>2</treesRebuilt>"

