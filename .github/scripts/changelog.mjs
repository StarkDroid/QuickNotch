#!/usr/bin/env node
/**
 * Turns a merged pull request into a release: the next version number, a
 * user-facing changelog entry, the cumulative `changelog.json` the app fetches,
 * and the Markdown body for the GitHub release.
 *
 * Two things make this script worth its own file rather than a pile of shell:
 *
 *  • The changelog is *published history*, not a summary of the diff. The app
 *    shows every release up to the running version, so a new entry has to be
 *    merged into the feed from the previous release, not regenerated from git.
 *  • Pull-request prose is full of implementation detail. The copy that ends up
 *    in front of a user is filtered through `humanise`, which drops lines that
 *    name internals (files, types, tools) and keeps the sentence a person would
 *    actually say. When nothing survives, the PR title becomes the entry — it is
 *    written for humans already.
 *
 * Runs with plain `node` (20+) and the repository's `GITHUB_TOKEN`; no
 * dependencies. Writes everything to `build/release/`:
 *
 *   plan.json       what was decided and why (kept as a CI artifact)
 *   notes.md        the GitHub release body
 *   changelog.json  the cumulative feed, with this release at the top
 *
 * Exit codes: 0 for both "release" and "nothing to release"; 1 for a real
 * failure (bad token, unreachable API, malformed previous feed), because a
 * silent fallback to a fresh history would reset the version to 2.0.0.
 */

import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const TOKEN = process.env.GITHUB_TOKEN || process.env.GH_TOKEN || "";
const REPO = process.env.GITHUB_REPOSITORY || "StarkDroid/QuickNotch";
const OUT = process.env.OUT_DIR || "build/release";
const FORCE = /^(1|true|yes)$/i.test(process.env.FORCE || "");
const REQUESTED_BUMP = (process.env.BUMP || "auto").toLowerCase();
const CHANGELOG_ASSET = "changelog.json";
const MAX_CHANGES = 6;
const FIRST_VERSION = "2.0.0";

// Imported by the local self-test below; running the file executes the planner.
const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  main().catch((error) => {
    console.error(`release planner failed: ${error.stack || error.message}`);
    process.exit(1);
  });
}

async function main() {
  mkdirSync(OUT, { recursive: true });
  const pull = await resolvePull();

  if (!pull.merged_at && !FORCE) {
    return skip("not-merged", "The pull request was closed without being merged.");
  }

  const files = await pullFiles(pull.number);
  const appFiles = files.filter(isAppFile);
  if (appFiles.length === 0 && !FORCE) {
    return skip(
      "no-app-changes",
      `Only non-app files changed (${files.map((f) => f.filename).slice(0, 6).join(", ") || "none"}).`
    );
  }

  const previous = await previousFeed();
  const released = previous.releases.find((release) => release.pr === pull.number);

  if (released && !FORCE) {
    return skip(
      "already-released",
      `#${pull.number} is already in the changelog as v${released.version}. Re-run with force to rebuild it.`
    );
  }

  const bump = released ? "none" : bumpFor(pull);
  const version = released ? released.version : nextVersion(previous.latest, bump);
  const entry = await buildEntry(pull, version);
  const feed = mergeEntry(previous, entry);
  const notes = renderNotes(entry, pull);

  writeFileSync(join(OUT, "changelog.json"), `${JSON.stringify(feed, null, 2)}\n`);
  writeFileSync(join(OUT, "notes.md"), `${notes}\n`);
  writeFileSync(
    join(OUT, "plan.json"),
    `${JSON.stringify(
      {
        release: true,
        reason: released ? "rebuild" : "merged-pull-request",
        version,
        tag: `v${version}`,
        bump,
        pull: pull.number,
        title: pull.title,
        appFiles: appFiles.length,
        totalFiles: files.length,
        previous: previous.latest,
        source: previous.source
      },
      null,
      2
    )}\n`
  );

  output("release", "true");
  output("version", version);
  output("tag", `v${version}`);
  output("pr", String(pull.number));
  output("bump", bump);

  console.log(`planned v${version} from #${pull.number} (${bump} bump, ${appFiles.length} app files)`);
}

