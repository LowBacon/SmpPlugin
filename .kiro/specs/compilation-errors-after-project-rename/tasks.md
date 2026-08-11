# Implementation Plan

## Overview

This implementation plan addresses 31 compilation errors introduced after renaming the project from FinnishSmp to SmpPlugin. The errors fall into two categories:

1. **Missing Import Error** - `SmpPluginExpansion` class cannot be resolved at line 287 in `SmpPlugin.java`
2. **Missing Manager Integration** - `AFKLoungeManager` lacks required field, getter, and instantiation in `SmpPlugin.java`, causing 30 errors across 4 dependent files

The fix involves minimal, targeted changes: adding one import statement, one field declaration, one getter method, and one instantiation line following existing manager patterns.

---

## Tasks

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Compilation Errors Verification
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: For this deterministic compilation bug, scope the property to the concrete failing cases - compilation of specific source files
  - Run `mvn clean compile` on UNFIXED code
  - Capture and document the 31 compilation errors:
    - 1 error for missing `SmpPluginExpansion` import at `SmpPlugin.java:287`
    - 2 errors in `ScoreboardManager.java` at lines 365, 635 for missing `getAFKLoungeManager()` method
    - 1 error in `TablistManager.java` at line 538 for missing `getAFKLoungeManager()` method
    - 13 errors in `AFKLoungeCommand.java` at lines 26, 65, 79, 96, 104, 111, 115, 122, 133, 138, 143, 145, 146
    - 14 errors in `AFKLoungeTask.java` at lines 30, 39, 42, 43, 56, 57, 68, 71, 74 (and additional locations)
  - Verify total error count is 31
  - **EXPECTED OUTCOME**: Compilation FAILS with 31 errors (this is correct - it proves the bug exists)
  - Document specific error messages:
    - "cannot find symbol: class SmpPluginExpansion"
    - "cannot find symbol: method getAFKLoungeManager()"
  - Mark task complete when test is run and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Affected Code Compilation
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for non-buggy compilation units
  - Identify Java source files that do NOT reference `SmpPluginExpansion` or call `getAFKLoungeManager()`
  - These files should compile successfully even in the unfixed codebase
  - Document specific files that currently compile without errors:
    - All manager classes except ScoreboardManager, TablistManager, AFKLoungeCommand, AFKLoungeTask
    - All command classes except AFKLoungeCommand
    - All other API expansion classes except where SmpPluginExpansion is instantiated
  - Create a verification approach: compile a subset of non-affected files individually to confirm they succeed
  - **EXPECTED OUTCOME**: Non-affected files compile successfully (this confirms baseline behavior to preserve)
  - Mark task complete when preservation scope is documented and baseline verified
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 3. Fix for compilation errors after project rename

  - [ ] 3.1 Add missing SmpPluginExpansion import
    - Open `src/main/java/com/bx/smpPlugin/SmpPlugin.java`
    - Locate the imports section (after line 6, where other API imports are located)
    - Add import statement: `import com.bx.smpPlugin.api.SmpPluginExpansion;`
    - Place it immediately after the existing line `import com.bx.smpPlugin.api.HideExpansion;` to maintain alphabetical ordering within the API imports group
    - Save the file
    - _Bug_Condition: isBugCondition(input) where input.filename == "SmpPlugin.java" AND input.line == 287 AND input.references("SmpPluginExpansion") AND NOT input.hasImport("com.bx.smpPlugin.api.SmpPluginExpansion")_
    - _Expected_Behavior: When SmpPlugin.java is compiled, the SmpPluginExpansion class reference at line 287 SHALL resolve successfully_
    - _Preservation: All other import statements remain unchanged (Requirement 3.2)_
    - _Requirements: 1.1, 2.1_

  - [ ] 3.2 Add AFKLoungeManager field declaration
    - Open `src/main/java/com/bx/smpPlugin/SmpPlugin.java`
    - Locate the "Managers" section (after line 94, where `AFKManager afkManager;` is declared)
    - Add field declaration: `private AFKLoungeManager afkLoungeManager;`
    - Place it immediately after the `AFKManager afkManager;` line since they are related features
    - Save the file
    - _Bug_Condition: isBugCondition(input) where input calls "plugin.getAFKLoungeManager()" AND NOT SmpPlugin.hasField("afkLoungeManager")_
    - _Expected_Behavior: SmpPlugin SHALL have a private field named afkLoungeManager of type AFKLoungeManager (Requirement 2.2)_
    - _Preservation: All existing manager field declarations remain unchanged (Requirement 3.1)_
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 2.2_

  - [ ] 3.3 Add AFKLoungeManager instantiation in onEnable()
    - Open `src/main/java/com/bx/smpPlugin/SmpPlugin.java`
    - Locate the `onEnable()` method, "Gameplay managers" section (after line 244, where `afkManager = new AFKManager(this);` is located)
    - Add instantiation: `afkLoungeManager = new AFKLoungeManager(this);`
    - Place immediately after `afkManager = new AFKManager(this);` since they are related AFK features
    - Save the file
    - _Bug_Condition: isBugCondition(input) where input calls "plugin.getAFKLoungeManager()" AND afkLoungeManager field is null_
    - _Expected_Behavior: When onEnable() executes, the AFKLoungeManager SHALL be instantiated with the plugin instance (Requirement 2.4)_
    - _Preservation: All other manager initialization sequences remain in correct order (Requirement 3.4)_
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 2.4_

  - [ ] 3.4 Add getAFKLoungeManager() getter method
    - Open `src/main/java/com/bx/smpPlugin/SmpPlugin.java`
    - Locate the "Getters" section (after line 1050, where `getAFKManager()` getter is located)
    - Add getter method:
      ```java
      public AFKLoungeManager getAFKLoungeManager() {
          return afkLoungeManager;
      }
      ```
    - Place immediately after the `getAFKManager()` getter since they are related features
    - Follow the exact pattern: simple return statement, no null checks
    - Save the file
    - _Bug_Condition: isBugCondition(input) where input calls "plugin.getAFKLoungeManager()" AND NOT SmpPlugin.hasMethod("getAFKLoungeManager")_
    - _Expected_Behavior: Any class calling plugin.getAFKLoungeManager() SHALL receive the AFKLoungeManager instance (Requirement 2.3)_
    - _Preservation: All existing manager getter methods remain unchanged (Requirement 3.1)_
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 2.3_

  - [ ] 3.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Successful Compilation
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior (compilation success)
    - Run `mvn clean compile` on FIXED code
    - **EXPECTED OUTCOME**: Compilation succeeds with 0 errors (confirms bug is fixed)
    - Verify specific fixes:
      - `SmpPluginExpansion` import is resolved at `SmpPlugin.java:287`
      - All 30 calls to `getAFKLoungeManager()` are resolved in 4 files
    - Document success: "All 31 compilation errors resolved"
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Affected Code Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run compilation on non-affected files from task 2
    - **EXPECTED OUTCOME**: Non-affected files still compile successfully (confirms no regressions)
    - Verify through code review:
      - All other manager field declarations remain unchanged (70+ managers)
      - All other manager getter methods remain unchanged
      - All other PlaceholderAPI expansion registrations remain unchanged (6 expansions)
      - Manager initialization order in onEnable() is unchanged except for new AFKLoungeManager line
      - AFKLoungeManager class implementation remains completely unchanged
    - Run full build: `mvn clean package`
    - Verify JAR artifact is produced successfully
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 4. Checkpoint - Ensure all tests pass
  - Run full Maven build: `mvn clean package`
  - Verify 0 compilation errors
  - Verify JAR artifact exists in `target/` directory
  - Optional: Load plugin on test server and verify startup logs show AFKLoungeManager initialization
  - Optional: Test AFKLounge-related functionality (commands, scoreboards, tasks) if test environment available
  - Ask the user if any questions arise or if further testing is needed

---

## Summary

This implementation plan addresses both compilation error categories:

1. **Issue 1 (Task 3.1)**: Add missing `SmpPluginExpansion` import statement
2. **Issue 2 (Tasks 3.2-3.4)**: Complete AFKLoungeManager integration (field, instantiation, getter)

The tasks follow the exploratory bugfix workflow:
- **Task 1**: Confirm bug exists by running compilation and documenting all 31 errors
- **Task 2**: Identify and verify non-affected code compiles successfully (preservation baseline)
- **Task 3**: Apply minimal fixes following existing manager integration patterns
- **Task 4**: Validate compilation success and verify no regressions introduced

All changes follow established patterns in the codebase (manager integration pattern, import organization) and maintain consistency with related features (AFKManager, other PlaceholderAPI expansions).
