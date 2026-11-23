import pathlib
import re

root = pathlib.Path("app/src/main/java")
radius = {"4.dp": "Small", "8.dp": "Medium", "12.dp": "Large", "16.dp": "XLarge"}
motion = {"120": "Fast", "200": "Standard", "300": "Slow"}

changed = []
for path in root.rglob("*.kt"):
    if path.name == "Tokens.kt":
        continue
    text = path.read_text(encoding="utf-8")
    original = text
    for value, name in radius.items():
        text = text.replace(f"RoundedCornerShape({value})", f"VaultShape.{name}")
    for value, name in motion.items():
        text = re.sub(rf"tween\({value}\)", f"tween(VaultMotion.{name})", text)
    if text == original:
        continue
    for symbol in ("VaultShape", "VaultMotion"):
        if f"{symbol}." in text and f"import com.example.album.ui.theme.{symbol}" not in text:
            lines = text.splitlines(keepends=True)
            package_index = next(i for i, line in enumerate(lines) if line.startswith("package "))
            lines.insert(package_index + 1, f"\nimport com.example.album.ui.theme.{symbol}\n")
            text = "".join(lines)
    path.write_text(text, encoding="utf-8")
    changed.append(str(path))

print("\n".join(changed))
print(f"changed={len(changed)}")
