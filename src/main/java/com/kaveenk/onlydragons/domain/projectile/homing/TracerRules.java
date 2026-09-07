package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.*;
import java.util.function.Predicate;

/** OnlyDragons calibration, not a reconstruction of an upstream steering algorithm. */
public final class TracerRules {
    /**
     * Legacy calibration identity for callers using the non-profile acquisition overload.
     */
    public static final String REVISION = "tracer-continuity/v1";
    /**
     * Legacy six-degree steering bound in radians; explicit profiles supply their own bound.
     */
    public static final double TURN_RADIANS = Math.toRadians(6);
    /**
     * Finite axis-aligned part bounds; zero extent is allowed.
     * @param min nonnull minimum world-block corner
     * @param max nonnull maximum corner, componentwise no lower than min
     */
    public record Box(Vector3 min, Vector3 max) {
        /**
         * Rejects null/inverted bounds. Coordinate finiteness is enforced by Vector3.
         */
        public Box {
            Objects.requireNonNull(min); Objects.requireNonNull(max);
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z())
                throw new IllegalArgumentException("Inverted box");
        }
        /**
         * Tests componentwise half-open bounds: minimum inclusive, maximum exclusive. A zero-width
         * axis contains no point; this differs deliberately from closed nearest-point clamping.
         */
        public boolean contains(Vector3 p) {
            return p.x() >= min.x() && p.x() < max.x() && p.y() >= min.y() && p.y() < max.y() && p.z() >= min.z() && p.z() < max.z();
        }
        /**
         * Returns a new point clamped to the closed box, including its maximum faces. An interior
         * position returns the same coordinates; the argument itself is never mutated.
         */
        public Vector3 nearest(Vector3 p) {
            return new Vector3(clamp(p.x(), min.x(), max.x()), clamp(p.y(), min.y(), max.y()), clamp(p.z(), min.z(), max.z()));
        }
    }
    /**
     * One current eligible real part projected by the adapter; no native entity reference is retained.
     * @param targetId nonnull managed parent UUID
     * @param partId nonnull real part UUID, used for deterministic ties
     * @param box nonnull current part bounds in the arrow's world
     */
    public record Part(UUID targetId, UUID partId, Box box) {
        /**
         * Requires complete part identity/geometry; adapter owns phase, generation, world and arena eligibility.
         */
        public Part { Objects.requireNonNull(targetId); Objects.requireNonNull(partId); Objects.requireNonNull(box); }
    }
    /**
     * Pure nearest-point proposal, never proof of collision. Public construction adds no validation.
     * @param part selected current part
     * @param point nearest closed-box point in world blocks
     * @param distance Euclidean arrow-to-point distance in blocks
     */
    public record Aim(Part part, Vector3 point, double distance) {}
    private TracerRules() {}
    /**
     * Returns legacy level×2 blocks for 0–5; invalid levels reject and zero means no guidance.
     */
    public static double radius(int level) {
        if (level < 0 || level > 5) throw new IllegalArgumentException("Tracer level must be 0..5");
        return level * 2.0;
    }
    /**
     * Inclusive radius, no cone. Recompute every tick, so removal/range/obstruction release naturally.
     * <p>
     * Uses legacy v1 radii with no retained target or launch cone. Returns empty at level zero
     * without testing geometry; otherwise chooses nearest visible eligible part with UUID string ties.
     */
    public static Optional<Aim> acquire(Vector3 position, int level, Collection<Part> parts, Predicate<Aim> visible) {
        double radius = radius(level);
        if (level == 0) return Optional.empty();
        return acquireWithin(position, radius, parts, visible);
    }
    /**
     * Retain only an eligible visible current part of the same target; otherwise reacquire honestly.
     * <p>
     * Supports only profiles without a captured-launch cone; AIMED_V3 throws to prevent accidental
     * ungated guidance. Same-target retention is tried first, then honest acquisition at ordinary radius.
     */
    public static Optional<Aim> acquire(Vector3 position, int level, Collection<Part> parts, Predicate<Aim> visible,
                                        TracerProfile profile, Optional<UUID> lock) {
        if (profile.requiresLaunchAim()) throw new IllegalArgumentException("Captured launch aim required");
        return acquireEligible(position, level, parts, visible, profile, lock);
    }
    /**
     * Range is current arrow-to-part distance; the cone always originates at the captured launch.
     * <p>
     * Applies the launch cone to both retention and reacquisition for v3, before visibility.
     * Range uses the arrow's current position; cone displacement uses the captured launch position
     * and the nearest real part-box aim point. The caller supplies only current eligible parts.
     * @param position current arrow position in blocks
     * @param level Tracer 0–5
     * @param parts current real geometry; iteration order does not select ties
     * @param visible caller-owned obstruction predicate, which may be evaluated for multiple candidates
     * @param profile captured immutable steering rules
     * @param lock nonnull optional current target UUID, not a part lock
     * @param launchPosition nonnull captured group launch position
     * @param initialVelocity nonnull captured initial direction/velocity
     * @return nearest eligible aim, or empty without inventing a replacement target
     */
    public static Optional<Aim> acquire(Vector3 position, int level, Collection<Part> parts, Predicate<Aim> visible,
                                        TracerProfile profile, Optional<UUID> lock,
                                        Vector3 launchPosition, Vector3 initialVelocity) {
        Objects.requireNonNull(launchPosition); Objects.requireNonNull(initialVelocity);
        Predicate<Aim> eligible = profile.requiresLaunchAim()
                ? aim -> withinLaunchCone(initialVelocity, subtract(aim.point(), launchPosition), profile.aimHalfAngleRadians())
                        && visible.test(aim)
                : visible;
        return acquireEligible(position, level, parts, eligible, profile, lock);
    }
    /**
     * Tries same-target extended retention first when enabled, then normal acquisition. Both paths
     * use the same eligibility predicate; a stale lock cannot bypass cone/obstruction checks.
     */
    private static Optional<Aim> acquireEligible(Vector3 position, int level, Collection<Part> parts, Predicate<Aim> eligible,
                                                TracerProfile profile, Optional<UUID> lock) {
        double radius = profile.radius(level);
        if (level == 0) return Optional.empty();
        if (profile.retentionRadius(level) > radius && lock.isPresent()) {
            var retained = acquireWithin(position, profile.retentionRadius(level),
                    parts.stream().filter(p -> p.targetId().equals(lock.get())).toList(), eligible);
            if (retained.isPresent()) return retained;
        }
        return acquireWithin(position, radius, parts, eligible);
    }
    /**
     * Inclusive angle. Zero launch/aim vectors cannot authorize guidance; non-finite magnitudes reject.
     * <p>
     * Normalizes both vectors and uses an inclusive cosine threshold with four ulps of boundary
     * tolerance. Non-finite magnitudes/angles reject; either zero vector returns false.
     * @param initialVelocity finite captured launch velocity
     * @param launchToAim finite displacement from launch to the current proposed real aim point
     * @param halfAngle finite radians in [0,π]
     * @return whether launch aim permits guidance; no vector or entity mutation
     */
    public static boolean withinLaunchCone(Vector3 initialVelocity, Vector3 launchToAim, double halfAngle) {
        if (!Double.isFinite(halfAngle) || halfAngle < 0 || halfAngle > Math.PI)
            throw new IllegalArgumentException("Invalid cone angle");
        double speed = length(initialVelocity), distance = length(launchToAim);
        if (!Double.isFinite(speed) || !Double.isFinite(distance)) throw new IllegalArgumentException("Vector magnitude overflow");
        if (speed == 0 || distance == 0) return false;
        double boundary = Math.cos(halfAngle);
        // Normalizing arbitrary axes can round an exact boundary dot downward by a few ulps.
        return dot(divide(initialVelocity, speed), divide(launchToAim, distance)) >= boundary - 4 * Math.ulp(boundary);
    }
    /**
     * Computes closed-box nearest points, filters inclusive radius, sorts by distance then target
     * UUID string then part UUID string, and chooses the first predicate-approved candidate.
     * Overflowing vector subtraction rejects instead of silently authorizing distant guidance.
     */
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
    /**
     * Rotate on a great circle, preserving speed; antiparallel vectors use a stable perpendicular.
     * <p>
     * Returns original velocity for zero speed/displacement/turn; otherwise turns at most maxTurn
     * toward displacement and renormalizes to the original speed. Near antiparallel directions use
     * a deterministic least-aligned coordinate axis. Does not model native drag/gravity or collision.
     * @param velocity finite current blocks/tick vector
     * @param displacement finite arrow-to-aim displacement in blocks
     * @param maxTurn finite radians in [0,π]
     * @return bounded-turn velocity with preserved magnitude within floating precision
     * @throws IllegalArgumentException for invalid angle or non-finite derived magnitudes
     */
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
    /**
     * Returns nested-hypot Euclidean magnitude; finite components can still yield infinity when
     * the true magnitude exceeds double range, so guidance callers explicitly check the result.
     */
    public static double length(Vector3 v) { return Math.hypot(Math.hypot(v.x(), v.y()), v.z()); }
    /**
     * Returns componentwise a−b; overflowing components reject through Vector3 validation.
     */
    public static Vector3 subtract(Vector3 a, Vector3 b) { return new Vector3(a.x()-b.x(), a.y()-b.y(), a.z()-b.z()); }
    /**
     * Returns componentwise sum for steering; Vector3 rejects non-finite results.
     */
    private static Vector3 add(Vector3 a, Vector3 b) { return new Vector3(a.x()+b.x(), a.y()+b.y(), a.z()+b.z()); }
    /**
     * Scales components by a caller-validated factor, rejecting non-finite resulting coordinates.
     */
    private static Vector3 scale(Vector3 v, double factor) { return new Vector3(v.x()*factor, v.y()*factor, v.z()*factor); }
    /**
     * Normalizes by a previously checked nonzero magnitude; invalid coordinates reject at construction.
     */
    private static Vector3 divide(Vector3 v, double divisor) { return new Vector3(v.x()/divisor, v.y()/divisor, v.z()/divisor); }
    /**
     * Computes the scalar product of normalized steering vectors; callers clamp angular roundoff.
     */
    private static double dot(Vector3 a, Vector3 b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    /**
     * Clamps finite caller-validated values to inclusive bounds; does not independently reject NaN.
     */
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
