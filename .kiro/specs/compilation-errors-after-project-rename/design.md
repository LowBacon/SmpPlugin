# Compilation Errors After Project Rename - Bugfix Design

## Overview

This bugfix addresses 31 compilation errors introduced after renaming the project from FinnishSmp to SmpPlugin. The errors fall into two categories: (1) a missing import statement for `SmpPluginExpansion`, and (2) missing integration of `AFKLoungeManager` into the main plugin class. The fix involves adding one import statement, declaring one manager field, providing one getter method, and adding one manager instantiation line following the existing manager integration patterns in `SmpPlugin.java`.

The approach is minimal and targeted: add only the missing pieces required for compilation, following the exact patterns established by other managers (such as `AFKManager`, `HoverStatsManager`, and `ShardManager`) already integrated in the codebase.

## Glossary

- **Bug_Condition (C)**: The condition that triggers compilation errors - when the Java compiler attempts to resolve `SmpPluginExpansion` or `getAFKLoungeManager()` references
- **Property (P)**: The desired behavior when the code is compiled - all class and method references resolve successfully
- **Preservation**: All existing manager integrations, PlaceholderAPI expansions, and initialization sequences that must remain unchanged
- **SmpPlugin.java**: The main plugin class at `src/main/java/com/bx/smpPlugin/SmpPlugin.java` that manages all plugin components
- **AFKLoungeManager**: The manager class at `src/main/java/com/bx/smpPlugin/managers/AFKLoungeManager.java` that handles AFK lounge functionality
- **SmpPluginExpansion**: The PlaceholderAPI expansion class at `src/main/java/com/bx/smpPlugin/api/SmpPluginExpansion.java` that provides placeholder support
- **Manager Integration Pattern**: The established pattern in SmpPlugin.java of declaring a private field, instantiating it in `onEnable()`, and providing a public getter method

## Bug Details

### Bug Condition

The compilation errors manifest when the Java compiler processes `SmpPlugin.java` and files that depend on `AFKLoungeManager`. The compiler encounters unresolved symbols at specific locations where either the class name cannot be found or the getter method does not exist.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type CompilationUnit (Java source file)
  OUTPUT: boolean
  
  RETURN (input.filename == "SmpPlugin.java" 
          AND input.line == 287 
          AND input.references("SmpPluginExpansion") 
          AND NOT input.hasImport("com.bx.smpPlugin.api.SmpPluginExpansion"))
         OR
         (input IN ["ScoreboardManager.java", "TablistManager.java", 
                    "AFKLoungeCommand.java", "AFKLoungeTask.java"]
          AND input.calls("plugin.getAFKLoungeManager()")
          AND NOT SmpPlugin.hasMethod("getAFKLoungeManager"))
