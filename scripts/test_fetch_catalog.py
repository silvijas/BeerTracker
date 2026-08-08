"""Tests for the seed generator's pure mapping and snapshot logic.

The SAMPLE_BEER product below is intentionally the same sample used by
SystembolagetCatalogFetcherTest.kt so the two mappers are checked against
identical input.
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))

import fetch_catalog

SAMPLE_BEER = {
    "productId": "50786609",
    "productNumber": "1324515",
    "productNumberShort": "13245",
    "productNameBold": "Omnipollo",
    "productNameThin": "Prodigal Pale Ale",
    "producerName": "Omnipollo",
    "categoryLevel1": "Öl",
    "categoryLevel2": "Ale",
    "categoryLevel3": "Pale Ale",
    "alcoholPercentage": 5.2,
    "volume": 330.0,
    "price": 25.9,
    "country": "Sverige",
    "images": [
        {"imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609"}
    ],
}

SAMPLE_WINE = {
    "productNumber": "7000101",
    "productNameBold": "Some Wine",
    "categoryLevel1": "Vin",
}


class IsBeerTest(unittest.TestCase):
    def test_beer_is_beer(self):
        self.assertTrue(fetch_catalog.is_beer(SAMPLE_BEER))

    def test_wine_is_not_beer(self):
        self.assertFalse(fetch_catalog.is_beer(SAMPLE_WINE))


class MapProductTest(unittest.TestCase):
    def test_maps_all_fields(self):
        self.assertEqual(
            fetch_catalog.map_product(SAMPLE_BEER),
            {
                "articleNumber": "1324515",
                "articleNumberShort": "13245",
                "name": "Omnipollo Prodigal Pale Ale",
                "brewery": "Omnipollo",
                "type": "Ale",
                "alcoholPercent": 5.2,
                "volumeMl": 330,
                "price": 25.9,
                "country": "Sverige",
                "imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609",
            },
        )

    def test_missing_optional_fields_become_none_or_fallbacks(self):
        mapped = fetch_catalog.map_product({"productNumber": 42, "categoryLevel1": "Öl"})
        self.assertEqual(mapped["articleNumber"], "42")
        self.assertIsNone(mapped["articleNumberShort"])
        self.assertEqual(mapped["name"], "")
        self.assertEqual(mapped["brewery"], "")
        self.assertEqual(mapped["type"], "Öl")
        self.assertIsNone(mapped["alcoholPercent"])
        self.assertIsNone(mapped["volumeMl"])
        self.assertIsNone(mapped["price"])
        self.assertIsNone(mapped["country"])
        self.assertIsNone(mapped["imageUrl"])

    def test_type_falls_back_to_category_level_3(self):
        product = dict(SAMPLE_BEER, categoryLevel2=None)
        self.assertEqual(fetch_catalog.map_product(product)["type"], "Pale Ale")

    def test_empty_image_list_gives_none(self):
        product = dict(SAMPLE_BEER, images=[])
        self.assertIsNone(fetch_catalog.map_product(product)["imageUrl"])


class ToSnapshotTest(unittest.TestCase):
    def test_filters_to_beer_only(self):
        snapshot = fetch_catalog.to_snapshot([SAMPLE_BEER, SAMPLE_WINE], "2026-08-08")
        self.assertEqual(len(snapshot["beers"]), 1)
        self.assertEqual(snapshot["snapshotVersion"], "2026-08-08")

    def test_sorts_and_deduplicates_by_article_number(self):
        a = dict(SAMPLE_BEER, productNumber="9")
        b = dict(SAMPLE_BEER, productNumber="10")
        duplicate = dict(SAMPLE_BEER, productNumber="9")
        snapshot = fetch_catalog.to_snapshot([a, duplicate, b], "2026-08-08")
        self.assertEqual(
            [beer["articleNumber"] for beer in snapshot["beers"]],
            ["10", "9"],
        )

    def test_keeps_previous_version_when_content_is_unchanged(self):
        first = fetch_catalog.to_snapshot([SAMPLE_BEER], "2026-08-01")
        second = fetch_catalog.to_snapshot([SAMPLE_BEER], "2026-08-08", previous_snapshot=first)
        self.assertEqual(second["snapshotVersion"], "2026-08-01")

    def test_bumps_version_when_content_changes(self):
        first = fetch_catalog.to_snapshot([SAMPLE_BEER], "2026-08-01")
        changed = dict(SAMPLE_BEER, price=30.0)
        second = fetch_catalog.to_snapshot([changed], "2026-08-08", previous_snapshot=first)
        self.assertEqual(second["snapshotVersion"], "2026-08-08")


if __name__ == "__main__":
    unittest.main()