/* ------------------------------------------------------------------ */
/* Pull-request context                                                */
/* ------------------------------------------------------------------ */

async function resolvePull() {
  const event = readEvent();

  if (event?.pull_request) {
    return { ...event.pull_request, files: undefined };
  }

  const explicit = Number.parseInt(process.env.PR_NUMBER || "", 10);
  if (Number.isFinite(explicit) && explicit > 0) {
    return pull(explicit);
  }

  const list = await api(`/repos/${REPO}/pulls?state=closed&sort=updated&direction=desc&per_page=30`);
  const merged = list.find((candidate) => candidate.merged_at);
  if (!merged) throw new Error("No merged pull request found to release.");
  return pull(merged.number);
}

function readEvent() {
  const path = process.env.GITHUB_EVENT_PATH;
  if (!path) return null;
  try {
    return JSON.parse(readFileSync(path, "utf8"));
  } catch {
    return null;
  }
}

async function pull(number) {
  return api(`/repos/${REPO}/pulls/${number}`);
}

async function pullFiles(number) {
  return paginate(`/repos/${REPO}/pulls/${number}/files?per_page=100`);
}

async function pullCommits(number) {
  return paginate(`/repos/${REPO}/pulls/${number}/commits?per_page=100`);
}

/**
 * The app is `composeApp/` plus the build system that produces its binaries.
 * Everything under `web/`, the docs and `.github/` is deliberately outside the
 * gate: a copy tweak on the site must not ship a new installer.
 */
function isAppFile(file) {
  const path = file.filename || "";
  return (
    path.startsWith("composeApp/") ||
    path.startsWith("gradle/") ||
    path.startsWith("config/detekt/") ||
    path === "settings.gradle.kts" ||
    path === "build.gradle.kts" ||
    path === "gradle.properties" ||
    path === "gradlew" ||
    path === "gradlew.bat"
  );
}

/* ------------------------------------------------------------------ */
/* Versioning                                                          */
/* ------------------------------------------------------------------ */

/**
 * Labels are the explicit signal; a conventional-commit prefix is the fallback.
 * Anything unlabelled is a minor change, so a normal feature PR never needs to
 * remember a label to avoid shipping as a patch.
 */
function bumpFor(entry) {
  if (["major", "minor", "patch"].includes(REQUESTED_BUMP)) return REQUESTED_BUMP;

  const labels = (entry.labels || []).map((label) => label.name.toLowerCase());
  const matches = (words) =>
    labels.some((label) => {
      const tail = label.split(/[:/]/).pop()?.trim();
      return words.includes(tail) || words.includes(label);
    });

  if (matches(["major", "breaking"])) return "major";
  if (matches(["patch", "fix", "bug"])) return "patch";

  const title = (entry.title || "").toLowerCase();
  const body = (entry.body || "").toLowerCase();
  if (/^[a-z]+(\([^)]*\))?!:/.test(title) || /breaking change/.test(body)) return "major";
  if (/^(fix|bug|hotfix)(\([^)]*\))?:/.test(title)) return "patch";

  return "minor";
}

function nextVersion(latest, bump) {
  if (!latest) return FIRST_VERSION;
  const [major, minor, patch] = parts(latest);
  if (bump === "major") return `${major + 1}.0.0`;
  if (bump === "patch") return `${major}.${minor}.${patch + 1}`;
  return `${major}.${minor + 1}.0`;
}

function parts(version) {
  return String(version)
    .trim()
    .replace(/^v/i, "")
    .split(".")
    .map((part) => Number.parseInt(part, 10) || 0);
}

