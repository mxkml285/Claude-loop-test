import unittest

from textutils import slugify


class SlugifyTests(unittest.TestCase):
    def test_lowercases_text(self):
        self.assertEqual(slugify("Hello World"), "hello-world")
        self.assertEqual(slugify("PYTHON"), "python")
        self.assertEqual(slugify("MiXeD"), "mixed")

    def test_spaces_become_hyphens(self):
        self.assertEqual(slugify("hello world"), "hello-world")
        self.assertEqual(slugify("a b c"), "a-b-c")

    def test_underscores_become_hyphens(self):
        self.assertEqual(slugify("hello_world"), "hello-world")
        self.assertEqual(slugify("a_b c"), "a-b-c")

    def test_lowercase_umlauts_are_transliterated(self):
        self.assertEqual(slugify("äöüß"), "aeoeuess")
        self.assertEqual(slugify("Größe"), "groesse")
        self.assertEqual(slugify("Straße"), "strasse")
        self.assertEqual(slugify("Fahrvergnügen"), "fahrvergnuegen")

    def test_uppercase_umlauts_are_transliterated(self):
        self.assertEqual(slugify("ÄÖÜ"), "aeoeue")
        self.assertEqual(slugify("ÄPFEL UND ÖL"), "aepfel-und-oel")
        self.assertEqual(slugify("Über uns"), "ueber-uns")

    def test_other_special_characters_are_removed(self):
        self.assertEqual(slugify("Hello, World!"), "hello-world")
        self.assertEqual(slugify("C++ & Python#3"), "c-python3")
        self.assertEqual(slugify("Grüße, Straße!"), "gruesse-strasse")

    def test_digits_are_preserved(self):
        self.assertEqual(slugify("abc123"), "abc123")
        self.assertEqual(slugify("Version 2.0"), "version-20")

    def test_consecutive_hyphens_are_collapsed(self):
        self.assertEqual(slugify("a---b"), "a-b")
        self.assertEqual(slugify("hello   world"), "hello-world")
        self.assertEqual(slugify("a - - b"), "a-b")

    def test_leading_and_trailing_hyphens_are_stripped(self):
        self.assertEqual(slugify("-hello-"), "hello")
        self.assertEqual(slugify("---a---"), "a")
        self.assertEqual(slugify("  hello  "), "hello")
        self.assertEqual(slugify("__hello__"), "hello")

    def test_empty_input_returns_empty_string(self):
        self.assertEqual(slugify(""), "")

    def test_special_characters_only_returns_empty_string(self):
        self.assertEqual(slugify("!!!"), "")
        self.assertEqual(slugify("---"), "")
        self.assertEqual(slugify("   "), "")
        self.assertEqual(slugify("___"), "")
        self.assertEqual(slugify("@#$%^&*()"), "")

    def test_combined_rules(self):
        self.assertEqual(
            slugify("  Der Größte_Test --- für Ölpreise!!  "),
            "der-groesste-test-fuer-oelpreise",
        )

    def test_non_german_accents_reduce_to_ascii(self):
        self.assertEqual(slugify("Café"), "cafe")
        self.assertEqual(slugify("naïve"), "naive")
        self.assertEqual(slugify("北京"), "")

    def test_tabs_and_newlines_are_treated_as_separators(self):
        self.assertEqual(slugify("hello\tworld"), "hello-world")
        self.assertEqual(slugify("hello\nworld"), "hello-world")

    def test_slugify_is_idempotent(self):
        values = [
            "  Der Größte_Test --- für Ölpreise!!  ",
            "Hello, World!",
            "äöü",
        ]
        for value in values:
            with self.subTest(value=value):
                self.assertEqual(slugify(slugify(value)), slugify(value))


if __name__ == "__main__":
    unittest.main()
