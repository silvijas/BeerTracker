"""Converts the verified SVG prototypes into ImageVector Kotlin source.

Mechanical so the path numbers in the plan cannot drift from what was
rendered and eyeballed in the browser.
"""
import math
import os
import re

def circle(cx, cy, r):
    return "M %g %g A %g %g 0 1 1 %g %g A %g %g 0 1 1 %g %g Z" % (
        cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy)

GLASS = [
    ("fill", "M7.6 3L16.4 3C16.4 8.2 14.6 11.4 12 11.8C9.4 11.4 7.6 8.2 7.6 3Z", None),
    ("fill", "M11.15 11.4L12.85 11.4L12.85 19.4L11.15 19.4Z", None),
    ("fill", "M8.2 19.2L15.8 19.2C16.3 19.2 16.7 19.6 16.7 20.1C16.7 20.6 16.3 21 15.8 21L8.2 21C7.7 21 7.3 20.6 7.3 20.1C7.3 19.6 7.7 19.2 8.2 19.2Z", None),
]

def transform(d, tx, ty, scale, deg, px, py):
    """Applies SVG `translate(tx ty) scale(s) rotate(deg px py)` to a path."""
    rad = math.radians(deg)
    cos, sin = math.cos(rad), math.sin(rad)

    def point(x, y):
        x, y = x - px, y - py
        x, y = x * cos - y * sin, x * sin + y * cos
        x, y = x * scale, y * scale
        return (x + px * scale + tx, y + py * scale + ty)

    out = []
    for cmd, nums in parse(d):
        if cmd == "Z":
            out.append(("Z", []))
            continue
        if cmd == "A":
            rx, ry, rot, large, sweep, x, y = nums
            nx, ny = point(x, y)
            out.append(("A", [rx * scale, ry * scale, rot + deg, large, sweep, nx, ny]))
            continue
        moved = []
        for i in range(0, len(nums), 2):
            moved.extend(point(nums[i], nums[i + 1]))
        out.append((cmd, moved))
    return out

TOKEN = re.compile(r"([MLCAZ])|(-?\d*\.?\d+)")

def parse(d):
    """Returns [(command, [numbers])] for the M/L/C/A/Z subset used here."""
    cmd, nums, out = None, [], []
    arity = {"M": 2, "L": 2, "C": 6, "A": 7, "Z": 0}
    for letter, number in TOKEN.findall(d):
        if letter:
            if cmd:
                out.extend(flush(cmd, nums, arity))
            cmd, nums = letter, []
            if letter == "Z":
                out.append(("Z", []))
                cmd = None
        else:
            nums.append(float(number))
    if cmd:
        out.extend(flush(cmd, nums, arity))
    return out

def flush(cmd, nums, arity):
    """SVG repeats the last command for extra coordinate groups; M repeats as L."""
    size = arity[cmd]
    if size == 0:
        return [(cmd, [])]
    chunks = [nums[i:i + size] for i in range(0, len(nums), size)]
    return [(cmd if i == 0 else ("L" if cmd == "M" else cmd), c) for i, c in enumerate(chunks)]

def f(value):
    text = ("%.4f" % value).rstrip("0").rstrip(".")
    return (text if text not in ("", "-0") else "0") + "f"

def kotlin_path(commands, indent="        "):
    lines = []
    for cmd, nums in commands:
        if cmd == "M":
            lines.append("moveTo(%s, %s)" % (f(nums[0]), f(nums[1])))
        elif cmd == "L":
            lines.append("lineTo(%s, %s)" % (f(nums[0]), f(nums[1])))
        elif cmd == "C":
            lines.append("curveTo(%s)" % ", ".join(f(n) for n in nums))
        elif cmd == "A":
            rx, ry, rot, large, sweep, x, y = nums
            lines.append("arcTo(%s, %s, %s, %s, %s, %s, %s)" % (
                f(rx), f(ry), f(rot), str(bool(large)).lower(),
                str(bool(sweep)).lower(), f(x), f(y)))
        elif cmd == "Z":
            lines.append("close()")
    return "\n".join(indent + line for line in lines)

