"""Small text helpers with no third-party dependencies."""

import re
import unicodedata

_UMLAUT_MAP = str.maketrans({"ä": "ae", "ö": "oe", "ü": "ue", "ß": "ss"})
_SEPARATORS = re.compile(r"[\s_]+")
_NON_SLUG = re.compile(r"[^a-z0-9-]")
_MULTI_HYPHEN = re.compile(r"-{2,}")

__all__ = ["slugify"]


def slugify(text: str) -> str:
    """Convert text into a URL-friendly slug.

    Rules applied, in order:
    - the text is lowercased
    - German umlauts are transliterated (ä->ae, ö->oe, ü->ue, ß->ss)
    - whitespace and underscores become hyphens
    - all remaining non-alphanumeric characters are removed
    - consecutive hyphens are collapsed into a single hyphen
    - leading and trailing hyphens are stripped

    Empty input, or input consisting only of characters that are removed
    by the rules above, results in an empty string.

    ``text`` must be a ``str``.
    """
    text = text.lower()
    text = text.translate(_UMLAUT_MAP)
    text = unicodedata.normalize("NFKD", text)
    text = _SEPARATORS.sub("-", text)
    text = _NON_SLUG.sub("", text)
    text = _MULTI_HYPHEN.sub("-", text)
    return text.strip("-")