END FUNCTION
```

### Examples

**Issue 1: Missing SmpPluginExpansion Import**
- **Location**: SmpPlugin.java, line 287
- **Current Code**: `new SmpPluginExpansion(this).register();`
- **Error**: "cannot find symbol: class SmpPluginExpansion"
- **Cause**: Import statement `import com.bx.smpPlugin.api.SmpPluginExpansion;` is missing from the imports section

**Issue 2: Missing AFKLoungeManager Integration**
- **Location 1**: ScoreboardManager.java, lines 365 and 635
- **Current Code**: `plugin.getAFKLoungeManager().getScoreboardLines()`
- **Error**: "cannot find symbol: method getAFKLoungeManager()"
- **Cause**: No field, getter, or instantiation in SmpPlugin.java

- **Location 2**: TablistManager.java, line 538
- **Current Code**: `plugin.getAFKLoungeManager().isPlayerInLounge(player)`
- **Error**: "cannot find symbol: method getAFKLoungeManager()"
- **Cause**: No field, getter, or instantiation in SmpPlugin.java

- **Location 3**: AFKLoungeCommand.java, 13 method call locations
- **Current Code**: `plugin.getAFKLoungeManager().getAFKLoungeLocation()` (and similar calls)
- **Error**: "cannot find symbol: method getAFKLoungeManager()"
- **Cause**: No field, getter, or instantiation in SmpPlugin.java

- **Location 4**: AFKLoungeTask.java, 9 method call locations
- **Current Code**: `plugin.getAFKLoungeManager().isEnabled()` (and similar calls)
- **Error**: "cannot find symbol: method getAFKLoungeManager()"
- **Cause**: No field, getter, or instantiation in SmpPlugin.java

**Edge Case**: Build system failure
- **Trigger**: Running `mvn clean package`
- **Expected Behavior**: Build should complete successfully and produce a JAR artifact
- **Current Behavior**: Build fails with 31 total compilation errors

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- All existing manager fields (70+ managers) must continue to be accessible through their getter methods
- All existing PlaceholderAPI expansions must continue to register successfully
- All manager initialization sequences must remain in their correct order
- AFKLoungeManager class implementation must remain completely unchanged
- All files calling other manager getters must continue to compile without errors

**Scope:**
All code that does NOT involve `SmpPluginExpansion` instantiation or `getAFKLoungeManager()` calls should be completely unaffected by this fix. This includes:
- All other import statements in SmpPlugin.java
- All other manager field declarations, instantiations, and getters
- All other PlaceholderAPI expansion registrations
- All other plugin initialization logic
- All classes that do not call `getAFKLoungeManager()`

## Hypothesized Root Cause

Based on the compilation errors and code analysis, the root causes are:

1. **Missing Import Statement**: The `SmpPluginExpansion` import was likely removed or never added during the project rename from FinnishSmp to SmpPlugin. Other PlaceholderAPI expansions (EconomyExpansion, FinnishSmpExpansion, etc.) have their imports present, but this one is missing.

2. **Incomplete Manager Integration**: The `AFKLoungeManager` class was created and is being used in multiple files (ScoreboardManager, TablistManager, AFKLoungeCommand, AFKLoungeTask), but the standard three-step integration pattern in SmpPlugin.java was never completed:
   - **Missing Step 1**: No private field declaration in the "Managers" section
   - **Missing Step 2**: No instantiation in the `onEnable()` method
   - **Missing Step 3**: No public getter method in the "Getters" section

3. **Pattern Consistency**: The existing codebase shows a clear pattern for manager integration (seen in AFKManager, ShardManager, HoverStatsManager, etc.), and this pattern was simply not applied to AFKLoungeManager.

4. **Project Rename Side Effect**: The errors appeared "after project rename," suggesting that either:
   - The import was accidentally removed during the rename process
   - The AFKLoungeManager was added after the rename but never fully integrated
   - Build cache issues masked these errors until a clean build was performed

## Correctness Properties

Property 1: Bug Condition - Compilation Success

_For any_ compilation input where the source files reference `SmpPluginExpansion` or call `getAFKLoungeManager()`, the fixed code SHALL compile successfully without "cannot find symbol" errors, because all required imports, field declarations, instantiations, and getter methods exist in the correct locations.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

Property 2: Preservation - Non-Affected Code Behavior

_For any_ code that does NOT involve `SmpPluginExpansion` instantiation or `getAFKLoungeManager()` calls, the fixed code SHALL produce exactly the same compiled bytecode and runtime behavior as the original code, preserving all existing manager integrations, PlaceholderAPI registrations, and initialization sequences.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `src/main/java/com/bx/smpPlugin/SmpPlugin.java`

**Specific Changes**:

1. **Add Missing Import Statement**:
   - **Location**: Import section (after line 6, where other API imports are located)
   - **Code to Add**: `import com.bx.smpPlugin.api.SmpPluginExpansion;`
   - **Pattern**: Place it immediately after the existing line `import com.bx.smpPlugin.api.HideExpansion;` to maintain alphabetical ordering within the API imports group

2. **Add Manager Field Declaration**:
   - **Location**: "Managers" section (after line 94, where `AFKManager afkManager;` is declared)
   - **Code to Add**: `private AFKLoungeManager afkLoungeManager;`
   - **Pattern**: Follow the exact pattern used for AFKManager since they are related features

3. **Add Manager Instantiation**:
   - **Location**: `onEnable()` method, in the "Gameplay managers" section (after line 244, where `afkManager = new AFKManager(this);` is located)
   - **Code to Add**: `afkLoungeManager = new AFKLoungeManager(this);`
   - **Pattern**: Place immediately after AFKManager instantiation since they are related features (AFK functionality)

4. **Add Getter Method**:
   - **Location**: "Getters" section (after line 1050, where `getAFKManager()` getter is located)
   - **Code to Add**:
     ```java
     public AFKLoungeManager getAFKLoungeManager() {
         return afkLoungeManager;
     }
     ```
   - **Pattern**: Follow the exact pattern used for AFKManager getter (simple return statement, no null checks)

5. **Add Reload Logic (if needed)**:
   - **Location**: `reloadAllPluginConfigurations()` method (check if AFKLoungeManager has a `reload()` method)
   - **Investigation Required**: Check if `AFKLoungeManager` implements a `reload()` method
   - **If Yes**: Add `afkLoungeManager.reload();` in the appropriate location in the reload sequence
   - **If No**: No changes needed

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, confirm that the current code produces compilation errors (exploratory checking), then verify that the fixed code compiles successfully and produces a working JAR artifact while preserving all existing functionality.

### Exploratory Bug Condition Checking

**Goal**: Surface the compilation errors BEFORE implementing the fix to confirm the root cause analysis.

**Test Plan**: Run `mvn clean compile` on the UNFIXED code and collect the compiler error messages. This will confirm:
- The exact error messages for `SmpPluginExpansion`
- The exact error messages for `getAFKLoungeManager()`
- The total count of compilation errors (should be 31)
- The exact file locations and line numbers

**Test Cases**:
1. **Clean Build Test**: Run `mvn clean compile` (will fail on unfixed code)
2. **Error Count Verification**: Verify that exactly 31 errors are reported
3. **Import Error Verification**: Verify error "cannot find symbol: class SmpPluginExpansion" at SmpPlugin.java:287
4. **Getter Error Verification**: Verify errors "cannot find symbol: method getAFKLoungeManager()" in 4 files at expected line numbers

**Expected Counterexamples**:
- SmpPluginExpansion cannot be resolved due to missing import
- getAFKLoungeManager() method does not exist in SmpPlugin class
- Build fails with 31 total errors

### Fix Checking

**Goal**: Verify that after applying the fix, all compilation errors are resolved and the build succeeds.

**Pseudocode:**
```
FOR ALL sourceFiles IN project DO
  result := compile(sourceFiles_fixed)
  ASSERT result.success == true
  ASSERT result.errors.count == 0
