# Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Tree-sitter based code parser for extracting structural information from source files.

Extracts classes (with inheritance), functions/methods, and imports
across Python, Java, Kotlin, Go, JavaScript, and TypeScript using proper AST parsing.
"""

import os
import logging
from typing import Optional

logger = logging.getLogger("tree_sitter_parser")

# Lazy-load tree-sitter to avoid import errors if not installed
_TS_AVAILABLE = False
_LANGUAGES = {}

def _init_tree_sitter():
    """Initialize tree-sitter languages. Called once on first use."""
    global _TS_AVAILABLE, _LANGUAGES
    if _LANGUAGES:
        return True
    try:
        import tree_sitter_python as tsp
        import tree_sitter_java as tsj
        import tree_sitter_javascript as tsjs
        import tree_sitter_typescript as tsts
        import tree_sitter_go as tsgo
        import tree_sitter_kotlin as tskt
        import tree_sitter_swift as tssw
        from tree_sitter import Language

        _LANGUAGES = {
            ".py":   Language(tsp.language()),
            ".pyi":  Language(tsp.language()),
            ".java": Language(tsj.language()),
            ".js":   Language(tsjs.language()),
            ".jsx":  Language(tsjs.language()),
            ".ts":   Language(tsts.language_typescript()),
            ".tsx":  Language(tsts.language_tsx()),
            ".go":   Language(tsgo.language()),
            ".kt":   Language(tskt.language()),
            ".kts":  Language(tskt.language()),
            ".swift": Language(tssw.language()),
        }
        _TS_AVAILABLE = True
        return True
    except Exception as e:
        logger.warning(f"tree-sitter init failed: {e}")
        _TS_AVAILABLE = False
        return False


def _node_text(node, source_bytes: bytes) -> str:
    """Extract the text of a tree-sitter node."""
    return source_bytes[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def _truncate(s: str, max_len: int = 120) -> str:
    return s[:max_len - 3] + "..." if len(s) > max_len else s


# ─── Python ────────────────────────────────────────────────────────────────

def _extract_python(root, src: bytes) -> dict:
    imports, classes, functions = [], [], []

    for node in root.children:
        # Imports
        if node.type in ("import_statement", "import_from_statement"):
            imports.append(_truncate(_node_text(node, src).split("\n")[0]))

        # Classes
        elif node.type == "class_definition":
            name_node = node.child_by_field_name("name")
            name = _node_text(name_node, src) if name_node else "?"
            # Superclasses
            bases_node = node.child_by_field_name("superclasses")
            if bases_node:
                bases = _node_text(bases_node, src)
                classes.append(f"class {name}({bases})")
            else:
                classes.append(f"class {name}")
            # Methods inside class
            for child in node.children:
                if child.type == "block":
                    for stmt in child.children:
                        if stmt.type == "function_definition":
                            fn_name = stmt.child_by_field_name("name")
                            params = stmt.child_by_field_name("parameters")
                            if fn_name:
                                sig = f"  def {_node_text(fn_name, src)}"
                                if params:
                                    sig += _node_text(params, src)
                                functions.append(_truncate(sig))

        # Top-level functions
        elif node.type == "function_definition":
            fn_name = node.child_by_field_name("name")
            params = node.child_by_field_name("parameters")
            if fn_name:
                sig = f"def {_node_text(fn_name, src)}"
                if params:
                    sig += _node_text(params, src)
                functions.append(_truncate(sig))

        # Decorated items
        elif node.type == "decorated_definition":
            for child in node.children:
                if child.type == "class_definition":
                    sub = _extract_python_node_class(child, src)
                    classes.extend(sub["classes"])
                    functions.extend(sub["functions"])
                elif child.type == "function_definition":
                    fn_name = child.child_by_field_name("name")
                    params = child.child_by_field_name("parameters")
                    if fn_name:
                        sig = f"def {_node_text(fn_name, src)}"
                        if params:
                            sig += _node_text(params, src)
                        functions.append(_truncate(sig))

    return {"imports": imports[:30], "classes": classes[:20], "functions": functions[:30]}


def _extract_python_node_class(node, src: bytes) -> dict:
    """Helper to extract from a class_definition node."""
    classes, functions = [], []
    name_node = node.child_by_field_name("name")
    name = _node_text(name_node, src) if name_node else "?"
    bases_node = node.child_by_field_name("superclasses")
    if bases_node:
        classes.append(f"class {name}({_node_text(bases_node, src)})")
    else:
        classes.append(f"class {name}")
    for child in node.children:
        if child.type == "block":
            for stmt in child.children:
                if stmt.type == "function_definition":
                    fn_name = stmt.child_by_field_name("name")
                    params = stmt.child_by_field_name("parameters")
                    if fn_name:
                        sig = f"  def {_node_text(fn_name, src)}"
                        if params:
                            sig += _node_text(params, src)
                        functions.append(_truncate(sig))
    return {"classes": classes, "functions": functions}


# ─── Java ──────────────────────────────────────────────────────────────────

def _extract_java(root, src: bytes) -> dict:
    imports, classes, functions = [], [], []

    def walk(node, depth=0):
        if node.type == "import_declaration":
            imports.append(_truncate(_node_text(node, src).rstrip(";")))

        elif node.type in ("class_declaration", "interface_declaration", "enum_declaration"):
            name_node = node.child_by_field_name("name")
            name = _node_text(name_node, src) if name_node else "?"
            kind = node.type.replace("_declaration", "")

            # Superclass / interfaces
            extras = []
            for child in node.children:
                if child.type == "superclass":
                    extras.append("extends " + _node_text(child, src).replace("extends ", ""))
                elif child.type == "super_interfaces":
                    extras.append("implements " + _node_text(child, src).replace("implements ", ""))
            sig = f"{kind} {name}"
            if extras:
                sig += " " + " ".join(extras)
            classes.append(_truncate(sig))

            # Methods
            for child in node.children:
                if child.type == "class_body":
                    for member in child.children:
                        if member.type == "method_declaration":
                            mname = member.child_by_field_name("name")
                            mtype = member.child_by_field_name("type")
                            params = member.child_by_field_name("parameters")
                            if mname:
                                sig_parts = []
                                if mtype:
                                    sig_parts.append(_node_text(mtype, src))
                                sig_parts.append(_node_text(mname, src))
                                if params:
                                    sig_parts[-1] += _node_text(params, src)
                                functions.append(_truncate("  " + " ".join(sig_parts)))
                        elif member.type == "constructor_declaration":
                            mname = member.child_by_field_name("name")
                            params = member.child_by_field_name("parameters")
                            if mname:
                                sig = _node_text(mname, src)
                                if params:
                                    sig += _node_text(params, src)
                                functions.append(_truncate("  " + sig))

        # Recurse into program > class body
        if depth < 3:
            for child in node.children:
                walk(child, depth + 1)

    walk(root)
    return {"imports": imports[:30], "classes": classes[:20], "functions": functions[:30]}


# ─── Kotlin ────────────────────────────────────────────────────────────────

def _extract_kotlin(root, src: bytes) -> dict:
    imports, classes, functions = [], [], []

    def walk(node, depth=0):
        if node.type == "import":
            text = _node_text(node, src).strip()
            if " " in text:  # Skip bare 'import' keyword
                imports.append(_truncate(text))

        elif node.type in ("class_declaration", "object_declaration"):
            # Find class name via 'identifier' child
            name = "?"
            for child in node.children:
                if child.type == "identifier":
                    name = _node_text(child, src)
                    break
                elif child.type == "type_identifier":
                    name = _node_text(child, src)
                    break
            # Delegation specifiers (extends/implements)
            for child in node.children:
                if child.type == "delegation_specifiers":
                    supers = _node_text(child, src)
                    classes.append(_truncate(f"class {name} : {supers}"))
                    break
            else:
                classes.append(f"class {name}")

            # Methods inside class body
            for child in node.children:
                if child.type == "class_body":
                    for member in child.children:
                        if member.type == "function_declaration":
                            fn_sig = _node_text(member, src).split("{")[0].strip()
                            functions.append(_truncate("  " + fn_sig))

        elif node.type == "function_declaration" and depth < 2:
            fn_sig = _node_text(node, src).split("{")[0].strip()
            functions.append(_truncate(fn_sig))

        if depth < 3:
            for child in node.children:
                walk(child, depth + 1)

    walk(root)
    return {"imports": imports[:30], "classes": classes[:20], "functions": functions[:30]}


# ─── Go ────────────────────────────────────────────────────────────────────

def _extract_go(root, src: bytes) -> dict:
    imports, classes, functions = [], [], []

    for node in root.children:
        if node.type == "import_declaration":
            imports.append(_truncate(_node_text(node, src).split("\n")[0]))

        elif node.type == "type_declaration":
            for child in node.children:
                if child.type == "type_spec":
                    name_node = child.child_by_field_name("name")
                    type_node = child.child_by_field_name("type")
                    if name_node and type_node:
                        name = _node_text(name_node, src)
                        kind = type_node.type  # struct_type, interface_type, etc.
                        kind_label = kind.replace("_type", "")
                        classes.append(f"type {name} {kind_label}")
                        # Extract struct fields or interface methods
                        if type_node.type == "interface_type":
                            for spec in type_node.children:
                                if spec.type == "method_spec":
                                    functions.append(_truncate("  " + _node_text(spec, src)))

        elif node.type == "function_declaration":
            name_node = node.child_by_field_name("name")
            params = node.child_by_field_name("parameters")
            result = node.child_by_field_name("result")
            if name_node:
                sig = f"func {_node_text(name_node, src)}"
                if params:
                    sig += _node_text(params, src)
                if result:
                    sig += " " + _node_text(result, src)
                functions.append(_truncate(sig))

        elif node.type == "method_declaration":
            name_node = node.child_by_field_name("name")
            receiver = node.child_by_field_name("receiver")
            params = node.child_by_field_name("parameters")
            if name_node:
                sig = "func "
                if receiver:
                    sig += _node_text(receiver, src) + " "
                sig += _node_text(name_node, src)
                if params:
                    sig += _node_text(params, src)
                functions.append(_truncate(sig))

    return {"imports": imports[:30], "classes": classes[:20], "functions": functions[:30]}


# ─── JavaScript / TypeScript ──────────────────────────────────────────────

def _extract_js_ts(root, src: bytes) -> dict:
    imports, classes, functions = [], [], []

    def walk(node, depth=0):
        if node.type in ("import_statement",):
            imports.append(_truncate(_node_text(node, src).split("\n")[0]))

        elif node.type == "class_declaration":
            name_node = node.child_by_field_name("name")
            name = _node_text(name_node, src) if name_node else "?"
            # Heritage (extends/implements)
            heritage = []
            for child in node.children:
                if child.type == "class_heritage":
                    heritage.append(_node_text(child, src))
            sig = f"class {name}"
            if heritage:
                sig += " " + " ".join(heritage)
            classes.append(_truncate(sig))

            # Methods
            for child in node.children:
                if child.type == "class_body":
                    for member in child.children:
                        if member.type in ("method_definition", "public_field_definition"):
                            mname = member.child_by_field_name("name")
                            if mname:
                                first_line = _node_text(member, src).split("{")[0].strip()
                                functions.append(_truncate("  " + first_line))

        elif node.type in ("function_declaration", "function"):
            name_node = node.child_by_field_name("name")
            params = node.child_by_field_name("parameters")
            if name_node:
                sig = f"function {_node_text(name_node, src)}"
                if params:
                    sig += _node_text(params, src)
                functions.append(_truncate(sig))

        elif node.type == "export_statement":
            for child in node.children:
                walk(child, depth)
            return

        elif node.type == "lexical_declaration" and depth < 2:
            # const foo = () => {} or const foo = function() {}
            for child in node.children:
                if child.type == "variable_declarator":
                    vname = child.child_by_field_name("name")
                    vvalue = child.child_by_field_name("value")
                    if vname and vvalue and vvalue.type in ("arrow_function", "function"):
                        sig = f"const {_node_text(vname, src)} = "
                        params = vvalue.child_by_field_name("parameters")
                        if params:
                            sig += _node_text(params, src) + " => ..."
                        else:
                            sig += "() => ..."
                        functions.append(_truncate(sig))

        # Recurse top-level only
        if depth < 2:
            for child in node.children:
                walk(child, depth + 1)

    walk(root)
    return {"imports": imports[:30], "classes": classes[:20], "functions": functions[:30]}


# ─── Swift ─────────────────────────────────────────────────────────────────

def _extract_swift(root, src: bytes) -> dict:
    imports, classes, functions = [], [], []

    for node in root.children:
        # Imports
        if node.type == "import_declaration":
            imports.append(_truncate(_node_text(node, src).strip()))

        # Classes, structs, enums, protocols
        elif node.type in ("class_declaration", "protocol_declaration"):
            name = "?"
            kind = "class" if node.type == "class_declaration" else "protocol"
            # For structs, tree-sitter-swift uses class_declaration with 'struct' keyword
            first_child_text = _node_text(node.children[0], src) if node.children else ""
            if first_child_text == "struct":
                kind = "struct"
            elif first_child_text == "enum":
                kind = "enum"

            # Find name (type_identifier child)
            for child in node.children:
                if child.type == "type_identifier":
                    name = _node_text(child, src)
                    break

            # Inheritance (inheritance_specifier children)
            supers = []
            for child in node.children:
                if child.type == "inheritance_specifier":
                    supers.append(_node_text(child, src).strip())

            sig = f"{kind} {name}"
            if supers:
                sig += ": " + ", ".join(supers)
            classes.append(_truncate(sig))

            # Methods inside class/protocol body
            for child in node.children:
                if child.type in ("class_body", "protocol_body"):
                    for member in child.children:
                        if member.type == "function_declaration":
                            fn_sig = _node_text(member, src).split("{")[0].strip()
                            functions.append(_truncate("  " + fn_sig))

        # Top-level functions
        elif node.type == "function_declaration":
            fn_sig = _node_text(node, src).split("{")[0].strip()
            functions.append(_truncate(fn_sig))

    return {"imports": imports[:30], "classes": classes[:20], "functions": functions[:30]}


# ─── Public API ────────────────────────────────────────────────────────────

# Map file extension → extractor function
_EXTRACTORS = {
    ".py":   _extract_python,
    ".pyi":  _extract_python,
    ".java": _extract_java,
    ".kt":   _extract_kotlin,
    ".kts":  _extract_kotlin,
    ".go":   _extract_go,
    ".js":   _extract_js_ts,
    ".jsx":  _extract_js_ts,
    ".ts":   _extract_js_ts,
    ".tsx":  _extract_js_ts,
    ".swift": _extract_swift,
}


def parse_file(path: str, content: str) -> Optional[dict]:
    """Parse a source file and extract structural info using tree-sitter.

    Returns dict with keys: imports, classes, functions
    Returns None if the file extension is unsupported or parsing fails.
    """
    if not _init_tree_sitter():
        return None

    ext = os.path.splitext(path)[1].lower()
    lang = _LANGUAGES.get(ext)
    extractor = _EXTRACTORS.get(ext)
    if not lang or not extractor:
        return None

    try:
        from tree_sitter import Parser
        parser = Parser(lang)
        src_bytes = content.encode("utf-8", errors="replace")
        tree = parser.parse(src_bytes)
        return extractor(tree.root_node, src_bytes)
    except Exception as e:
        logger.debug(f"tree-sitter parse failed for {path}: {e}")
        return None


def is_available() -> bool:
    """Check if tree-sitter is available."""
    return _init_tree_sitter()
