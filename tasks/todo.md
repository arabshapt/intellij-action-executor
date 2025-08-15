# IntelliJ Action Executor - README Update Tasks

## Completed Tasks
- [x] Review README.md for outdated version references
- [x] Update version badge from 1.1.3 to 1.1.4
- [x] Update download filename in installation instructions  
- [x] Add section about improved context handling in v1.1.4
- [x] Review if any other documentation needs updates

## Review Section

### Summary of Changes Made

#### Version Updates
1. **Version Badge** - Updated from v1.1.3 to v1.1.4 in the shields.io badge (line 3)
2. **Installation Instructions** - Updated download filename from `intellijPlugin-1.1.3.zip` to `intellijPlugin-1.1.4.zip` (line 34)

#### Documentation Enhancements
1. **Added "What's New in v1.1.4" Section** - Created a new section highlighting the key improvements in the latest version:
   - Live DataContext retrieval from focused components
   - Fixed tree navigation actions in chains
   - Improved context-dependent action support
   - Universal solution benefiting all actions

2. **Updated Troubleshooting Section** - Added note in "Actions Not Executing" section mentioning that v1.1.4 fixes most context-dependent action issues

### Key Improvements in v1.1.4
The main improvement is the switch from static DataContext creation to live context retrieval using DataManager. This ensures actions receive the current state of the focused component rather than a snapshot, fixing issues with:
- Tree navigation actions (Tree-selectFirst, Tree-selectNext)
- Git operations (Git.CompareWithBranch)
- Other context-dependent actions

All changes maintain backward compatibility while providing better action execution reliability.