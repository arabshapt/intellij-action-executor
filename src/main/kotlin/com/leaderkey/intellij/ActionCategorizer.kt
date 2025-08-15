package com.leaderkey.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

@Service
class ActionCategorizer {
    companion object {
        private val LOG = Logger.getInstance(ActionCategorizer::class.java)
        
        // Delay constants in milliseconds
        const val DELAY_NONE = 0L
        const val DELAY_MINIMAL = 25L
        const val DELAY_QUICK_UI = 50L
        const val DELAY_TOOL_WINDOW = 100L
        const val DELAY_TREE_LIST = 150L
        const val DELAY_ASYNC_OPERATION = 500L
        
        // Action categories
        private val instantActions = setOf(
            // File operations
            "SaveAll", "SaveDocument", "SaveAs", "ExportToFile",
            
            // Clipboard operations
            "\$Copy", "\$Paste", "\$Cut", "CopyPaths", "CopyReference", 
            "CopyAbsolutePath", "CopyFileName", "CopyPathFromRepositoryRootProvider",
            
            // Navigation (no UI update)
            "GotoDeclaration", "GotoImplementation", "GotoSuperMethod",
            "GotoTypeDeclaration", "ShowUsages", "FindUsages",
            
            // Editor cursor movement
            "EditorLeft", "EditorRight", "EditorUp", "EditorDown",
            "EditorLineStart", "EditorLineEnd", "EditorPageUp", "EditorPageDown",
            "EditorTextStart", "EditorTextEnd", "EditorNextWord", "EditorPreviousWord",
            
            // Tab navigation
            "NextTab", "PreviousTab", "CloseContent", "CloseActiveTab",
            
            // Bookmarks
            "ToggleBookmark", "ShowBookmarks", "GotoNextBookmark", "GotoPreviousBookmark",
            
            // Code folding
            "CollapseRegion", "ExpandRegion", "CollapseAllRegions", "ExpandAllRegions"
        )
        
        private val quickUIActions = setOf(
            // Code editing with immediate UI feedback
            "ReformatCode", "OptimizeImports", "RearrangeCode", "AutoIndentLines",
            "CommentByLineComment", "CommentByBlockComment",
            
            // Editor UI updates
            "EditorToggleShowWhitespaces", "EditorToggleShowLineNumbers",
            "EditorToggleUseSoftWraps", "EditorToggleShowIndentLines",
            
            // Quick UI toggles
            "ViewNavigationBar", "ViewStatusBar", "ViewToolBar",
            
            // Selection
            "EditorSelectWord", "EditorUnSelectWord", "\$SelectAll"
        )
        
        private val toolWindowActions = setOf(
            "ActivateProjectToolWindow", "ActivateStructureToolWindow",
            "ActivateFavoritesToolWindow", "ActivateVersionControlToolWindow",
            "ActivateTerminalToolWindow", "ActivateDebugToolWindow",
            "ActivateRunToolWindow", "ActivateTODOToolWindow",
            "ActivateProblemsViewToolWindow", "ActivateFindToolWindow",
            "ActivateServicesToolWindow", "ActivateBuildToolWindow",
            
            // Window operations
            "MaximizeToolWindow", "HideActiveWindow", "HideAllWindows",
            "JumpToLastWindow", "StretchWindowToLeft", "StretchWindowToRight"
        )
        
        private val treeListActions = setOf(
            "Tree-selectFirst", "Tree-selectLast", "Tree-selectNext", "Tree-selectPrevious",
            "Tree-selectParent", "Tree-selectChild",
            "List-selectFirstRow", "List-selectLastRow", "List-selectNextRow", "List-selectPreviousRow",
            "\$Delete" // When in tree/list context
        )
        
        private val asyncTriggerActions = setOf(
            // Build & Compile
            "CompileDirty", "CompileProject", "BuildProject", "RebuildProject",
            "MakeModule", "Compile", "GenerateSources",
            
            // Run & Debug
            "Run", "Debug", "RunClass", "DebugClass", "RunConfiguration",
            "ChooseRunConfiguration", "ChooseDebugConfiguration",
            
            // Version Control (network operations)
            "Git.Pull", "Git.Push", "Git.Fetch", "Git.Merge", "Git.Rebase",
            "Svn.Update", "Svn.Commit", "Hg.Pull", "Hg.Push",
            
            // Indexing & Refresh
            "Synchronize", "Refresh", "RefreshLinkedCppProjects",
            "Maven.Reimport", "Gradle.RefreshDependencies",
            
            // External tools
            "ExternalSystem.RefreshAllProjects", "ExternalSystem.ProjectRefreshAction"
        )
        
        private val dialogActions = setOf(
            // Settings & Configuration
            "ShowSettings", "ShowProjectStructureSettings", "EditRunConfigurations",
            
            // VCS Dialogs
            "CheckinProject", "Git.Branches", "Vcs.ShowHistoryForBlock",
            "Vcs.ShowTabbedFileHistory", "Vcs.ShowHistoryForRevision",
            
            // Refactoring Dialogs
            "RefactoringMenu", "RenameElement", "Move", "ExtractMethod",
            "ExtractInterface", "ExtractSuperclass", "Inline", "ChangeSignature",
            
            // Search Dialogs
            "SearchEverywhere", "FindInPath", "ReplaceInPath", "StructuralSearchPlugin",
            
            // Other Dialogs
            "About", "NewElement", "NewProject", "OpenFile", "OpenProject",
            "PrintExportToHTML", "ExportSettings", "ImportSettings"
        )
        
        fun getOptimalDelay(currentAction: String, nextAction: String?): Long {
            // No delay for last action or dialog actions (already async)
            if (nextAction == null || isDialogAction(currentAction)) {
                return DELAY_NONE
            }
            
            // Both actions are instant - no delay needed
            if (isInstantAction(currentAction) && isInstantAction(nextAction)) {
                LOG.debug("No delay between instant actions: $currentAction -> $nextAction")
                return DELAY_NONE
            }
            
            // Async operations need time to start
            if (triggersAsyncOperation(currentAction)) {
                LOG.debug("Async operation delay for: $currentAction")
                return DELAY_ASYNC_OPERATION
            }
            
            // Tree/List navigation needs time for selection update
            if (isTreeOrListAction(currentAction)) {
                LOG.debug("Tree/List action delay for: $currentAction")
                return DELAY_TREE_LIST
            }
            
            // Tool window activation needs animation time
            if (isToolWindowAction(currentAction)) {
                LOG.debug("Tool window delay for: $currentAction")
                return DELAY_TOOL_WINDOW
            }
            
            // Quick UI actions need minimal delay
            if (isQuickUIAction(currentAction)) {
                LOG.debug("Quick UI delay for: $currentAction")
                return DELAY_QUICK_UI
            }
            
            // Default conservative delay
            LOG.debug("Default delay for: $currentAction")
            return DELAY_QUICK_UI
        }
        
        fun isInstantAction(actionId: String): Boolean {
            return instantActions.contains(actionId) ||
                   (actionId.startsWith("Editor") && 
                    !actionId.contains("Split") && 
                    !actionId.contains("Toggle") &&
                    !quickUIActions.contains(actionId))
        }
        
        fun isQuickUIAction(actionId: String): Boolean {
            return quickUIActions.contains(actionId)
        }
        
        fun isToolWindowAction(actionId: String): Boolean {
            return toolWindowActions.contains(actionId) ||
                   actionId.startsWith("Activate") && actionId.endsWith("ToolWindow")
        }
        
        fun isTreeOrListAction(actionId: String): Boolean {
            return treeListActions.contains(actionId) ||
                   actionId.startsWith("Tree-") ||
                   actionId.startsWith("List-")
        }
        
        fun triggersAsyncOperation(actionId: String): Boolean {
            return asyncTriggerActions.contains(actionId) ||
                   actionId.contains("Run") && !actionId.contains("Configuration") ||
                   actionId.contains("Debug") && !actionId.contains("Configuration") ||
                   actionId.contains("Build") ||
                   actionId.contains("Compile") ||
                   actionId.contains("Make") ||
                   actionId.contains("Refresh") ||
                   actionId.contains("Synchronize") ||
                   actionId.contains("Index")
        }
        
        fun isDialogAction(actionId: String): Boolean {
            return dialogActions.contains(actionId) ||
                   actionId.contains("Show") && actionId.contains("Settings") ||
                   actionId.contains("Show") && actionId.contains("Dialog") ||
                   actionId.contains("Refactor") ||
                   actionId.endsWith("InPath")
        }
        
        fun isUIAction(actionId: String): Boolean {
            // Broader category for backward compatibility
            return isQuickUIAction(actionId) || 
                   isToolWindowAction(actionId) || 
                   isTreeOrListAction(actionId)
        }
    }
}