#!/usr/bin/env python3
import ast
import io
import re
import tokenize
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

CODE = 0
STRING = 1
COMMENT = 2

KOTLIN_SUFFIXES = (".kt", ".kts", ".java")
GROOVY_SUFFIXES = (".gradle",)
PYTHON_SUFFIXES = (".py",)
XML_SUFFIXES = (".xml",)
HASH_SUFFIXES = (".yml", ".yaml", ".toml", ".properties", ".pro")
MULTI_DOLLAR_PREFIX = 2


@dataclass(frozen=True)
class Span:
    start: int
    end: int


class _CLikeScanner:
    def __init__(self, text: str, *, single_quoted_strings: bool) -> None:
        self.text = text
        self.kinds = bytearray(len(text))
        self.single_quoted_strings = single_quoted_strings
        self.frames: list[tuple[str, int]] = []
        self.mode = "code"
        self.depth = 0
        self.index = 0

    def mark(self, start: int, end: int, kind: int) -> None:
        self.kinds[start:end] = bytes([kind]) * (end - start)

    def scan(self) -> bytearray:
        while self.index < len(self.text):
            if self.mode == "code":
                self.step_code()
            else:
                self.step_string()
        return self.kinds

    def step_code(self) -> None:
        text, i = self.text, self.index
        if text.startswith("//", i):
            end = text.find("\n", i)
            end = len(text) if end < 0 else end
            self.mark(i, end, COMMENT)
            self.index = end
        elif text.startswith("/*", i):
            end = self.block_comment_end(i)
            self.mark(i, end, COMMENT)
            self.index = end
        elif text[i] == "$" and self.dollar_string_start(i) is not None:
            self.open_string(i, self.dollar_string_start(i))
        elif text[i] == '"':
            self.open_string(i, i)
        elif text[i] == "'":
            self.index = self.quoted_literal_end(i)
            self.mark(i, self.index, STRING)
        elif text[i] == "`":
            end = text.find("`", i + 1)
            self.index = len(text) if end < 0 else end + 1
        elif text[i] == "{":
            self.depth += 1
            self.index += 1
        elif text[i] == "}" and self.depth == 0 and self.frames:
            self.mode, self.depth = self.frames.pop()
            self.mark(i, i + 1, STRING)
            self.index += 1
        else:
            if text[i] == "}":
                self.depth -= 1
            self.index += 1

    def dollar_string_start(self, i: int) -> int | None:
        end = i
        while end < len(self.text) and self.text[end] == "$":
            end += 1
        if end < len(self.text) and self.text[end] == '"' and end - i >= MULTI_DOLLAR_PREFIX:
            return end
        return None

    def open_string(self, start: int, quote: int) -> None:
        raw = self.text.startswith('"""', quote)
        self.mode = "raw" if raw else "string"
        self.index = quote + (3 if raw else 1)
        self.mark(start, self.index, STRING)

    def block_comment_end(self, start: int) -> int:
        nesting, i = 0, start
        while i < len(self.text):
            if self.text.startswith("/*", i):
                nesting += 1
                i += 2
            elif self.text.startswith("*/", i):
                nesting -= 1
                i += 2
                if nesting == 0:
                    return i
            else:
                i += 1
        return len(self.text)

    def quoted_literal_end(self, start: int) -> int:
        text = self.text
        if self.single_quoted_strings and text.startswith("'''", start):
            end = text.find("'''", start + 3)
            return len(text) if end < 0 else end + 3
        i = start + 1
        while i < len(text) and text[i] != "'":
            if text[i] == "\n" and not self.single_quoted_strings:
                return i
            i += 2 if text[i] == "\\" else 1
        return min(i + 1, len(text))

    def step_string(self) -> None:
        text, i = self.text, self.index
        if self.mode == "string" and text[i] == "\\":
            self.mark(i, i + 2, STRING)
            self.index += 2
        elif self.mode == "string" and text[i] == '"':
            self.mark(i, i + 1, STRING)
            self.mode = "code"
            self.index += 1
        elif self.mode == "raw" and text.startswith('"""', i):
            end = i
            while end < len(text) and text[end] == '"':
                end += 1
            self.mark(i, end, STRING)
            self.mode = "code"
            self.index = end
        elif text.startswith("${", i):
            self.mark(i, i + 2, STRING)
            self.frames.append((self.mode, self.depth))
            self.mode, self.depth = "code", 0
            self.index += 2
        else:
            self.mark(i, i + 1, STRING)
            self.index += 1