ICONS = [
    ("Pork", [
        ("fill", "M4.6 13C4.6 9.5 8 7.1 12 7.1C16.5 7.1 20 9.6 20 13C20 14.6 19.3 16 18.1 17L18.1 19.6L16.1 19.6L16.1 17.9C15.3 18.1 14.4 18.2 13.5 18.2L10.6 18.2C9.8 18.2 9.1 18.1 8.4 18L8.4 19.6L6.4 19.6L6.4 17.2C5.3 16.2 4.6 14.7 4.6 13Z"
                 "M8.6 6.4L11.4 5.3L11 8.5Z" + circle(4.2, 12.6, 2), None),
        ("stroke", "M20 10.8C21.6 10.4 22.2 9 21.2 8.2C20.5 7.6 19.6 8 19.8 8.8", 1.3),
    ]),
    ("Poultry", [
        ("fill", "M4 13.6C4 11 7 9.2 10.6 9.2L15.6 9.2C17.8 9.2 19.4 10.6 19.4 12.6C19.4 15 17.4 16.9 14.6 16.9L8.6 16.9C5.9 16.9 4 15.5 4 13.6Z"
                 "M4.6 11.4L1.4 9.6L4.4 14.6Z"
                 "M14.2 7.6C13.6 9 13.4 10 13.4 11.2L16.8 11.2C16.8 9.8 17 8.6 17.6 7.6Z"
                 "M17.6 5L21.4 5.9L17.6 6.9Z" + circle(15.2, 5.8, 2.6), None),
        ("stroke", "M9.4 17L9.4 20.4", 1.3),
        ("stroke", "M13 17L13 20.4", 1.3),
        ("stroke", "M8 20.6L11 20.6", 1.3),
        ("stroke", "M11.6 20.6L14.6 20.6", 1.3),
    ]),
    ("Lamb", [
        ("fill", "M6.6 12.6A2.5 2.5 0 0 1 8.9 8.4A2.5 2.5 0 0 1 13 7.2A2.5 2.5 0 0 1 17.2 8.6A2.4 2.4 0 0 1 18.4 13.2A2.4 2.4 0 0 1 16.6 15.6L8.2 15.6A2.4 2.4 0 0 1 6.6 12.6Z"
                 "M8.6 15.4L10.4 15.4L10.4 19.6L8.6 19.6Z"
                 "M14.4 15.4L16.2 15.4L16.2 19.6L14.4 19.6Z"
                 "M18 11.6C18 9.8 19.2 8.6 20.6 8.6C22 8.6 23 9.8 23 11.4C23 13.2 21.8 14.4 20.4 14.4C19 14.4 18 13.2 18 11.6Z"
                 "M17.4 8.6A1.5 1.9 -25 1 1 19.6 7.4A1.5 1.9 -25 1 1 17.4 8.6Z", None),
    ]),
    ("Beef", [
        ("fill", "M6 9.4L16.4 9.4C18.2 9.4 19.6 10.8 19.6 12.6L19.6 14.4C19.6 16.2 18.2 17.6 16.4 17.6L16.2 17.6L16.2 20.6L14.4 20.6L14.4 17.6L8.6 17.6L8.6 20.6L6.8 20.6L6.8 17.6L6 17.6C4.2 17.6 2.8 16.2 2.8 14.4L2.8 12.6C2.8 10.8 4.2 9.4 6 9.4Z"
                 "M19.4 10.8C21.2 10.8 22.6 12.2 22.6 14C22.6 15.8 21.2 17.2 19.4 17.2Z", None),
        ("stroke", "M19.6 11C19.6 9.4 20.4 8.2 21.6 7.8", 1.3),
        ("stroke", "M22.4 11.4C23 10 22.8 8.6 22 7.6", 1.3),
        ("stroke", "M2.8 11C1.6 10.4 1.2 9 1.6 7.6", 1.3),
    ]),
    ("Game", [
        ("fill", "M12 11.4C10 11.4 8.6 12.9 8.6 14.9C8.6 17.6 10.2 20.4 12 21.4C13.8 20.4 15.4 17.6 15.4 14.9C15.4 12.9 14 11.4 12 11.4Z"
                 "M8.8 11.6A1.7 2.2 30 1 1 6.6 13.4A1.7 2.2 30 1 1 8.8 11.6Z"
                 "M15.2 11.6A1.7 2.2 -30 1 0 17.4 13.4A1.7 2.2 -30 1 0 15.2 11.6Z", None),
        ("stroke", "M9.6 11.2L8 7.2", 1.4),
        ("stroke", "M8 7.2L5.6 6.4", 1.4),
        ("stroke", "M8 7.2L8.8 4", 1.4),
        ("stroke", "M14.4 11.2L16 7.2", 1.4),
        ("stroke", "M16 7.2L18.4 6.4", 1.4),
        ("stroke", "M16 7.2L15.2 4", 1.4),
    ]),
    ("Fish", [
        ("evenodd", "M1.8 12C4.8 7 10.8 5.8 15 8.8L19.2 5.8L18.2 12L19.2 18.2L15 15.2C10.8 18.2 4.8 17 1.8 12Z"
                    + circle(6.2, 10.8, 1), None),
    ]),
    ("Shellfish", [
        ("fill", "M15.07 5.95A7.5 7.5 0 1 1 5 13L8.5 13A4 4 0 1 0 13.87 9.24Z"
                 "M15.6 4.4L20.6 1.8L21.4 8.4L16.6 7.6Z" + circle(7, 11.4, 1), None),
        ("stroke", "M6.6 13.6C5 15 3.4 15.4 1.8 15", 1.2),
        ("stroke", "M7 14.8C6 16.4 4.6 17.4 2.8 17.6", 1.2),
    ]),
    ("Vegetables", [
        ("fill", "M11.9 22L7.6 11.6C10.2 10.2 13.6 10.2 16.2 11.6Z"
                 "M12 9.8C12 7.4 13.6 5.6 16 5.2C16.4 7.8 15 9.6 12.6 10.2Z"
                 "M11.4 9.6C10.6 7.6 11.2 5.4 13 4C14.4 6 14.2 8.2 12.6 9.8Z"
                 "M10.8 10.2C9 9.4 7.8 7.6 7.8 5.4C10.2 5.8 11.6 7.4 11.8 9.6Z", None),
    ]),
    ("Cheese", [
        ("evenodd", "M2.6 18.4L2.6 13.8C2.6 13.3 2.9 12.9 3.4 12.8L20 8.2C20.7 8 21.4 8.5 21.4 9.2L21.4 18.4C21.4 19 21 19.4 20.4 19.4L3.6 19.4C3 19.4 2.6 19 2.6 18.4Z"
                    + circle(7, 16, 1.3) + circle(13, 14.4, 1.6) + circle(17.8, 17, 1.1), None),
    ]),
    ("Dessert", [
        ("fill", "M6.2 12.6L17.8 12.6L16.4 20.2C16.3 20.8 15.8 21.2 15.2 21.2L8.8 21.2C8.2 21.2 7.7 20.8 7.6 20.2Z"
                 "M6.4 12.2C5.1 12.2 4.2 11.1 4.4 9.9C4.6 8.9 5.4 8.2 6.4 8.2C6.4 6.4 7.9 5 9.7 5.2C10.3 4 11.6 3.2 13 3.4C14.8 3.6 16.1 5.1 16.1 6.8C17.6 6.8 18.8 8 18.8 9.5C18.8 11 17.6 12.2 16.1 12.2Z", None),
    ]),
    ("SpicyFood", [
        ("fill", "M7.4 8.2C9.8 8 11.9 9.2 13.3 11.4C15.3 14.4 17.6 16.9 20.8 18.4C17.8 20.8 13.6 20.2 10.8 17.2C8.4 14.6 7.4 11.6 7.4 8.2Z"
                 "M6.2 4C8.2 4 9.2 5.4 9 7.6L6.6 8.8C5.6 7.2 5.4 5.4 6.2 4Z", None),
    ]),
    ("AsianFood", [
        ("fill", "M2.8 12.2L21.2 12.2C21.2 17 17.1 20.8 12 20.8C6.9 20.8 2.8 17 2.8 12.2Z", None),
        ("stroke", "M2 20.6L22 20.6", 1.8),
        ("stroke", "M21.6 3.4L12.6 10.8", 1.5),
        ("stroke", "M22.4 6.2L16 10.8", 1.5),
    ]),
    ("Buffet", [
        ("fill", "M3 16.4C3 11.4 7 7.4 12 7.4C17 7.4 21 11.4 21 16.4Z"
                 "M2 17.6L22 17.6C22.6 17.6 23 18 23 18.6C23 19.2 22.6 19.6 22 19.6L2 19.6C1.4 19.6 1 19.2 1 18.6C1 18 1.4 17.6 2 17.6Z"
                 + circle(12, 5.4, 1.5), None),
    ]),
    ("Aperitif", [
        ("fill", "M3.4 4.4L20.6 4.4L13 13.2L11 13.2Z"
                 "M11.1 12.8L12.9 12.8L12.9 19.2L11.1 19.2Z"
                 "M7.4 19.2L16.6 19.2C17.2 19.2 17.6 19.6 17.6 20.2C17.6 20.8 17.2 21.2 16.6 21.2L7.4 21.2C6.8 21.2 6.4 20.8 6.4 20.2C6.4 19.6 6.8 19.2 7.4 19.2Z", None),
    ]),
]

