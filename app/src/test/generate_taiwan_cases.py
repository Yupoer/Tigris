"""Regenerate title conversion cases using the independent OpenCC reference."""

import argparse
import json
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--opencc-library", type=Path)
args = parser.parse_args()
if args.opencc_library:
    sys.path.insert(0, str(args.opencc_library))

from opencc import OpenCC

converter = OpenCC("s2twp")
cases = [
    "嫁金钗",
    "Doctor X：白色黑手党时代",
    "伊甸之东",
    "沉默的巡游",
    "头发发展干杯干部里面面条，皇后在后宫",
    "软件鼠标视频打印机网络土豆西红柿",
    "咒术回战：涩谷事变",
    "后台计程车与出租车",
    "台灣臺北與芝麻街",
    "𬣙头发🎬 Netflix",
    "𬣙",
    "一出电影，打开电脑看电影",
    "重庆森林与重复播放",
    "少年派的奇幻漂流",
    "哆啦A梦：大雄的宇宙小战争",
    "蜘蛛侠：纵横宇宙",
    "网球王子与互联网",
    "录制视频，读取文件到内存",
    "电影《干杯，朋友》\n软件\t硬盘",
    "海上鋼琴師",
    "Netflix's Doctor X: Season 2 🎬",
    "İstanbul'da Bir Gece",
    "",
]
target = Path(__file__).with_name("resources") / "taiwan-text.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(
    json.dumps(
        [{"source": text, "taiwan": converter.convert(text)} for text in cases],
        ensure_ascii=False,
        indent=2,
    ) + "\n",
    encoding="utf-8",
    newline="\n",
)
print(f"Wrote {len(cases)} OpenCC reference cases to {target}")
