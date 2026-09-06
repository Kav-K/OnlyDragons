package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.*;
import java.util.function.Predicate;

/** OnlyDragons calibration, not a reconstruction of an upstream steering algorithm. */
public final class TracerRules {
    public static final String REVISION = "tracer-continuity/v1";
    public static final double TURN_RADIANS = Math.toRadians(6);
    public record Box(Vector3 min, Vector3 max) {
        public Box {
            Objects.requireNonNull(min); Objects.requireNonNull(max);
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z())
                throw new IllegalArgumentException("Inverted box");
        }
        public boolean contains(Vector3 p) {
            return p.x() >= min.x() && p.x() < max.x() && p.y() >= min.y() && p.y() < max.y() && p.z() >= min.z() && p.z() < max.z();
        }
        public Vector3 nearest(Vector3 p) {
            return new Vector3(clamp(p.x(), min.x(), max.x()), clamp(p.y(), min.y(), max.y()), clamp(p.z(), min.z(), max.z()));
        }
    }
    public record Part(UUID targetId, UUID partId, Box box) {
        public Part { Objects.requireNonNull(targetId); Objects.requireNonNull(partId); Objects.requireNonNull(box); }
    }
    public record Aim(Part part, Vector3 point, double distance) {}
    private TracerRules() {}
    public static double radius(int level) {
        if (level < 0 || level > 5) throw new IllegalArgumentException("Tracer level must be 0..5");
        return level * 2.0;
    }
    /** Inclusive radius, no cone. Recompute every tick, so removal/range/obstruction release naturally. */
    public static Optional<Aim> acquire(Vector3 position, int level, Collection<Part> parts, Predicate<Aim> visible) {
        double radius = radius(level);
        if (level == 0) return Optional.empty();
        return acquireWithin(position, radius, parts, visible);
    }
    /** Retain only an eligible visible current part of the same target; otherwise reacquire honestly. */
    public static Optional<Aim> acquire(Vector3 position, int level, Collection<Part> parts, Predicate<Aim> visible,
                                        TracerProfile profile, Optional<UUID> lock) {
        double radius = profile.radius(level);
        if (level == 0) return Optional.empty();
        if (profile == TracerProfile.RETURN_V2 && lock.isPresent()) {
            var retained = acquireWithin(position, profile.retentionRadius(level),
                    parts.stream().filter(p -> p.targetId().equals(lock.get())).toList(), visible);
            if (retained.isPresent()) return retained;
        }
        return acquireWithin(position, radius, parts, visible);
    }
    private static Optional<Aim> acquireWithin(Vector3 position, double radius, Collection<Part> parts, Predicate<Aim> visible) {
        return parts.stream().map(part -> {
            Vector3 point = part.box().nearest(position);
            return new Aim(part, point, length(subtract(point, position)));
        }).filter(aim -> aim.distance() <= radius)
                .sorted(Comparator.comparingDouble(Aim::distance)
                        .thenComparing(a -> a.part().targetId().toString())
                        .thenComparing(a -> a.part().partId().toString()))
                .filter(visible).findFirst();
    }
    /** Rotate on a great circle, preserving speed; antiparallel vectors use a stable perpendicular. */
    public static Vector3 steer(Vector3 velocity, Vector3 displacement, double maxTurn) {
        if (!Double.isFinite(maxTurn) || maxTurn < 0 || maxTurn > Math.PI) throw new IllegalArgumentException("Invalid turn");
        double speed = length(velocity), distance = length(displacement);
        if (!Double.isFinite(speed) || !Double.isFinite(distance)) throw new IllegalArgumentException("Vector magnitude overflow");
        if (speed == 0 || distance == 0 || maxTurn == 0) return velocity;
        Vector3 from = divide(velocity, speed), to = divide(displacement, distance);
        double dot = clamp(dot(from, to), -1, 1), angle = Math.acos(dot);
        if (angle <= maxTurn) return scale(to, speed);
        Vector3 tangent = subtract(to, scale(from, dot));
        double tangentLength = length(tangent);
        if (tangentLength < 1e-12) {
            Vector3 axis = Math.abs(from.x()) <= Math.abs(from.y()) && Math.abs(from.x()) <= Math.abs(from.z())
                    ? new Vector3(1, 0, 0) : Math.abs(from.y()) <= Math.abs(from.z()) ? new Vector3(0, 1, 0) : new Vector3(0, 0, 1);
            tangent = subtract(axis, scale(from, dot(from, axis))); tangentLength = length(tangent);
        }
        Vector3 turned = add(scale(from, Math.cos(maxTurn)), scale(tangent, Math.sin(maxTurn) / tangentLength));
        return scale(turned, speed / length(turned));
    }
    public static double length(Vector3 v) { return Math.hypot(Math.hypot(v.x(), v.y()), v.z()); }
    public static Vector3 subtract(Vector3 a, Vector3 b) { return new Vector3(a.x()-b.x(), a.y()-b.y(), a.z()-b.z()); }
    private static Vector3 add(Vector3 a, Vector3 b) { return new Vector3(a.x()+b.x(), a.y()+b.y(), a.z()+b.z()); }
    private static Vector3 scale(Vector3 v, double factor) { return new Vector3(v.x()*factor, v.y()*factor, v.z()*factor); }
    private static Vector3 divide(Vector3 v, double divisor) { return new Vector3(v.x()/divisor, v.y()/divisor, v.z()/divisor); }
    private static double dot(Vector3 a, Vector3 b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
