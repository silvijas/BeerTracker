"""Regenerates the bundled Systembolaget beer catalog seed.

Walks the public product-search API page by page, keeps only beer
(categoryLevel1 == "Öl"), maps each product to the minimal schema the app
bundles, and writes app/src/main/assets/catalog/beers.json.

This is a development tool for creating or regenerating the seed asset that
ships inside the APK. Routine catalog updates happen inside the app itself,
which fetches the same endpoint with the same mapping (see
app/src/main/java/com/beertracker/data/CatalogFetcher.kt). The map_product
function here and mapProduct there must stay field for field identical.

Usage:
    $env:SYSTEMBOLAGET_API_KEY = "<public product-search key>"
    python scripts/fetch_catalog.py

    python scripts/fetch_catalog.py --input-file assortment.json

The optional --input-file skips the network and reads a JSON array of raw
product objects using the same field names as the live API, for example the
community mirror file data/assortment.json from the
AlexGustafsson/systembolaget-api-data repository.

The key is the public product-search key visible in Systembolaget's own
website JavaScript; it is not a secret. It is read from the environment here
only to keep this script copy-paste safe in shell history and logs.
"""

import argparse
import json
import os
import sys
import time
import urllib.request
from urllib.parse import urlencode

BASE_URL = "https://api-extern.systembolaget.se/sb-api-ecommerce/v1/productsearch/search"
REFERER = "https://www.systembolaget.se/"
PAGE_SIZE = 30
PAGE_DELAY_SECONDS = 0.3
MAX_PAGES = 500
MAX_RETRIES = 5
RETRY_DELAY_SECONDS = 1.0
# Two full sweeps with different deterministic sort keys, unioned by
# articleNumber in to_snapshot. Necessary because the live search API has no
# stable sort for items that tie on the sort field: their relative order can
# still drift between page requests, so a single sweep silently drops a
# meaningful slice of a category this large (measured 2026-08-09: a lone
# Name-sorted sweep recovered only 4826 of 4984 Öl products). A second sweep
# on an unrelated field ties differently and recovers nearly all the rest
# (4966 of 4984 unioned).
SORT_SWEEPS = [("Name", "Ascending"), ("Price", "Ascending")]
OUTPUT_PATH = os.path.join("app", "src", "main", "assets", "catalog", "beers.json")
MIN_EXPECTED_BEERS = 4000
MAX_EXPECTED_BEERS = 6000


def is_beer(product):
    return product.get("categoryLevel1") == "Öl"


def map_product(product):
    """Maps one raw API product to the minimal bundled schema.

    Must stay field for field identical to mapProduct in
    app/src/main/java/com/beertracker/data/CatalogFetcher.kt.
    """
    images = product.get("images") or []
    image_url = None
    if images:
        candidate = (images[0] or {}).get("imageUrl")
        if candidate:
            image_url = candidate
    name_parts = [
        (product.get("productNameBold") or "").strip(),
        (product.get("productNameThin") or "").strip(),
    ]
    name = " ".join(part for part in name_parts if part)
    volume = product.get("volume")
    return {
        "articleNumber": str(product.get("productNumber") or ""),
        "articleNumberShort": str(product.get("productNumberShort") or "") or None,
        "name": name,
        "brewery": product.get("producerName") or "",
        "type": product.get("categoryLevel2") or product.get("categoryLevel3") or "Öl",
        "alcoholPercent": product.get("alcoholPercentage"),
        "volumeMl": int(round(volume)) if volume is not None else None,
        "price": product.get("price"),
        "country": product.get("country"),
        "imageUrl": image_url,
    }


def to_snapshot(raw_products, today_utc, previous_snapshot=None):
    """Filters, maps, deduplicates, and sorts. Keeps the previous
    snapshotVersion when the beer list is byte-for-byte unchanged, so an
    unchanged assortment produces an unchanged file."""
    unique = {}
    for product in raw_products:
        if not is_beer(product):
            continue
        beer = map_product(product)
        if beer["articleNumber"]:
            unique[beer["articleNumber"]] = beer
    ordered = [unique[key] for key in sorted(unique)]
    version = today_utc
    if previous_snapshot is not None and previous_snapshot.get("beers") == ordered:
        version = previous_snapshot.get("snapshotVersion", today_utc)
    return {"snapshotVersion": version, "beers": ordered}


