"""Apply Taiwan place names without changing aerial media or caption timing."""
import argparse
import json
from pathlib import Path

TERMS = {
    "迪拜": "杜拜",
    "悉尼": "雪梨",
    "澳大利亞": "澳洲",
    "新西蘭": "紐西蘭",
    "沙特阿拉伯": "沙烏地阿拉伯",
    "阿布扎比": "阿布達比",
    "加利福利亞州": "加利福尼亞州",
    "洲際公路": "州際公路",
    "格林蘭島": "格陵蘭島",
    "落基山脈": "洛磯山脈",
    "休斯頓": "休士頓",
    "新奧爾良": "紐奧良",
    "珀斯": "伯斯",
    "吉爾吉斯斯坦": "吉爾吉斯",
    "紅海里的": "紅海裡的",
    "冰川穀": "冰河谷",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    path = Path(__file__).resolve().parents[1] / "app/src/main/assets/aerials.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    original = json.loads(path.read_text(encoding="utf-8"))
    changes = []
    for video in data["videos"]:
        for caption in video["captions"]:
            before = caption[1]
            for source, target in TERMS.items():
                caption[1] = caption[1].replace(source, target)
            if "Moving from day to night" in caption[2]:
                caption[1] = caption[1].replace("自夜向晝", "自晝向夜")
            if "Jamaica" in caption[2]:
                caption[1] = caption[1].replace("地中海", "加勒比海")
            if "Horn of Africa" in caption[2]:
                caption[1] = caption[1].replace("非洲好望角", "非洲之角")
            if before != caption[1]:
                changes.append([before, caption[1]])
    if args.check:
        if changes:
            raise SystemExit("Taiwan place names need updating: " + json.dumps(changes, ensure_ascii=False))
    else:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for old_video, new_video in zip(original["videos"], data["videos"], strict=True):
        assert {k: v for k, v in old_video.items() if k != "captions"} == {
            k: v for k, v in new_video.items() if k != "captions"}
        for old, new in zip(old_video["captions"], new_video["captions"], strict=True):
            assert old[0] == new[0] and old[2:] == new[2:]
    print(json.dumps({"videos": len(data["videos"]), "changed": len(changes),
                      "examples": changes[:5], "media_and_timing_preserved": True}, ensure_ascii=False))


if __name__ == "__main__":
    main()