function compareVersions(a, b) {
  const left = parts(a);
  const right = parts(b);
  for (let index = 0; index < Math.max(left.length, right.length); index += 1) {
    const difference = (left[index] || 0) - (right[index] || 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

/* ------------------------------------------------------------------ */
/* Published history                                                   */
/* ------------------------------------------------------------------ */

/**
 * Read the newest changelog the workflow itself published. The release list is
 * sorted by creation date, not semver — a pre-changelog `v3.1.2` may sit above a
 * newer `v2.x` — so the *only* release that counts is one carrying the
 * `changelog.json` asset.
 */
async function previousFeed() {
  const releases = await api(`/repos/${REPO}/releases?per_page=50`);
  const managed = releases.find(
    (release) =>
      !release.draft &&
      !release.prerelease &&
      (release.assets || []).some((asset) => asset.name === CHANGELOG_ASSET)
  );
  if (!managed) return { latest: null, releases: [], source: null };

  const asset = managed.assets.find((candidate) => candidate.name === CHANGELOG_ASSET);
  const response = await fetch(asset.browser_download_url, {
    headers: { "User-Agent": "QuickNotch release planner" }
  });
  if (!response.ok) {
    throw new Error(`previous changelog -> HTTP ${response.status} (${managed.tag_name})`);
  }

  const feed = await response.json();
  if (!feed || !Array.isArray(feed.releases)) {
    throw new Error(`previous changelog in ${managed.tag_name} has no releases array`);
  }

  const latest = feed.latest || feed.releases[0]?.version || null;
  return { latest, releases: feed.releases, source: managed.tag_name };
}

function mergeEntry(previous, entry) {
  const releases = previous.releases.filter((release) => release.version !== entry.version);
  releases.push(entry);
  releases.sort((a, b) => compareVersions(b.version, a.version));

  const latest = releases
    .map((release) => release.version)
    .reduce((highest, version) => (compareVersions(version, highest) > 0 ? version : highest), entry.version);

  return {
    schema: 1,
    latest,
    updatedAt: new Date().toISOString(),
    releases
  };
}

/* ------------------------------------------------------------------ */
/* Copy                                                                */
/* ------------------------------------------------------------------ */

async function buildEntry(entry, version) {
  const title = cleanTitle(entry.title) || `QuickNotch ${version}`;
  const changes = (await collectChanges(entry)).map((text) => ({ kind: kindFor(text), text }));
  if (changes.length === 0) {
    changes.push({ kind: kindFor(title), text: title });
  }

  const ordered = [
    ...changes.filter((change) => change.kind === "new"),
    ...changes.filter((change) => change.kind === "improved"),
    ...changes.filter((change) => change.kind === "fixed")
  ];

  return {
    version,
    date: new Date().toISOString().slice(0, 10),
    title,
    pr: entry.number,
    highlights: ordered.slice(0, 3).map((change) => change.text),
    changes: ordered
  };
}

async function collectChanges(entry) {
  const candidates = [];

  for (const line of bulletLines(entry.body || "")) candidates.push(line);

  if (candidates.length < 3) {
    for (const sentence of sentenceLines(entry.body || "")) candidates.push(sentence);
  }

  if (candidates.length < 2 && entry.number) {
    try {
      const commits = await pullCommits(entry.number);
      for (const commit of commits) {
        const headline = (commit.commit?.message || "").split("\n")[0];
        candidates.push(headline);
      }
    } catch (error) {
      console.warn(`could not read commits for #${entry.number}: ${error.message}`);
    }
  }

  const seen = new Set();
  const changes = [];
  for (const candidate of candidates) {
    const text = humanise(candidate);
    if (!text) continue;
    const key = text.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    changes.push(text);
    if (changes.length >= MAX_CHANGES) break;
  }
  return changes;
}

function cleanTitle(title) {
  let stripped = String(title || "").trim();

  // A branch name ("feature/system-monitor-widget") is not a release title.
  if (/^[a-z0-9._-]+(?:\/[a-z0-9._-]+)+$/i.test(stripped)) {
    stripped = stripped.split("/").slice(-1)[0].replace(/[-_]+/g, " ").trim();
  }
  // Some PRs keep the branch prefix in the title ("Feature/system monitor widget").
  stripped = stripped.replace(
    /^(?:feature|feat|fix|bugfix|hotfix|chore|release|perf|refactor|docs|cursor)[/:_-]\s*/i,
    ""
  );

  stripped = stripped
    .replace(/^(feat|fix|perf|refactor|docs|chore|build|ci|test|style|revert)(\([^)]*\))?!?:\s*/i, "")
    .trim();
  const text = humanise(stripped, { allowTitle: true });
  return text || capitalise(stripped);
}

function bulletLines(body) {
  const lines = [];
  let fenced = false;
  for (const raw of body.split(/\r?\n/)) {
    if (/^\s*```/.test(raw)) {
      fenced = !fenced;
      continue;
    }
    if (fenced) continue;
    const match = raw.match(/^\s*(?:[-*+•]|\d+[.)])\s+(.*)$/);
    if (match) lines.push(match[1]);
  }
  return lines;
}

function sentenceLines(body) {
  const paragraphs = String(body || "")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/```[\s\S]*?```/g, " ")
    .split(/\r?\n\s*\r?\n/);

  const sentences = [];
  for (const paragraph of paragraphs) {
    const text = stripMarkdown(paragraph);
    for (const sentence of text.split(/(?<=[.!?])\s+/)) {
      if (sentence.trim()) sentences.push(sentence.trim());
    }
  }
  return sentences;
}

const JARGON = [
  [/\brefactor(?:ed|ing)?\b/gi, "reworked"],
  [/\bbump(?:ed|s)?\b/gi, "updated"],
  [/\bdeprecat(?:e|ed|ing)\b/gi, "retired"],
  [/\bimplement(?:ed|s|ing)?\b/gi, "added"],
  [/\butili[sz](?:e|ed|es|ing)\b/gi, "uses"],
  [/\binitiali[sz](?:e|ed|es|ing)\b/gi, "sets up"],
  [/\bcorrect(?:ed|s|ing)?\b/gi, "fixed"]
];

/**
 * A sentence survives only if a user could read it without knowing the
 * codebase. Anything that names a file, a type, a shell, a protocol or a test
 * tool is dropped rather than rewritten — "we changed `SystemMetrics.kt`" has no
 * honest user-facing paraphrase, and inventing one is how changelogs start
 * lying.
 */
const TECHNICAL =
  /\b(?:stateflow|composable|viewmodel|coroutine|kotlin|compose desktop|gradle|detekt|jvm|jni|awt|skiko|jpackage|dylib|dll|powershell|pwsh|stdout|stderr|regex|mutex|semaphore|bytecode|classpath|namespace|serializ\w*|deserializ\w*|middleware|manifest|payload|endpoint|schema|workflow|unit tests?|baseline|callback|boolean|null|thread|heap|stack trace|segfault|memory leak|oauth|api|sdk|ipc|rpc|http|https|tcp|udp|json|yaml|xml|ci|cd|commit|branch|merge|pull request)\b/i;

const FILE_PATH =
  /\b[\w./-]+\.(?:kt|kts|ps1|sh|m|h|dylib|dll|json|ya?ml|md|gradle|toml|svg|png|java|xml)\b/i;

const META =
  /^(?:bump(?:ed)? (?:the )?version|update (?:the )?readme|update (?:the )?docs?|merge (?:branch|main)|revert\b|wip\b|todo\b|signed-off-by|co-authored-by|chore\b)/i;

/**
 * Work that happens around the app rather than in it. A PR that adds a widget
 * and also updates the marketing site must not publish "sell QuickNotch" as a
 * user-facing change, so sentences about the site, the docs or the repo's own
 * process are dropped wholesale.
 */
const PROCESS =
  /\b(?:web|website|site|marketing|landing page|pricing|buy ctas?|cta|seo|docs?|readme|roadmap|release notes?|tooling|baseline|detekt|lint|unit tests?|test suite|coverage|github actions?|tour|cloudflare)\b/i;

/** Dot-directories such as `.serena` or `.github` are tooling, not features. */
const DOT_PATH = /(?:^|\s)\.[a-z][\w.-]*/i;

const CHECKLIST = /^\[[ xX]\]\s*/;

function humanise(raw, options = {}) {
  let text = stripMarkdown(raw);
  text = text.replace(CHECKLIST, "");
  text = text.replace(/https?:\/\/\S+/g, " ");
  text = text.replace(/@[\w-]+/g, " ");
  text = text.replace(/#\d+/g, " ");
  text = text.replace(/\b[0-9a-f]{7,40}\b/gi, " ");
  for (const [pattern, replacement] of JARGON) text = text.replace(pattern, replacement);
  text = text.replace(/\s+/g, " ").trim();
  text = text.replace(/^[\s\-–—:;,.)]+/, "").replace(/[\s\-–—:;,.(]+$/, "").trim();

  if (text.length < 12 || text.length > 160) return "";
  if (META.test(text) || PROCESS.test(text) || DOT_PATH.test(text)) return "";
  if (FILE_PATH.test(text) || TECHNICAL.test(text)) return "";
  if (!/[a-z]/i.test(text)) return "";
  if (!options.allowTitle && !/^[A-Z0-9]/.test(text)) return "";

  return capitalise(text);
}

function stripMarkdown(text) {
  return String(text || "")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]*)`/g, "$1")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/^\s{0,3}#{1,6}\s+/gm, "")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/__([^_]+)__/g, "$1")
    .replace(/\*([^*]+)\*/g, "$1")
    .replace(/\s+/g, " ")
    .trim();
}

function capitalise(text) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function kindFor(text) {
  if (/^(add|new|introduc|bring|support|let|make)\b/i.test(text)) return "new";
  if (/^(fix|correct|resolve|prevent|stop|avoid)\b/i.test(text)) return "fixed";
  return "improved";
}

function renderNotes(entry, pull) {
  const groups = { new: [], improved: [], fixed: [] };
  for (const change of entry.changes) {
    (groups[change.kind] || groups.improved).push(change.text);
  }

  const headings = { new: "New", improved: "Improved", fixed: "Fixed" };
  const lines = [`## ${entry.title}`, ""];

  for (const kind of ["new", "improved", "fixed"]) {
    if (groups[kind].length === 0) continue;
    lines.push(`### ${headings[kind]}`, "");
    for (const text of groups[kind]) lines.push(`- ${text}`);
    lines.push("");
  }

  lines.push("---", "");
  lines.push(
    "Download the package for your platform below. QuickNotch can also update itself: open **Settings → Changelog**."
  );
  if (pull.user?.login) lines.push("", `Contributed in #${pull.number} by @${pull.user.login}.`);

  return lines.join("\n");
}

/* ------------------------------------------------------------------ */
/* Plumbing                                                            */
/* ------------------------------------------------------------------ */

async function api(path) {
  if (!TOKEN) throw new Error("GITHUB_TOKEN is not set");
  const response = await fetch(`https://api.github.com${path}`, {
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${TOKEN}`,
      "User-Agent": "QuickNotch release planner",
      "X-GitHub-Api-Version": "2022-11-28"
    }
  });
  if (!response.ok) {
    throw new Error(`${path} -> HTTP ${response.status} ${await response.text()}`);
  }
  return response.json();
}

async function paginate(path) {
  const results = [];
  let page = 1;
  const separator = path.includes("?") ? "&" : "?";
  while (true) {
    const batch = await api(`${path}${separator}page=${page}`);
    results.push(...batch);
    if (batch.length < 100) break;
    page += 1;
  }
  return results;
}

function skip(reason, message) {
  writeFileSync(
    join(OUT, "plan.json"),
    `${JSON.stringify({ release: false, reason, message }, null, 2)}\n`
  );
  output("release", "false");
  output("reason", reason);
  console.log(`no release: ${message}`);
}

function output(key, value) {
  const file = process.env.GITHUB_OUTPUT;
  if (file) appendFileSync(file, `${key}=${value}\n`);
  console.log(`::notice::${key}=${value}`);
}

// Exported for the local self-test below; CI only executes the file.
export {
  bumpFor,
  cleanTitle,
  compareVersions,
  humanise,
  isAppFile,
  mergeEntry,
  nextVersion
};