def page_url(page, sort_by, sort_direction):
    params = urlencode(
        {
            "size": PAGE_SIZE,
            "page": page,
            "categoryLevel1": "Öl",
            "sortBy": sort_by,
            "sortDirection": sort_direction,
        }
    )
    return BASE_URL + "?" + params


def default_http_get(url, api_key):
    request = urllib.request.Request(
        url,
        headers={
            "ocp-apim-subscription-key": api_key,
            "Referer": REFERER,
            "Accept": "application/json",
            "User-Agent": "BeerTracker seed generator (personal project)",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


def fetch_page_with_retry(url, http_get, sleep):
    """A handful of the ~170 sequential requests in a full sweep hit
    transient network errors in practice; retry with backoff instead of
    aborting the whole sweep."""
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            return json.loads(http_get(url))
        except Exception as error:
            last_error = error
            sys.stderr.write("  %s (attempt %d/%d)\n" % (error, attempt, MAX_RETRIES))
            sleep(RETRY_DELAY_SECONDS * attempt)
    raise last_error


def fetch_sweep(sort_by, sort_direction, http_get, sleep):
    """Pages through one full sweep, following metadata.nextPage until it
    reports -1 (or is absent). NOTE: the API signals the end of results via
    nextPage, not an empty products array -- an out-of-range page 404s
    instead of returning {"products": []}."""
    products = []
    page = 1
    for _ in range(MAX_PAGES):
        payload = fetch_page_with_retry(page_url(page, sort_by, sort_direction), http_get, sleep)
        page_products = payload.get("products") or []
        products.extend(page_products)
        sys.stderr.write(
            "%s %s page %d: %d products, %d total\n"
            % (sort_by, sort_direction, page, len(page_products), len(products))
        )
        next_page = payload.get("metadata", {}).get("nextPage")
        if not next_page or next_page <= 0:
            break
        page = next_page
        sleep(PAGE_DELAY_SECONDS)
    return products


def fetch_all_products(api_key, http_get=None, sleep=time.sleep):
    getter = http_get or (lambda url: default_http_get(url, api_key))
    products = []
    for sort_by, sort_direction in SORT_SWEEPS:
        products.extend(fetch_sweep(sort_by, sort_direction, getter, sleep))
    return products


def main():
    parser = argparse.ArgumentParser(
        description="Regenerate the bundled Systembolaget beer catalog seed."
    )
    parser.add_argument(
        "--input-file",
        help="read raw products from a JSON file instead of calling the API",
    )
    parser.add_argument("--output", default=OUTPUT_PATH)
    args = parser.parse_args()

    if args.input_file:
        with open(args.input_file, encoding="utf-8") as handle:
            raw = json.load(handle)
        raw_products = raw if isinstance(raw, list) else raw.get("products") or []
    else:
        api_key = os.environ.get("SYSTEMBOLAGET_API_KEY")
        if not api_key:
            sys.exit("SYSTEMBOLAGET_API_KEY is not set")
        raw_products = fetch_all_products(api_key)

    previous = None
    if os.path.exists(args.output):
        with open(args.output, encoding="utf-8") as handle:
            previous = json.load(handle)

    today = time.strftime("%Y-%m-%d", time.gmtime())
    snapshot = to_snapshot(raw_products, today, previous)

    count = len(snapshot["beers"])
    if not MIN_EXPECTED_BEERS <= count <= MAX_EXPECTED_BEERS:
        sys.exit(
            "Implausible beer count %d (expected %d to %d); refusing to write"
            % (count, MIN_EXPECTED_BEERS, MAX_EXPECTED_BEERS)
        )

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(snapshot, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")
    print(
        "Wrote %d beers (snapshotVersion %s) to %s"
        % (count, snapshot["snapshotVersion"], args.output)
    )


if __name__ == "__main__":
    main()
