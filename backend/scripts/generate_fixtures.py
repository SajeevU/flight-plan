"""Generates the offline fixture data used when CAAS is unreachable or no key is configured.

The shapes mirror the live CAAS APIs (checked October 2026):
  /geopoints/list/{fixes,navaids,airports}  -> ["NAME (lat,lon)", ...]   names repeat worldwide
  /geopoints/list/airways                   -> ["A464", ...]             names only
  /geopoints/search/airways/{term}          -> ["A464: [FIX1,FIX2,...]"] fixes in order, no airports
  /flight-manager/displayAll                -> Flight Object Model; routeElement[] with designatedPoint,
                                               seqNum from 0, and the airway flown to the next element
Names and coordinates are illustrative, not real aeronautical data.
Re-run with: python3 scripts/generate_fixtures.py
"""
import json, uuid, pathlib

here = pathlib.Path(__file__).parent.parent / "src" / "main" / "resources" / "fixtures"

airports = {
    "WSSS": (1.36, 103.99), "WMKK": (2.75, 101.71), "VTBS": (13.69, 100.75),
    "WIII": (-6.13, 106.66), "RPLL": (14.51, 121.02), "VHHH": (22.31, 113.92),
    "VVTS": (10.82, 106.65), "WBKK": (5.94, 116.05), "YSSY": (-33.95, 151.18),
}

# VOR/DME stations.
navaids = {"VJR": (1.62, 103.30), "VPL": (5.30, 100.30), "BKK": (12.80, 100.60), "PKP": (-2.15, 106.14)}

fixes = {
    "PIBOS": (2.05, 102.80), "ATMAX": (2.40, 102.20), "BIKTA": (1.95, 103.55), "TODAM": (2.45, 102.95),
    "OSOMA": (4.50, 101.20), "LIPRO": (7.20, 99.80), "DOLOX": (9.80, 99.90),
    "ARAMA": (3.20, 104.20), "IGARI": (6.94, 103.59), "KADAX": (9.40, 102.20), "BIPOP": (11.30, 101.40),
    "BATAM": (0.70, 104.10), "LAMOB": (-4.20, 106.40), "ANITO": (-0.60, 105.40), "TELOK": (-3.60, 105.80),
    "LAVAX": (3.50, 105.50), "MABLI": (6.80, 107.20), "SABIP": (10.20, 109.80), "DOTMI": (14.20, 111.80), "SIKOU": (18.60, 113.00),
    "BUNTA": (4.90, 108.50), "KAPOR": (8.60, 112.60), "NOBEN": (11.80, 116.80), "TONGA": (13.40, 119.40),
    "TIMEK": (5.20, 105.40), "VIKAS": (8.20, 106.00), "OBDOS": (3.20, 108.70), "MIBEL": (4.90, 112.40),
}

# Same names elsewhere in the world, as in the real data: the resolver must pick the nearby one.
duplicates = [("ARAMA", (45.10, -100.20)), ("VJR", (40.00, 10.00)), ("LAVAX", (-33.00, 151.00))]

airways = {
    "A464": ["VJR", "PIBOS", "ATMAX"],
    "G334": ["BIKTA", "TODAM"],
    "R208": ["OSOMA", "VPL", "LIPRO", "DOLOX", "BKK"],
    "M751": ["ARAMA", "IGARI", "KADAX", "BIPOP"],
    "W17":  ["IGARI", "VPL"],
    "B470": ["BATAM", "PKP", "LAMOB"],
    "G579": ["ANITO", "TELOK"],
    "M771": ["LAVAX", "MABLI", "SABIP", "DOTMI", "SIKOU"],
    "N891": ["LAVAX", "BUNTA", "KAPOR", "NOBEN", "TONGA"],
    "L642": ["TIMEK", "VIKAS"],
    "M768": ["MABLI", "VIKAS"],
    "A457": ["OBDOS", "MIBEL"],
    "R469": ["BUNTA", "MIBEL"],
}

def coord(name):
    return airports.get(name) or navaids.get(name) or fixes[name]

def fmt(name, c):
    return f"{name} ({c[0]:.2f},{c[1]:.2f})"

