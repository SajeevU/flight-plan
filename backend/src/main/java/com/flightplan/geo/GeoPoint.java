package com.flightplan.geo;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A named point. The aeronautical data service returns these as "NAME (lat,lon)", e.g. "WSSL (1.42,103.87)". */
public record GeoPoint(String name, double lat, double lon) implements LatLon {

    private static final Pattern ENTRY =
            Pattern.compile("^\\s*(\\S+)\\s*\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)\\s*$");

    /** Parses one entry, or returns null when it isn't in the expected format or is out of range. */
    public static GeoPoint parse(String entry) {
        if (entry == null) return null;
        Matcher m = ENTRY.matcher(entry);
        if (!m.matches()) return null;
        double lat = Double.parseDouble(m.group(2));
        double lon = Double.parseDouble(m.group(3));
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
        return new GeoPoint(m.group(1), lat, lon);
    }

    public static List<GeoPoint> parseAll(List<String> entries) {
        if (entries == null) return List.of();
        return entries.stream().map(GeoPoint::parse).filter(Objects::nonNull).toList();
    }
}
