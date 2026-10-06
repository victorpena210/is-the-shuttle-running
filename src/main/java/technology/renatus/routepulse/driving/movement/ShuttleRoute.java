package technology.renatus.routepulse.driving.movement;

import java.util.ArrayList;
import java.util.List;

/** Organization-specific route corridor, in travel order (latitude,longitude per line). */
public record ShuttleRoute(List<Point> points) {
    public static final double CORRIDOR_METERS = 150;
    private static final double EARTH_METERS = 6371000;
    public record Point(double latitude, double longitude) {}

    public static ShuttleRoute parse(String text) {
        var points = new ArrayList<Point>();
        if (text == null || text.isBlank()) return new ShuttleRoute(List.of());
        if (text.length() > 12000) throw new IllegalArgumentException("Use at most 100 route points.");
        for (String line : text.strip().split("\\R")) {
            if (line.isBlank()) continue;
            String[] pair = line.trim().split("\\s*,\\s*");
            try {
                if (pair.length != 2) throw new NumberFormatException();
                double lat = Double.parseDouble(pair[0]), lon = Double.parseDouble(pair[1]);
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 85 || Math.abs(lon) > 180)
                    throw new NumberFormatException();
                points.add(new Point(lat, lon));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Enter one latitude,longitude pair per line.");
            }
        }
        if (points.size() < 2 || points.size() > 100 || points.stream().distinct().count() < 2)
            throw new IllegalArgumentException("Add 2–100 route points, with at least two different locations.");
        for (int i = 1; i < points.size(); i++) {
            if (distance(points.get(i-1).latitude, points.get(i-1).longitude,
                    points.get(i).latitude, points.get(i).longitude) > 10000)
                throw new IllegalArgumentException("Keep adjacent route points within 10 km; include points along turns.");
        }
        return new ShuttleRoute(List.copyOf(points));
    }
    public boolean configured() { return points.size() >= 2; }
    public String normalized() {
        return points.stream().map(p -> p.latitude + "," + p.longitude).collect(java.util.stream.Collectors.joining("\n"));
    }
    public boolean contains(double lat, double lon, double accuracy) {
        if (!configured()) return false;
        double scale = Math.cos(Math.toRadians(lat));
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i-1), b = points.get(i);
            double ax = Math.toRadians(a.longitude-lon)*EARTH_METERS*scale;
            double ay = Math.toRadians(a.latitude-lat)*EARTH_METERS;
            double bx = Math.toRadians(b.longitude-lon)*EARTH_METERS*scale;
            double by = Math.toRadians(b.latitude-lat)*EARTH_METERS;
            double dx = bx-ax, dy = by-ay, length = dx*dx+dy*dy;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(ax*dx+ay*dy)/length));
            if (Math.hypot(ax+t*dx, ay+t*dy) + accuracy <= CORRIDOR_METERS) return true;
        }
        return false;
    }
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);
        return 2*EARTH_METERS*Math.asin(Math.sqrt(Math.min(1,a)));
    }
}
