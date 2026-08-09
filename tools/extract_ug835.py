#!/usr/bin/env python3
"""Turn the Vivado Tcl Command Reference (UG835) into the plugin's command catalogue.

The plugin exposes only ~30 MCP tools; the full ~770-command reference is shipped as
data and reached through searchVivadoCommands / describeVivadoCommand / runVivadoCommand.
This script produces that data.

Usage:
    python3 tools/extract_ug835.py [--pdf docs/UG835.pdf] [--out src/main/resources/vivado]

Requires `pdftotext` (poppler-utils).  The PDF itself is NOT committed (36 MB); download
it from AMD/Xilinx ("Vivado Design Suite Tcl Command Reference Guide (UG835)") and drop it
at docs/UG835.pdf before running.  Re-run this after upgrading to a newer UG835 revision;
the two generated files are purely mechanical, so regenerating never clobbers the
hand-curated tier lists (ug835-tier-core.txt / ug835-tier-extended.txt) that live beside
them.

Outputs:
    ug835-index.tsv    name \t categories \t summary \t syntax          (one line per command)
    ug835-details.tsv  name \t detailText                               (\\n / \\t escaped)
"""

import argparse
import os
import re
import subprocess
import sys

# A command entry opens with the bare command name at column 0 ...
NAME_RE = re.compile(r"[a-z][a-z0-9_]*\Z")
# ... followed, within a few lines, by the "Syntax" heading. Names alone are ambiguous
# (they also appear in prose and See Also lists), so the Syntax heading is the real anchor.
SYNTAX_LOOKAHEAD = 14

# Page furniture repeated on all 2576 pages.
PAGE_FOOTER_RE = re.compile(r"^\s*Page\d+ of \d+\s*$")
RUNNING_HEAD_RE = re.compile(r"^\s*Vivado Design Suite Tcl Command Reference Guide \(UG835\)\s*$")
# Usage tables that spill across a page repeat their column header.
TABLE_HEAD_RE = re.compile(r"^\s*Name\s{2,}Description\s*$")

SECTIONS = ("Syntax", "Returns", "Usage", "Categories", "Description", "Arguments",
            "Examples", "See Also")


