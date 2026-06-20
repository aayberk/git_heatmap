# Git Heatmap

Git Heatmap is an IntelliJ IDEA plugin that analyzes Git history inside the IDE. It visualizes file activity as a heatmap and provides file, commit, author, code statistics, token usage, and estimated effort analytics.

## Features

- Automatically loads Git history for the selected repository and branch.
- Calculates per-file heat scores.
- Visualizes repository activity as a heatmap.
- Provides sortable Files, Commits, and Authors tables.
- Shows Added, Deleted, and Net line-change metrics.
- Estimates engineering effort with a heuristic model.
- Filters Files and Heatmap by selected author or commit.
- Supports optional date-range filtering.
- Supports PR Overlay by comparing two branches.
- Discovers multiple Git repositories under the same project root.
- Supports an aggregate `All repositories` workspace view.
- Shows Code Statistics with file format distribution, contribution calendar, and hourly commit analysis.
- Shows Token Usage for Claude and Codex with token and estimated price views.

## Requirements

- `JDK 21`
- `Git`
- `IntelliJ IDEA 2025.3+`
- A Gradle-capable macOS, Linux, or Windows environment

## Technical Details

- Kotlin JVM
- IntelliJ Platform Plugin
- Plugin ID: `com.githeatmap`
- Plugin version: `1.2.0`
- Build target: `IntelliJ IDEA 2026.1`
- Compatibility: `2025.3+`
- Since build: `253`
- Tool window: `Git Heatmap`

## Setup

Run commands from the repository root:

```bash
cd /Users/aliayberkunsalan/Desktop/aliayberk/intelij_git_analyzer_kiro
```

Check Java:

```bash
java -version
./gradlew -version
```

If needed, point Gradle to JDK 21 on macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

## Run

Run the plugin in a sandbox IDE:

```bash
./gradlew runIde
```

To run from IntelliJ IDEA:

1. Open this project in IntelliJ IDEA.
2. Select `JDK 21` as the Gradle JVM.
3. Run the `runIde` Gradle task.

## Build and Test

Compile Kotlin:

```bash
./gradlew compileKotlin
```

Run tests and quality checks:

```bash
./gradlew check
```

Build the plugin distribution:

```bash
./gradlew buildPlugin
```

Run a full build:

```bash
./gradlew build
```

## Usage

Open an IntelliJ project that contains one or more Git repositories, then open the `Git Heatmap` tool window. The plugin discovers repositories and automatically starts loading Git history for the selected repository and branch. The `Load` button remains available for manual reloads.

### Top Controls

- `Repository`: Choose a single repository or `All repositories`.
- `Branch`: Choose the branch history to analyze in single-repository mode.
- `Load`: Reload Git history for the selected scope.
- `PR Overlay`: Compare two branches in the selected repository.
- `Clear Overlay`: Remove the active PR overlay.
- `Clear Author Filter`: Remove the active author filter.
- `Clear Commit Filter`: Remove the active commit filter.
- `Date range`: Optional start and end date filter.

If no date range is selected, the plugin uses all loaded history. If only one side of the range is selected, the filter is open-ended.

## Tabs

### Heatmap

- Each cell represents a file.
- Hotter colors indicate higher change activity.
- Hover shows file details.
- Clicking a cell selects the same file in the `Files` tab.
- Active author or commit filters also scope the heatmap.

### Files

Main columns:

- `File`
- `Heat`
- `Commits`
- `Added`
- `Deleted`
- `Net`
- `Authors`

Numeric columns sort numerically. When PR overlay, author filter, or commit filter is active, the file list is scoped accordingly.

### Commits

Main columns:

- `Hash`
- `Repo` in aggregate mode
- `Author`
- `Date`
- `Files`
- `Added`
- `Deleted`
- `Effort`
- `Message`

The message column is wide and shows the full commit message in a tooltip. Double-clicking a commit filters Files and Heatmap to that commit's changed files.

### Authors

Main columns:

- `#`
- `Author`
- `Commits`
- `Added`
- `Deleted`
- `Net`
- `Avg/Commit`
- `Effort Min`
- `Effort Max`

Double-clicking an author filters Files, Commits, Heatmap, and summary to that author. `Clear Author Filter` removes the filter.

### Code Statistics

- Shows file format and language-style distribution.
- Provides a GitHub-like contribution calendar.
- Contribution calendar can show `Commits` or `Effort`.
- Shows average hourly commit activity for the last 30 days.
- Respects the active author filter when present.

### Token Usage

Reads local usage files for Claude and Codex.

- `Scope`: `Global` or current `Repository`.
- `Group`: `Monthly` or `Daily`.
- `Metric`: `Tokens` or `Price`.
- `Providers`: Claude and Codex visibility.
- Monthly view shows all monthly history.
- Daily chart shows the last 3 months.
- Daily tables list all daily history.
- Claude and Codex are shown as separate tables.
- Tables include input, cache write, cache read, output, total, and price fields.

Token Usage loads in a background thread so the UI does not freeze. Price values are estimates based on known model pricing.

## PR Overlay

`PR Overlay` compares:

- `Base branch`
- `Target branch`

When overlay is active:

- Files shows only diff files.
- Commits shows only commits in the branch range.
- Authors are calculated from the same range.
- Summary reflects the PR scope.

PR Overlay is disabled in `All repositories` mode.

## Multi-Repository Mode

The plugin can discover Git repositories under the project root even when the project root itself is not a Git repository.

- Single-repository mode enables branch selection.
- `All repositories` merges commits into an aggregate workspace view.
- Aggregate file paths are prefixed by repository to avoid ambiguity.
- The Commits tab shows a `Repo` column in aggregate mode.
- PR Overlay is not available in aggregate mode.

## Estimated Effort

Effort is heuristic and represents an estimated range for a 5-year mid-senior developer baseline. It is not actual tracked time.

Effort is used in:

- Commit rows
- Author totals
- Summary
- PR overlay scope

## Known Limitations

- PR Overlay works only in single-repository mode.
- Effort values are estimates, not actual time.
- Very large Git histories can take time during the first load.
- Token Usage filter changes run in the background, but raw event caching would be needed for true recomputation speedups.
- Token prices are calculated from known model pricing; fallback pricing may be used for newer unknown models.

## Troubleshooting

### Build fails because of Java version

```bash
java -version
./gradlew -version
```

Make sure Gradle runs with `JDK 21`.

### Tool window does not appear

- Start the sandbox IDE with `./gradlew runIde`.
- Confirm the plugin is installed in the sandbox IDE.
- Look for the `Git Heatmap` tool window.

### No data is shown

- Confirm the project contains at least one Git repository.
- Confirm the selected branch exists and is accessible.
- Try manual reload with `Load`.

### PR Overlay returns nothing

- Check both base and target branch names.
- Make sure you are not in `All repositories` mode.

## Useful Commands

```bash
./gradlew runIde
./gradlew compileKotlin
./gradlew check
./gradlew buildPlugin
./gradlew build
```
