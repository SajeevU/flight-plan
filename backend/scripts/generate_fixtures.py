"""Generates the offline fixture data used when no CAAS API key is configured.

The shapes mirror the CAAS APIs: fixes/airways/airports are "NAME (lat,lon)"
strings and flights follow the Flight Object Model. Names and coordinates are
illustrative, not real aeronautical data. Re-run with: python3 scripts/generate_fixtures.py
"""
import json, uuid, pathlib

here = pathlib.Path(__file__).parent.parent / "src" / "main" / "resources" / "fixtures"

airports = {
    "WSSS": (1.36, 103.99), "WMKK": (2.75, 101.71), "VTBS": (13.69, 100.75),
    "WIII": (-6.13, 106.66), "RPLL": (14.51, 121.02), "VHHH": (22.31, 113.92),
    "VVTS": (10.82, 106.65), "WBKK": (5.94, 116.05),
}

fixes = {
    # Singapore - Kuala Lumpur
    "VJR": (1.62, 103.30), "PIBOS": (2.05, 102.80), "ATMAX": (2.40, 102.20),
    "BIKTA": (1.95, 103.55), "TODAM": (2.45, 102.95),
    # Kuala Lumpur - Bangkok
    "OSOMA": (4.50, 101.20), "VPL": (5.30, 100.30), "LIPRO": (7.20, 99.80), "DOLOX": (9.80, 99.90), "BKK": (12.80, 100.60),
    # Singapore - Bangkok direct over the Gulf
    "ARAMA": (3.20, 104.20), "IGARI": (6.94, 103.59), "KADAX": (9.40, 102.20), "BIPOP": (11.30, 101.40),
    # Singapore - Jakarta
    "BATAM": (0.70, 104.10), "PKP": (-2.15, 106.14), "LAMOB": (-4.20, 106.40),
    "ANITO": (-0.60, 105.40), "TELOK": (-3.60, 105.80),
    # Singapore - Hong Kong / Manila / Ho Chi Minh / Kota Kinabalu
    "LAVAX": (3.50, 105.50), "MABLI": (6.80, 107.20), "SABIP": (10.20, 109.80), "DOTMI": (14.20, 111.80), "SIKOU": (18.60, 113.00),
    "BUNTA": (4.90, 108.50), "KAPOR": (8.60, 112.60), "NOBEN": (11.80, 116.80), "TONGA": (13.40, 119.40),
    "TIMEK": (5.20, 105.40), "VIKAS": (8.20, 106.00),
    "OBDOS": (3.20, 108.70), "MIBEL": (4.90, 112.40),
}

airways = {
    "A464": ["WSSS", "VJR", "PIBOS", "ATMAX", "WMKK"],
    "G334": ["WSSS", "BIKTA", "TODAM", "WMKK"],
    "R208": ["WMKK", "OSOMA", "VPL", "LIPRO", "DOLOX", "BKK", "VTBS"],
    "M751": ["WSSS", "ARAMA", "IGARI", "KADAX", "BIPOP", "VTBS"],
    "W17":  ["IGARI", "VPL"],
    "B470": ["WSSS", "BATAM", "PKP", "LAMOB", "WIII"],
    "G579": ["WSSS", "ANITO", "TELOK", "WIII"],
    "M771": ["WSSS", "LAVAX", "MABLI", "SABIP", "DOTMI", "SIKOU", "VHHH"],
    "N891": ["WSSS", "LAVAX", "BUNTA", "KAPOR", "NOBEN", "TONGA", "RPLL"],
    "L642": ["WSSS", "TIMEK", "VIKAS", "VVTS"],
    "M768": ["MABLI", "VIKAS"],
    "A457": ["WSSS", "OBDOS", "MIBEL", "WBKK"],
    "R469": ["BUNTA", "MIBEL"],
}

def coord(name):
    return airports.get(name) or fixes[name]

def fmt(name, c):
    return f"{name} ({c[0]},{c[1]})"

def flight(callsign, dep, dest, airway_path, actype, reg, eobt, with_coords=False):
    """airway_path: list of (fix, airway-to-next) after departure."""
    elements = []
    for i, (fix, awy) in enumerate(airway_path, start=1):
        pos = {"designatedPoint": fix}
        if with_coords:
            lat, lon = coord(fix)
            pos.update({"lat": lat, "lon": lon})
        el = {"seqNum": i, "position": pos}
        if awy:
            el["airway"] = awy
            el["airwayType"] = "ATS"
        elements.append(el)
    return {
        "_id": uuid.uuid5(uuid.NAMESPACE_DNS, callsign).hex[:24],
        "gufi": str(uuid.uuid5(uuid.NAMESPACE_URL, callsign)),
        "messageType": "FPL",
        "aircraftIdentification": callsign,
        "flightType": "S",
        "aircraftOperating": callsign[:3],
        "departure": {"departureAerodrome": dep, "dateOfFlight": "2026-10-06", "estimatedOffBLockTime": eobt},
        "arrival": {"destinationAerodrome": dest},
        "aircraft": {"aircraftType": actype, "aircraftRegistration": reg},
        "filedRoute": {"routeElement": elements},
        "lastUpdatedTimeStamp": "2026-10-05T08:00:00Z",
        "src": "FIXTURE",
    }

flights = [
    flight("SIA200", "WSSS", "WMKK", [("VJR", "A464"), ("ATMAX", None)], "A359", "9VSHN", "0130"),
    flight("MAS604", "WSSS", "WMKK", [("BIKTA", "G334"), ("TODAM", None)], "B738", "9MMXA", "0215"),
    flight("THA404", "WSSS", "VTBS", [("ARAMA", "M751"), ("BIPOP", None)], "A333", "HSTEK", "0300", with_coords=True),
    flight("SIA978", "WSSS", "VTBS", [("ARAMA", "M751"), ("IGARI", "W17"), ("VPL", "R208"), ("BKK", None)], "B78X", "9VOJA", "0410"),
    flight("GIA821", "WSSS", "WIII", [("BATAM", "B470"), ("LAMOB", None)], "A333", "PKGPZ", "0520"),
    flight("CPA712", "WSSS", "VHHH", [("LAVAX", "M771"), ("SIKOU", None)], "A35K", "BLXA", "0605", with_coords=True),
    flight("PAL502", "WSSS", "RPLL", [("LAVAX", "N891"), ("TONGA", None)], "A321", "RPC9930", "0700"),
    flight("VJC812", "WSSS", "VVTS", [("TIMEK", "L642"), ("VIKAS", None)], "A321", "VNA697", "0745"),
    flight("MAS2611", "WSSS", "WBKK", [("OBDOS", "A457"), ("MIBEL", None)], "B738", "9MMLK", "0830"),
    flight("SIA622", "WSSS", "VHHH", [("LAVAX", "M771"), ("MABLI", "M771"), ("DOTMI", None)], "A388", "9VSKS", "0915", with_coords=True),
]

(here / "airports.json").write_text(json.dumps([fmt(n, c) for n, c in airports.items()], indent=1))
(here / "fixes.json").write_text(json.dumps([fmt(n, c) for n, c in fixes.items()], indent=1))
(here / "airways.json").write_text(json.dumps([fmt(a, coord(p)) for a, pts in airways.items() for p in pts], indent=1))
(here / "flights.json").write_text(json.dumps(flights, indent=1))
print(f"{len(flights)} flights, {len(fixes)} fixes, {len(airways)} airways")