def social():
    left = [transform(d, -0.2, 3.4, 0.8, -22, 12, 21) for _, d, _ in GLASS]
    right = [transform(d, 4.8, 3.4, 0.8, 22, 12, 21) for _, d, _ in GLASS]
    return left + right

HEADER = '''package com.beertracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beertracker.R
import com.beertracker.domain.Pairing

/**
 * One flat silhouette per [Pairing], in the spirit of the food pairing
 * symbols on systembolaget.se but drawn here rather than copied. Each is a
 * single black-filled 24 by 24 vector, tinted by the caller so it follows
 * the theme like every other icon in the app.
 */
'''

def emit_icon(name, parts):
    body = []
    for kind, d, width in parts:
        commands = d if isinstance(d, list) else parse(d)
        if kind == "stroke":
            body.append(
                "    path(\n"
                "        stroke = SolidColor(Color.Black),\n"
                "        strokeLineWidth = %sf,\n"
                "        strokeLineCap = StrokeCap.Round,\n"
                "    ) {\n%s\n    }" % (width, kotlin_path(commands))
            )
        else:
            fill_type = ("        pathFillType = PathFillType.EvenOdd,\n"
                         if kind == "evenodd" else "")
            body.append(
                "    path(\n        fill = SolidColor(Color.Black),\n%s    ) {\n%s\n    }"
                % (fill_type, kotlin_path(commands))
            )
    return ("internal val Pairing%sIcon: ImageVector = ImageVector.Builder(\n"
            "    name = \"Pairing%s\",\n"
            "    defaultWidth = 24.dp,\n"
            "    defaultHeight = 24.dp,\n"
            "    viewportWidth = 24f,\n"
            "    viewportHeight = 24f,\n"
            ").apply {\n%s\n}.build()\n" % (name, name, "\n".join(body)))