END FOR

result := buildJar(sourceFiles_fixed)
ASSERT result.success == true
ASSERT result.jarFile.exists == true
```

**Test Cases**:
1. **Compilation Success**: Run `mvn clean compile` and verify it completes with 0 errors
2. **JAR Build Success**: Run `mvn clean package` and verify it produces a JAR artifact
3. **Import Resolution**: Verify SmpPluginExpansion is properly imported and instantiated at line 287
4. **Getter Resolution**: Verify all 30 calls to `getAFKLoungeManager()` compile successfully in all 4 files
5. **Runtime Instantiation**: Verify that AFKLoungeManager is instantiated during plugin startup (check plugin logs)

### Preservation Checking

**Goal**: Verify that all code not involving SmpPluginExpansion or getAFKLoungeManager() continues to behave exactly as before.

**Pseudocode:**
```
FOR ALL managerGetters IN SmpPlugin WHERE getter != "getAFKLoungeManager" DO
  ASSERT getter.exists_in_original == getter.exists_in_fixed
  ASSERT getter.returnType_original == getter.returnType_fixed
END FOR

FOR ALL placeholderExpansions IN onEnable() WHERE expansion != "SmpPluginExpansion" DO
  ASSERT expansion.registered_original == expansion.registered_fixed
END FOR

FOR ALL managerInstantiations IN onEnable() WHERE manager != "AFKLoungeManager" DO
  ASSERT manager.initOrder_original == manager.initOrder_fixed
END FOR
```

**Testing Approach**: Property-based testing is NOT practical for this bugfix because:
- We are only adding missing code, not modifying existing logic
- The changes are purely structural (imports, declarations, assignments)
- All existing code paths remain unchanged
- Manual verification and compilation success are sufficient

**Test Plan**: Verify through code review and successful compilation that no existing code was modified.

**Test Cases**:
1. **Manager Getter Preservation**: Verify all 70+ existing manager getters remain unchanged (code review)
2. **PlaceholderAPI Expansion Preservation**: Verify all 6 existing expansions remain unchanged (code review)
3. **Initialization Order Preservation**: Verify manager initialization order in onEnable() is unchanged except for the new AFKLoungeManager line (code review)
4. **Existing Tests Pass**: Run existing unit/integration tests (if any) to verify no regressions
5. **Plugin Startup**: Start the plugin on a test server and verify all existing features work correctly

### Unit Tests

- Verify SmpPlugin.java compiles without errors
- Verify SmpPluginExpansion import is present
- Verify AFKLoungeManager field is declared
- Verify getAFKLoungeManager() method exists and returns non-null after initialization
- Verify all dependent files (ScoreboardManager, TablistManager, AFKLoungeCommand, AFKLoungeTask) compile without errors

### Property-Based Tests

Not applicable for this bugfix. The changes are purely structural additions to resolve compilation errors, not logic changes that would benefit from property-based testing. The "preservation" aspect is verified through compilation success and code review rather than runtime property checking.

### Integration Tests

- Build the plugin with `mvn clean package` and verify success
- Load the plugin on a test server and verify it enables without errors
- Verify AFKLoungeManager is properly instantiated (check logs)
- Verify PlaceholderAPI expansions register correctly (check logs for "PlaceholderAPI expansion registered")
- Verify AFKLounge-related commands work correctly (`/afklounge` command, if registered)
- Verify AFKLoungeTask runs correctly (check for shard rewards in AFK lounge)
- Verify ScoreboardManager and TablistManager use AFKLounge data correctly (check player scoreboards/tablists when in AFK lounge)
