# Git Heatmap

Git Heatmap is an IntelliJ IDEA plugin prototype that analyzes Git history and visualizes repository activity as a heatmap. It also shows file, commit, author, and effort-level analytics inside a dedicated tool window.

## What It Does

- Reads Git history from the selected repository
- Calculates per-file activity and heat scores
- Displays a file heatmap
- Shows file-level analytics with sortable columns
- Shows commit-level analytics with estimated effort
- Shows author-level analytics with added, deleted, net, and effort totals
- Supports date-range filtering
- Supports PR-style branch comparison with overlay highlighting
- Supports multiple Git repositories under the same project root
- Supports an aggregate `All repositories` scope for workspace-wide analysis

## Requirements

- `JDK 21`
- `Git`
- `IntelliJ IDEA 2026.1+`
- A Gradle-capable environment on macOS, Linux, or Windows

## Build Target

- IntelliJ Platform: `2026.1`
- Since build: `261`
- Plugin ID: `com.githeatmap`
- Tool window: `Git Heatmap`

## Setup

Run commands from the repository root:

```bash
cd /Users/aliayberkunsalan/Desktop/aliayberk/intelij_git_analyzer_kiro
```

Check Java:

```bash
java -version
```

If needed, point Gradle to JDK 21. Example on macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

## Development Commands

Run the plugin in a sandbox IDE:

```bash
./gradlew runIde
```

Compile the project:

```bash
./gradlew compileKotlin
```

Run tests and coverage gates:

```bash
./gradlew check
```

Build the plugin distribution:

```bash
./gradlew build
```

## How to Use

Open an IntelliJ project, then open the `Git Heatmap` tool window.

### Top controls

- `Repository`: choose the active Git root
- `Branch`: choose which branch history will be loaded
- `Load`: read Git history and compute metrics
- `PR Overlay`: compare two branches inside the selected repository
- `Clear Overlay`: remove the active PR scope
- `Clear Author Filter`: clear the active author filter
- `Clear Commit Filter`: clear the active commit filter
- `Date range`: optional start and end dates

If neither start nor end is selected, the plugin analyzes all available history for the loaded branch.

## Tabs

### Heatmap

- Each cell represents a file
- Hotter files are more active according to the calculated heat score
- Hover shows tooltip details
- Clicking a cell selects the same file in the `Files` tab
- Double-click behavior resolves and opens the underlying file

### Files

Columns:

- `File`
- `Heat`
- `Commits`
- `Added`
- `Deleted`
- `Net`
- `Authors`

Behavior:

- Numeric columns are sortable
- When a PR overlay is active, the list is limited to overlay files
- When an author is selected, the list is limited to that author's files
- When a commit is selected, the list is limited to that commit's files

### Commits

Columns:

- `Hash`
- `Repo` in aggregate mode
- `Author`
- `Date`
- `Files`
- `Added`
- `Deleted`
- `Effort`
- `Message`

Behavior:

- Double-clicking a commit activates a commit filter
- The `Files` and `Heatmap` tabs switch to that commit's files
- The message column shows the full commit message in a tooltip

### Authors

Columns:

- `#`
- `Author`
- `Commits`
- `Added`
- `Deleted`
- `Net`
- `Avg/Commit`
- `Effort Min`
- `Effort Max`

Behavior:

- Columns are sortable
- Double-clicking an author activates an author filter
- The `Files`, `Commits`, `Heatmap`, and summary views switch to that author's scope

## Summary and Filters

The summary line updates according to the active scope:

- full loaded history
- selected author
- selected commit
- PR overlay scope

The right-side labels indicate active filters for:

- PR overlay
- author
- commit

## PR Overlay

`PR Overlay` compares:

- `base branch`
- `target branch`

The plugin uses:

- branch diff for changed files
- commit range for PR-scoped commit and author analytics

When PR overlay is active:

- `Files` shows only changed files
- `Commits` shows only commits in the compared range
- `Authors` shows only authors in the compared range
- the summary reflects the PR scope

`Clear Overlay` returns the tool window to the current branch/date-range analysis.

### Aggregate mode limitation

`PR Overlay` is available only in single-repository mode.

When `Repository = All repositories`:

- overlay is disabled
- branch history is loaded from each discovered repository
- results are merged into one aggregate view

## Multi-Repository Support

If the project root itself is not a Git repository but contains multiple Git repositories, the plugin can discover them.

Modes:

- `Single repository`
- `All repositories`

In aggregate mode:

- commits from all discovered repositories are merged
- file paths are prefixed so duplicate relative paths remain distinguishable
- the `Commits` tab shows a `Repo` column

## Estimated Effort

The plugin includes a heuristic effort model for a mid-to-senior developer baseline.

It is used in:

- commit rows
- author totals
- summary line
- PR overlay summary

This is an estimated engineering effort range, not tracked or actual time.

## Expected Workflow

1. Select a repository or `All repositories`
2. Select a branch if you are in single-repository mode
3. Click `Load`
4. Review the heatmap, files, commits, and author analytics
5. Optionally set a date range
6. Optionally activate `PR Overlay`
7. Optionally drill down by author or commit

## Known Limitations

- PR overlay is disabled in aggregate mode
- Effort values are heuristic estimates, not real recorded time
- Very large repositories may still need additional performance tuning
- Some IntelliJ UI APIs currently emit deprecation warnings during compilation

## Troubleshooting

### Build fails because of Java version

Check:

```bash
java -version
./gradlew -version
```

Make sure Gradle runs with `JDK 21`.

### No data is shown

- Confirm the selected project contains one or more Git repositories
- Confirm the selected branch exists
- Click `Load` again after changing repository or branch

### PR Overlay returns nothing

- Verify both branch names exist in the selected repository
- Make sure you are not in `All repositories` mode

### The tool window does not appear

- Run the plugin in the sandbox IDE with `runIde`
- Reopen the project in the sandbox IDE
- Look for the `Git Heatmap` tool window

## Useful Commands

```bash
./gradlew runIde
./gradlew compileKotlin
./gradlew check
./gradlew build
```
