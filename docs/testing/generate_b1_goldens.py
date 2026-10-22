"""Independent B1 oracle. No production code import; canonical text is reviewed explicitly.

Counter-based feature aggregation separates this reference from a streaming implementation.
Run with Python 3; all imports are from the standard library.
Golden generation consumes explicit canonical fixtures; unicodedata separately checks them.
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
        # Fixture-only sanity check: their whitespace characters agree in Python and Java.
        assert " ".join(unicodedata.normalize("NFKC", original).lower().split()) == canonical
        print(rounds, repr(original), hashlib.sha256(vector_bytes(canonical, rounds)).hexdigest())