def flight(callsign, dep, dest, path, actype, reg, eobt, with_coords=False):
    """path: list of (point, airway flown to the next point)."""
    elements = []
    for i, (fix, awy) in enumerate(path):
        pos = {"designatedPoint": fix}
        if with_coords:
            lat, lon = coord(fix)
            pos.update({"lat": str(lat), "lon": str(lon)})  # CAAS sends these as strings
        el = {"position": pos, "seqNum": i, "airwayType": "NAMED"}
        if awy:
            el["airway"] = awy
        elements.append(el)
    return {
        "_id": uuid.uuid5(uuid.NAMESPACE_DNS, callsign).hex[:24],
        "messageType": "FPL",
        "aircraftIdentification": callsign,
        "filedRoute": {"routeElement": elements},
        "flightType": "S",
        "aircraft": {"aircraftType": actype, "aircraftRegistration": reg},
        "departure": {"departureAerodrome": dep, "dateOfFlight": "2026-10-08", "estimatedOffBLockTime": eobt},
        "arrival": {"destinationAerodrome": dest},
        "aircraftOperating": callsign[:3],
        "src": "FIXTURE",
        "lastUpdatedTimeStamp": "2026-10-07T08:00:00Z",
    }

flights = [
    flight("SIA200", "WSSS", "WMKK", [("VJR", "A464"), ("ATMAX", None)], "A359", "9VSHN", "01:30:00"),
    flight("MAS604", "WSSS", "WMKK", [("BIKTA", "G334"), ("TODAM", None)], "B738", "9MMXA", "02:15:00"),
    flight("THA404", "WSSS", "VTBS", [("ARAMA", "M751"), ("BIPOP", None)], "A333", "HSTEK", "03:00:00", with_coords=True),
    flight("SIA978", "WSSS", "VTBS", [("ARAMA", "M751"), ("IGARI", "W17"), ("VPL", "R208"), ("BKK", None)], "B78X", "9VOJA", "04:10:00"),
    flight("GIA821", "WSSS", "WIII", [("BATAM", "B470"), ("LAMOB", None)], "A333", "PKGPZ", "05:20:00"),
    flight("CPA712", "WSSS", "VHHH", [("LAVAX", "M771/N0480F380"), ("SIKOU", None)], "A35K", "BLXA", "06:05:00"),
    flight("PAL502", "WSSS", "RPLL", [("LAVAX", "N891"), ("TONGA", None)], "A321", "RPC9930", "07:00:00"),
    flight("VJC812", "WSSS", "VVTS", [("TIMEK", "L642"), ("VIKAS", None)], "A321", "VNA697", "07:45:00"),
    flight("MAS2611", "WSSS", "WBKK", [("OBDOS", "A457"), ("MIBEL", None)], "B738", "9MMLK", "08:30:00"),
    flight("SIA622", "WSSS", "VHHH", [("LAVAX", "M771"), ("MABLI", "M771"), ("DOTMI", None)], "A388", "9VSKS", "09:15:00"),
    flight("SIA231", "WSSS", "YSSY", [], "A359", "9VSHA", "10:00:00"),
]

out = here
out.mkdir(parents=True, exist_ok=True)
(out / "airports.json").write_text(json.dumps([fmt(n, c) for n, c in airports.items()], indent=1))
(out / "navaids.json").write_text(json.dumps([fmt(n, c) for n, c in navaids.items()] + [fmt("VJR", (40.0, 10.0))], indent=1))
(out / "fixes.json").write_text(json.dumps([fmt(n, c) for n, c in fixes.items()] + [fmt(n, c) for n, c in duplicates if n != "VJR"], indent=1))
(out / "airways.json").write_text(json.dumps(list(airways) + ["Z999"], indent=1))
(out / "airway-search.json").write_text(json.dumps([f"{a}: [{','.join(pts)}]" for a, pts in airways.items()], indent=1))
(out / "flights.json").write_text(json.dumps(flights, indent=1))
print(f"{len(flights)} flights, {len(fixes)} fixes, {len(navaids)} navaids, {len(airways)} airways")
