# Bugfix Requirements Document

## Introduction

After renaming the project from FinnishSmp to SmpPlugin, the Maven build fails with 31 compilation errors across multiple files. The errors fall into two distinct categories:

1. **Missing Import Error (1 error)**: `SmpPluginExpansion` class cannot be found at line 287 in `SmpPlugin.java` because the import statement is missing, even though the class file exists at `com.bx.smpPlugin.api.SmpPluginExpansion.java`.

2. **Missing Manager Field and Getter (30 errors)**: The `AFKLoungeManager` class exists in the managers package, but the main plugin class lacks the required private field declaration, getter method, and instantiation logic. This causes 30 compilation errors across 4 files (`ScoreboardManager.java`, `TablistManager.java`, `AFKLoungeCommand.java`, `AFKLoungeTask.java`) that attempt to call `plugin.getAFKLoungeManager()`.

These compilation errors prevent the build from completing successfully, blocking all development and deployment activities. The errors must be fixed to restore build functionality and maintain existing features related to AFK Lounge functionality.

## Bug Analysis

### Current Behavior (Defect)

#### Issue 1: Missing SmpPluginExpansion Import

1.1 WHEN `SmpPlugin.java` attempts to instantiate `SmpPluginExpansion` at line 287 THEN the compiler fails with error "cannot find symbol: class SmpPluginExpansion" because the import statement `com.bx.smpPlugin.api.SmpPluginExpansion` is missing from the import section.

#### Issue 2: Missing AFKLoungeManager Integration

1.2 WHEN `ScoreboardManager.java` calls `plugin.getAFKLoungeManager()` at lines 365 and 635 THEN the compiler fails with error "cannot find symbol: method getAFKLoungeManager()" because the method does not exist in `SmpPlugin.java`.

1.3 WHEN `TablistManager.java` calls `plugin.getAFKLoungeManager()` at line 538 THEN the compiler fails with error "cannot find symbol: method getAFKLoungeManager()" because the method does not exist in `SmpPlugin.java`.

1.4 WHEN `AFKLoungeCommand.java` calls `plugin.getAFKLoungeManager()` at multiple locations (lines 26, 65, 79, 96, 104, 111, 115, 122, 133, 138, 143, 145, 146) THEN the compiler fails with error "cannot find symbol: method getAFKLoungeManager()" because the method does not exist in `SmpPlugin.java`.

1.5 WHEN `AFKLoungeTask.java` calls `plugin.getAFKLoungeManager()` at multiple locations (lines 30, 39, 42, 43, 56, 57, 68, 71, 74) THEN the compiler fails with error "cannot find symbol: method getAFKLoungeManager()" because the method does not exist in `SmpPlugin.java`.

1.6 WHEN Maven build command `mvn clean package` is executed THEN the build fails with 31 total compilation errors and does not produce a JAR artifact.

### Expected Behavior (Correct)

#### Issue 1: SmpPluginExpansion Should Be Imported

2.1 WHEN `SmpPlugin.java` is compiled THEN the system SHALL successfully resolve the `SmpPluginExpansion` class reference at line 287 because the import statement `import com.bx.smpPlugin.api.SmpPluginExpansion;` exists in the imports section.

#### Issue 2: AFKLoungeManager Should Be Integrated

2.2 WHEN `SmpPlugin.java` is loaded THEN the system SHALL have a private field `private AFKLoungeManager afkLoungeManager;` declared in the Managers section alongside other manager fields.

2.3 WHEN any class calls `plugin.getAFKLoungeManager()` THEN the system SHALL return the `AFKLoungeManager` instance because a public getter method `public AFKLoungeManager getAFKLoungeManager() { return afkLoungeManager; }` exists in `SmpPlugin.java`.

2.4 WHEN the plugin's `onEnable()` method executes THEN the system SHALL instantiate the `AFKLoungeManager` with `afkLoungeManager = new AFKLoungeManager(this);` in the appropriate initialization sequence (after `AFKManager` initialization, as they are related features).

2.5 WHEN Maven build command `mvn clean package` is executed THEN the system SHALL complete successfully without compilation errors and produce a valid JAR artifact.

### Unchanged Behavior (Regression Prevention)

3.1 WHEN all existing manager fields (other than `afkLoungeManager`) are accessed through their respective getter methods THEN the system SHALL CONTINUE TO return their manager instances correctly as they did before the fix.

3.2 WHEN all existing PlaceholderAPI expansions (`EconomyExpansion`, `FinnishSmpExpansion`, `FsmpExpansion`, `HideExpansion`, `EconomyLeaderboardExpansion`, `EconomyRankExpansion`) are registered in `onEnable()` THEN the system SHALL CONTINUE TO register them successfully as they did before the fix.

3.3 WHEN all other manager classes (`ScoreboardManager`, `TablistManager`, `AFKLoungeCommand`, `AFKLoungeTask`) call their respective getter methods for other managers THEN the system SHALL CONTINUE TO resolve those methods without compilation errors as they did before the fix.

3.4 WHEN the plugin initialization sequence in `onEnable()` executes THEN the system SHALL CONTINUE TO initialize all other managers in their correct order and functional state as they did before the fix.

3.5 WHEN `AFKLoungeManager` class is instantiated THEN the system SHALL CONTINUE TO load its configuration from `afk-lounge.yml`, initialize its data structures, and provide all its existing methods (`isEnabled()`, `isPlayerInLounge()`, `getScoreboardLines()`, etc.) exactly as the class is currently implemented.

3.6 WHEN Maven build is executed with all other source files unchanged THEN the system SHALL CONTINUE TO compile those files successfully without introducing new compilation errors.
