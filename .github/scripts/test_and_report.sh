#!/usr/bin/env bash
set -e

UPSTREAM_VERSION="${UPSTREAM_VERSION:-unknown}"
CURRENT_VERSION="${CURRENT_VERSION:-unknown}"

# Configure git identity
git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"

# Run tests and catch exit code safely without crashing
set +e
echo "Running ./gradlew test to verify binary and API compatibility with JCEF ${UPSTREAM_VERSION}..."
chmod +x gradlew
./gradlew test --no-daemon > build_test.log 2>&1
TEST_EXIT_CODE=$?
set -e

if [ $TEST_EXIT_CODE -eq 0 ]; then
  # ─────────────────────────────────────────────────────────────
  # SUCCESS PATH: ZERO BREAKING CHANGES
  # ─────────────────────────────────────────────────────────────
  echo "✅ All tests and compilation passed successfully!"
  echo "$UPSTREAM_VERSION" > jcef-version.txt

  BRANCH_NAME="update/jcef-${UPSTREAM_VERSION}"
  git checkout -B "$BRANCH_NAME"
  git add kromium-core/libs/jcef.jar jcef-version.txt
  git commit -m "chore(deps): update JCEF to ${UPSTREAM_VERSION}

- Downloaded certified jcef.jar from JetBrains Maven (${UPSTREAM_VERSION})
- Verified all unit tests and compilation pass with 0 errors"

  git push -u origin "$BRANCH_NAME" --force

  # Create Pull Request if one doesn't exist already
  PR_EXISTS=$(gh pr list --head "$BRANCH_NAME" --json number -q '.[0].number' 2>/dev/null || echo "")
  if [ -z "$PR_EXISTS" ]; then
    gh pr create \
      --title "feat(deps): upgrade JCEF engine to ${UPSTREAM_VERSION}" \
      --body "### Automated JCEF Engine Upgrade

This automated PR updates the pure \`jcef.jar\` binary from JetBrains Maven repository from **${CURRENT_VERSION}** to **${UPSTREAM_VERSION}**.

- ✅ Official certified binary from \`org.jetbrains.intellij.deps.jcef:jcef\`
- ✅ \`./gradlew test\` passed cleanly with zero compilation or API regressions

Ready for review and merge." \
      --base master \
      --head "$BRANCH_NAME"
  fi

  if [ -n "$GITHUB_STEP_SUMMARY" ]; then
    echo "### 🎉 Automated JCEF Update Ready" >> "$GITHUB_STEP_SUMMARY"
    echo "Upstream JCEF \`${UPSTREAM_VERSION}\` was verified and passed all tests. A Pull Request has been created on branch \`${BRANCH_NAME}\`." >> "$GITHUB_STEP_SUMMARY"
  fi

else
  # ─────────────────────────────────────────────────────────────
  # API BREAKING CHANGE PATH: SAFE DEVELOPER NOTIFICATION
  # ─────────────────────────────────────────────────────────────
  echo "⚠️ Compilation or tests failed due to upstream JCEF API changes."
  tail -n 40 build_test.log

  # Extract compiler errors
  ERROR_LOG=$(grep -E 'e: |error: |FAILURE' build_test.log | head -n 30 || true)

  # Create developer branch with new jcef.jar so dev can immediately start fixing
  FIX_BRANCH="fix/jcef-api-change-${UPSTREAM_VERSION}"
  git checkout -B "$FIX_BRANCH"
  echo "$UPSTREAM_VERSION" > jcef-version.txt
  git add kromium-core/libs/jcef.jar jcef-version.txt
  git commit -m "WIP: update jcef.jar to ${UPSTREAM_VERSION} (API changes detected)

Automated commit containing the new jcef.jar for ${UPSTREAM_VERSION}.
Requires developer attention to adapt KromiumClient / handler signatures."

  git push -u origin "$FIX_BRANCH" --force

  # Search for existing issue safely (avoiding search query syntax errors with special symbols)
  SEARCH_TERM="Upstream JCEF API Change"
  EXISTING_ISSUE=$(gh issue list --search "$SEARCH_TERM in:title ${UPSTREAM_VERSION}" --json number -q '.[0].number' 2>/dev/null || echo "")

  ISSUE_TITLE="Upstream JCEF API Change Detected (${UPSTREAM_VERSION})"
  ISSUE_BODY=$(cat <<EOF
### 🚨 Upstream JCEF API Change Detected

The automated update pipeline detected a new certified upstream JCEF release: **${UPSTREAM_VERSION}** (current: **${CURRENT_VERSION}**).

During verification, \`./gradlew test\` reported compilation or interface compatibility issues.

#### 📋 Compiler Diagnostics Snippet:
\`\`\`text
${ERROR_LOG}
\`\`\`

#### 🛠️ How to Fix:
1. Check out the automated branch containing the new \`jcef.jar\`:
   \`\`\`bash
   git fetch origin
   git checkout ${FIX_BRANCH}
   \`\`\`
2. Open \`dev.daviante.kromium.presentation.browser.KromiumClient.kt\` and adapt any modified handler method signatures.
3. Run \`./gradlew test\` to verify.
4. Commit and merge back to \`master\`!

*The pipeline completed safely with status code 0.*
EOF
)

  if [ -z "$EXISTING_ISSUE" ]; then
    gh issue create \
      --title "$ISSUE_TITLE" \
      --body "$ISSUE_BODY" \
      --label "bug" 2>/dev/null || gh issue create --title "$ISSUE_TITLE" --body "$ISSUE_BODY"
    echo "Created notification issue for developers."
  else
    echo "Issue already exists (#$EXISTING_ISSUE), skipping duplicate issue creation."
  fi

  if [ -n "$GITHUB_STEP_SUMMARY" ]; then
    echo "### ⚠️ Upstream JCEF API Change Detected" >> "$GITHUB_STEP_SUMMARY"
    echo "Upstream JCEF \`${UPSTREAM_VERSION}\` contains API modifications. The pipeline safely handled the event, created branch \`${FIX_BRANCH}\` with the new \`jcef.jar\`, and opened an issue for developer attention." >> "$GITHUB_STEP_SUMMARY"
  fi
fi

# Exit with code 0 to keep the run marked as successful and clean
exit 0
