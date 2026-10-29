"""Đọc catalog thiết kế thành JSON trên stdout; không sửa nguồn hoặc publish bài.

Chỉ nhận bảng canonical đúng shape; các bảng giải thích/prerequisite override
không được ghi đè topic. Caller phải review patch snapshot trước khi lưu.
"""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ["core-foundations.md", "core-api-data.md", "core-security-operations.md", "devops-catalog.md"]


def selectors(text):
    """TOP yêu cầu toàn topic; .A/.B chỉ giữ đúng CAP, không thêm cạnh chặng."""
    if text.strip() == "—":
        return []
    result = []
    for token in text.split(","):
        token = token.strip()
        match = re.fullmatch(r"TOP-([A-Z]+-\d{3})(?:\.([AB]))?", token)
        if not match:
            raise ValueError("Invalid prerequisite selector: " + token)
        result.append({"kind": "competency" if match[2] else "topic",
                       "id": "CAP-" + match[1] + "-" + match[2] if match[2] else token})
    return result


def topic_ids(text):
    """Mở dải ID explicit trong bảng DS, không suy topic từ vị trí dòng."""
    result = []
    for domain, start, end in re.findall(r"TOP-([A-Z]+)-(\d{3})(?:[–−](\d{3}))?", text):
        last = int(end or start)
        if last < int(start):
            raise ValueError("Reversed topic range")
        result.extend(f"TOP-{domain}-{number:03}" for number in range(int(start), last + 1))
    if not result:
        raise ValueError("Stage has no explicit topic")
    return result


def outline():
    """Trả outline95/190 có provenance; không tạo lesson, rubric hoặc evidence."""
    topics, competencies, placements, milestones = [], [], [], []
    for name in SOURCES:
        relative = "docs/curriculum/" + name
        stage = None
        competency_table = False
        for line in (ROOT / relative).read_text(encoding="utf-8").splitlines():
            heading = re.match(r"### (S\d{2})\b", line)
            if heading:
                stage = heading[1]
            if line.startswith("#"):
                competency_table = False
            if not line.startswith("| "):
                continue
            cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
            if cells[0].startswith("Topic"):
                # Bảng giải thích shared prerequisites cũng có ba cột; chỉ
                # header A/B canonical mới được phép sinh competency.
                competency_table = len(cells) == 3 and cells[1].startswith("A —")
            if re.fullmatch(r"TOP-[A-Z]+-\d{3}", cells[0]):
                identifier = cells[0]
                if len(cells) == 6 and re.fullmatch(r"K\d{2}(?:,K\d{2})*", cells[2]):
                    topics.append({"id": identifier, "title": cells[1], "status": "planned",
                                   "level": cells[3], "knowledgeAreas": cells[2].split(","),
                                   "prerequisites": selectors(cells[4]), "source": relative})
                    if stage and name != "devops-catalog.md":
                        placements.append({"roadmap": "java-backend", "stage": stage, "topicId": identifier})
                elif len(cells) == 3 and competency_table:
                    for suffix, description in zip(("A", "B"), cells[1:]):
                        competencies.append({"id": identifier.replace("TOP-", "CAP-", 1) + "-" + suffix,
                                             "topicId": identifier, "description": description})
            if name == "devops-catalog.md" and re.match(r"DS\d{2}\b", cells[0]):
                for identifier in topic_ids(cells[1]):
                    placements.append({"roadmap": "devops", "stage": cells[0][:4], "topicId": identifier})
            if name == "devops-catalog.md" and re.match(r"DP[0-5] ·", cells[0]):
                milestones.append({"id": cells[0].split(" ·")[0], "readiness": selectors(cells[1])})
    if len(topics) != 95 or len(competencies) != 190 or len({t["id"] for t in topics}) != 95:
        raise ValueError(f"Canonical outline counts differ: {len(topics)} topics/{len(competencies)} competencies")
    return {"schemaVersion": 2, "catalogVersion": 2, "topics": topics, "competencies": competencies,
            "placements": placements, "milestones": milestones, "units": [], "packages": [], "aliases": []}


if __name__ == "__main__":
    print(json.dumps(outline(), ensure_ascii=False, indent=2))