ENUM_NAMES = [
    "PORK", "POULTRY", "LAMB", "BEEF", "GAME", "FISH", "SHELLFISH",
    "VEGETABLES", "CHEESE", "DESSERT", "SPICY", "ASIAN", "BUFFET",
    "APERITIF", "SOCIAL",
]
STRING_KEYS = [
    "pork", "poultry", "lamb", "beef", "game", "fish", "shellfish",
    "vegetables", "cheese", "dessert", "spicy", "asian", "buffet",
    "aperitif", "social",
]

def tail():
    icon_names = [name for name, _ in ICONS] + ["SocialDrink"]
    icon_arms = "\n".join(
        "    Pairing.%s -> Pairing%sIcon" % (enum, icon)
        for enum, icon in zip(ENUM_NAMES, icon_names))
    label_arms = "\n".join(
        "    Pairing.%s -> R.string.pairing_%s" % (enum, key)
        for enum, key in zip(ENUM_NAMES, STRING_KEYS))
    return '''/**
 * Exhaustive on purpose: a new [Pairing] cannot compile until it has an
 * icon and a label.
 */
internal fun pairingIcon(pairing: Pairing): ImageVector = when (pairing) {
%s
}

internal fun pairingLabelRes(pairing: Pairing): Int = when (pairing) {
%s
}

/** The translated display label for a pairing. */
@Composable
internal fun pairingLabel(pairing: Pairing): String = stringResource(pairingLabelRes(pairing))

@Composable
internal fun PairingIcon(
    pairing: Pairing,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = pairingIcon(pairing),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
''' % (icon_arms, label_arms)

if __name__ == "__main__":
    chunks = [HEADER]
    for name, parts in ICONS:
        chunks.append(emit_icon(name, parts))
    chunks.append(emit_icon("SocialDrink", [("fill", part, None) for part in social()]))
    chunks.append(tail())
    target = os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        "app", "src", "main", "java", "com", "beertracker", "ui", "components",
        "PairingIcons.kt")
    open(target, "w", encoding="utf-8", newline="\n").write("\n".join(chunks))
    print("wrote " + target)