def _hash_kinds(text: str) -> bytearray:
    kinds = bytearray(len(text))
    offset = 0
    for line in text.splitlines(keepends=True):
        quote = None
        for position, char in enumerate(line):
            if quote:
                kinds[offset + position] = STRING
                if char == quote:
                    quote = None
            elif char in "\"'":
                quote = char
                kinds[offset + position] = STRING
            elif char == "#" and (position == 0 or line[position - 1] in " \t"):
                end = len(line.rstrip("\r\n"))
                kinds[offset + position : offset + end] = bytes([COMMENT]) * (end - position)
                break
        offset += len(line)
    return kinds


def _xml_kinds(text: str) -> bytearray:
    kinds = bytearray(len(text))
    for match in re.finditer(r"<!--.*?-->", text, flags=re.DOTALL):
        kinds[match.start() : match.end()] = bytes([COMMENT]) * (match.end() - match.start())
    return kinds


def _line_offsets(text: str) -> list[int]:
    offsets = [0]
    for line in text.splitlines(keepends=True):
        offsets.append(offsets[-1] + len(line))
    return offsets


def _python_kinds(text: str) -> bytearray:
    kinds = bytearray(len(text))
    offsets = _line_offsets(text)

    def absolute(row: int, column: int) -> int:
        return offsets[row - 1] + column

    for token in tokenize.generate_tokens(io.StringIO(text).readline):
        start = absolute(*token.start)
        end = absolute(*token.end)
        if token.type == tokenize.COMMENT and not (token.start == (1, 0) and token.string.startswith("#!")):
            kinds[start:end] = bytes([COMMENT]) * (end - start)
        elif token.type == tokenize.STRING:
            kinds[start:end] = bytes([STRING]) * (end - start)
    for node in ast.walk(ast.parse(text)):
        if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)) and node.body:
            first = node.body[0]
            if isinstance(first, ast.Expr) and isinstance(first.value, ast.Constant) and isinstance(first.value.value, str):
                start = absolute(first.lineno, first.col_offset)
                end = absolute(first.end_lineno or first.lineno, first.end_col_offset or 0)
                kinds[start:end] = bytes([COMMENT]) * (end - start)
    return kinds


def classify(path: Path, text: str) -> bytearray:
    suffix = path.suffix
    if suffix in KOTLIN_SUFFIXES:
        return _CLikeScanner(text, single_quoted_strings=False).scan()
    if suffix in GROOVY_SUFFIXES:
        return _CLikeScanner(text, single_quoted_strings=True).scan()
    if suffix in PYTHON_SUFFIXES:
        return _python_kinds(text)
    if suffix in XML_SUFFIXES:
        return _xml_kinds(text)
    if suffix in HASH_SUFFIXES:
        return _hash_kinds(text)
    return bytearray(len(text))


def comment_spans(kinds: bytearray) -> list[Span]:
    spans: list[Span] = []
    start = None
    for index, kind in enumerate(kinds):
        if kind == COMMENT and start is None:
            start = index
        elif kind != COMMENT and start is not None:
            spans.append(Span(start, index))
            start = None
    if start is not None:
        spans.append(Span(start, len(kinds)))
    return spans


def code_only(text: str, kinds: bytearray) -> str:
    return "".join(
        char if kind == CODE or char == "\n" else ("" if kind == COMMENT else " ") for char, kind in zip(text, kinds, strict=True)
    )


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def _glue_needed(left: str, right: str) -> bool:
    return bool(left) and bool(right) and not left.isspace() and not right.isspace()


def strip_comments(text: str, kinds: bytearray, keep: Callable[[str], bool] | None = None) -> str:
    pieces: list[str] = []
    cursor = 0
    for span in comment_spans(kinds):
        if keep is not None and keep(text[span.start : span.end]):
            continue
        pieces.append(text[cursor : span.start])
        left = text[span.start - 1] if span.start else ""
        right = text[span.end] if span.end < len(text) else ""
        pieces.append(" " if _glue_needed(left, right) else "\0")
        cursor = span.end
    pieces.append(text[cursor:])
    stripped = "".join(pieces)
    output: list[str] = []
    for original_line in stripped.splitlines(keepends=True):
        had_comment = "\0" in original_line
        line = original_line.replace("\0", "")
        body = line.rstrip("\r\n")
        newline = line[len(body) :]
        if had_comment and not body.strip():
            continue
        output.append((body.rstrip() if had_comment else body) + newline)
    return "".join(output)