def pdf_to_text(pdf_path):
    """Run pdftotext -layout and return the whole document as one string."""
    if not os.path.isfile(pdf_path):
        sys.exit("error: %s not found. See the module docstring for where to get it." % pdf_path)
    try:
        out = subprocess.run(["pdftotext", "-layout", pdf_path, "-"],
                             check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    except FileNotFoundError:
        sys.exit("error: pdftotext not found. Install poppler-utils.")
    return out.stdout.decode("utf-8", errors="replace")


def clean_lines(text):
    """Drop page furniture and repeated table headers, and normalize UG835's odd glyphs."""
    # UG835 renders option leaders as U+2011 NON-BREAKING HYPHEN; Tcl needs a plain '-'.
    text = text.replace("\u2011", "-").replace("\u2013", "-")
    kept = []
    for line in text.split("\n"):
        if PAGE_FOOTER_RE.match(line) or RUNNING_HEAD_RE.match(line) or TABLE_HEAD_RE.match(line):
            continue
        kept.append(line.rstrip())
    return kept


def find_body(lines):
    """Return the slice starting at the alphabetical command chapter.

    The heading string also appears in the table of contents near the top of the
    document, so skip past the front matter before matching.
    """
    for i, line in enumerate(lines):
        if i > 2000 and line.strip() == "Tcl Commands Listed Alphabetically":
            return lines[i:]
    sys.exit("error: could not locate the 'Tcl Commands Listed Alphabetically' chapter")


def entry_starts(body):
    """Locate every command entry: (line index, command name), document order."""
    found = []
    for i, line in enumerate(body):
        if not NAME_RE.match(line):
            continue
        for j in range(i + 1, min(i + SYNTAX_LOOKAHEAD, len(body))):
            probe = body[j].strip()
            if probe == "Syntax":
                found.append((i, line))
                break
            # Another bare name before the heading means this line was prose, not an entry.
            if NAME_RE.match(body[j]):
                break
    return found


def section(segment_text, heading):
    """Text of one `heading` block, up to the next known section heading."""
    others = "|".join(h for h in SECTIONS if h != heading)
    m = re.search(r"\n%s\n(.*?)(?=\n(?:%s)\n|\Z)" % (heading, others), segment_text, re.S)
    return m.group(1).strip() if m else ""


def parse_entry(name, segment):
    """Pull the index fields plus the detail body out of one command's lines."""
    text = "\n".join(segment)

    # Summary: everything between the name line and the Syntax heading.
    summary_parts = []
    for line in segment[1:]:
        if line.strip() == "Syntax":
            break
        if line.strip():
            summary_parts.append(line.strip())
    summary = " ".join(summary_parts)

    syntax = " ".join(section(text, "Syntax").split())

    categories = " ".join(section(text, "Categories").split())
    # A command with no categories leaves the heading empty; guard against swallowing prose.
    if categories.startswith("Description") or len(categories) > 200:
        categories = ""
    categories = ", ".join(c.strip() for c in categories.split(",") if c.strip())

    # Detail: the whole entry minus the name line, collapsed to at most one blank line
    # in a row so describeVivadoCommand output stays compact.
    detail = re.sub(r"\n{3,}", "\n\n", "\n".join(segment[1:]).strip())

    return {"name": name, "categories": categories, "summary": summary,
            "syntax": syntax, "detail": detail}


def escape(field):
    return field.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pdf", default="docs/UG835.pdf")
    ap.add_argument("--out", default="src/main/resources/vivado")
    args = ap.parse_args()

    lines = clean_lines(pdf_to_text(args.pdf))
    body = find_body(lines)
    starts = entry_starts(body)
    if len(starts) < 500:
        sys.exit("error: only %d entries found; the PDF layout likely changed" % len(starts))

    commands = []
    seen = set()
    for k, (start, name) in enumerate(starts):
        end = starts[k + 1][0] if k + 1 < len(starts) else len(body)
        if name in seen:                      # first occurrence wins
            continue
        seen.add(name)
        commands.append(parse_entry(name, body[start:end]))

    # UG835 occasionally files an entry under one heading while documenting another command
    # (report_noc is written up under the "report_noc_qos" heading). Both names are real
    # Vivado commands, so alias the syntax head in rather than losing it.
    aliases = []
    for c in commands:
        head = c["syntax"].split(" ")[0] if c["syntax"] else ""
        if head and head != c["name"] and head not in seen:
            seen.add(head)
            alias = dict(c)
            alias["name"] = head
            aliases.append(alias)
            print("note: '%s' documented under the '%s' heading; indexing both" % (head, c["name"]))
    commands.extend(aliases)

    commands.sort(key=lambda c: c["name"])

    os.makedirs(args.out, exist_ok=True)
    index_path = os.path.join(args.out, "ug835-index.tsv")
    details_path = os.path.join(args.out, "ug835-details.tsv")

    with open(index_path, "w", encoding="utf-8") as fh:
        fh.write("# generated by tools/extract_ug835.py -- do not edit\n")
        fh.write("# name\tcategories\tsummary\tsyntax\n")
        for c in commands:
            fh.write("%s\t%s\t%s\t%s\n" %
                     (c["name"], escape(c["categories"]), escape(c["summary"]), escape(c["syntax"])))

    with open(details_path, "w", encoding="utf-8") as fh:
        fh.write("# generated by tools/extract_ug835.py -- do not edit\n")
        for c in commands:
            fh.write("%s\t%s\n" % (c["name"], escape(c["detail"])))

    no_syntax = [c["name"] for c in commands if not c["syntax"]]
    print("commands: %d" % len(commands))
    print("%s  %.1f KB" % (index_path, os.path.getsize(index_path) / 1024.0))
    print("%s  %.1f MB" % (details_path, os.path.getsize(details_path) / 1048576.0))
    if no_syntax:
        print("warning: %d entries have no syntax line: %s" % (len(no_syntax), no_syntax[:10]))


if __name__ == "__main__":
    main()
