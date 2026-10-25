#!/usr/bin/env python3
"""Check modular TV UI translations before Android compilation.

Only Android's resource resolver chooses the active locale. This script checks
both catalogues and their Kotlin references; it does not change runtime locale.
"""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
JAVA = ROOT / "app/src/main/java"
SLOTS = re.compile(r"%\d+\$(?:[-+# 0,(]*\d*(?:\.\d+)?)[sdf]")
REFERENCE = re.compile(r"R\.string\.(i18n_[A-Za-z0-9_]+)")

def catalogue(folder: str) -> dict[str, str]:
    root = RES / folder
    files = sorted(root.glob("*.xml"))
    result: dict[str, str] = {}
    for file in files:
        assert file.is_file(), f"Missing resource: {file}"
        for value in ET.parse(file).getroot().findall("string"):
            key = value.attrib["name"]
            assert key not in result, f"Duplicate string: {key}"
            result[key] = "".join(value.itertext())
    return result

def main() -> None:
    english = catalogue("values")
    chinese = catalogue("values-zh")
    taiwan = catalogue("values-zh-rTW")
    for locale, translated in (("Simplified Chinese", chinese), ("Traditional Chinese", taiwan)):
        assert english.keys() == translated.keys(), (
            f"{locale}: only English: {sorted(english.keys() - translated.keys())}; "
            f"only translated: {sorted(translated.keys() - english.keys())}"
        )
        for key in english:
            assert Counter(SLOTS.findall(english[key])) == Counter(SLOTS.findall(translated[key])), (
                f"Format arguments differ in {locale}: {key}"
            )
    mainland_terms = ("啓", "軟件", "視頻", "投屏", "屏保", "信號源", "二維碼",
                      "進程", "配置", "兼容", "鏈接", "搜索", "安裝包", "前台")
    for key, value in taiwan.items():
        assert not any(term in value for term in mainland_terms), f"Non-Taiwan wording in {key}"
    kotlin = "\n".join(p.read_text(encoding="utf-8") for p in JAVA.rglob("*.kt"))
    missing = set(REFERENCE.findall(kotlin)) - english.keys()
    assert not missing, f"Unresolved translation references: {sorted(missing)}"
    assert len(english) > 250, f"Unexpectedly incomplete UI catalogue: {len(english)} strings"
    print(f"i18n validated: {len(english)} resources in English, Simplified Chinese, and Traditional Chinese.")

if __name__ == "__main__":
    main()
