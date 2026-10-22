"""B1独立参考实现：不导入生产代码，显式审查规范化文本期望。

通过Counter聚合特征，使参考路径独立于生产流式实现。
使用Python 3运行，所有依赖均来自标准库。
黄金生成采用明确的规范化夹具，unicodedata另外核对这些期望。
"""
from collections import Counter
import hashlib
import math
import struct
import unicodedata

FIXTURES = [
    (32, "", ""),
    (1, "ababa", "ababa"),
    (32, "ababa", "ababa"),
    (32, "　ＡＢＣ\u00a0DEF\u2007\tＧ　", "abc def g"),
    (32, "中文，单字。", "中文,单字。"),
    (32, "😀🚀 😀", "😀🚀 😀"),
    (32, "e\u0301 É", "é é"),
    (32, "İ I", "i\u0307 i"),
]


def vector_bytes(canonical, rounds):
    features = Counter()
    for word in canonical.split(" "):
        features.update("U:" + char for char in word)
        features.update("B:" + word[i:i + 2] for i in range(len(word) - 1))
    buckets = [0] * 256
    for feature, count in features.items():
        digest = feature.encode("utf-8")
        for _ in range(rounds):
            digest = hashlib.sha256(digest).digest()
        buckets[digest[0]] += count if digest[1] % 2 == 0 else -count
    norm = math.sqrt(sum(value * value for value in buckets))
    return b"".join(struct.pack(">f", value / norm if norm else 0.0) for value in buckets)


if __name__ == "__main__":
    for rounds, original, canonical in FIXTURES:
        # 仅核对夹具：这些样本中的空白字符在Python与Java中含义一致。
        assert " ".join(unicodedata.normalize("NFKC", original).lower().split()) == canonical
        print(rounds, repr(original), hashlib.sha256(vector_bytes(canonical, rounds)).hexdigest())
