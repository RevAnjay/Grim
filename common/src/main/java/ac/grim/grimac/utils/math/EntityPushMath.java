package ac.grim.grimac.utils.math;

import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;

public final class EntityPushMath {
    // Vanilla Entity.push: normalizes by sqrt(absMax), not euclidean length.
    // Per-axis cap is 0.05, vector magnitude reaches 0.05*sqrt(2) on diagonal.
    public static final double PUSH_STRENGTH = 0.05000000074505806D; // 0.05f -> double
    public static final double MIN_THRESHOLD = 0.009999999776482582D; // 0.01f -> double

    private EntityPushMath() {}

    public static double absMax(double a, double b) {
        if (a < 0) a = -a;
        if (b < 0) b = -b;
        return Math.max(a, b);
    }

    public static double pushX(double selfX, double selfZ, double otherX, double otherZ) {
        double dx = otherX - selfX;
        double dz = otherZ - selfZ;
        double m = absMax(dx, dz);
        if (m < MIN_THRESHOLD) return 0;
        m = Math.sqrt(m);
        double falloff = Math.min(1.0 / m, 1.0);
        return -(dx / m) * falloff * PUSH_STRENGTH;
    }

    public static double pushZ(double selfX, double selfZ, double otherX, double otherZ) {
        double dx = otherX - selfX;
        double dz = otherZ - selfZ;
        double m = absMax(dx, dz);
        if (m < MIN_THRESHOLD) return 0;
        m = Math.sqrt(m);
        double falloff = Math.min(1.0 / m, 1.0);
        return -(dz / m) * falloff * PUSH_STRENGTH;
    }

    // Push is piecewise-continuous with seams at absMax=0.01 (threshold), absMax=1 (falloff clamp),
    // and |dx|=|dz| (absMax axis switch). Sample those explicitly alongside corners.
    public static void pushRange(double selfX, double selfZ,
                                  double entityMinX, double entityMaxX,
                                  double entityMinZ, double entityMaxZ,
                                  double[] out) {
        SeamAccumulator acc = new SeamAccumulator(selfX, selfZ);

        double midX = (entityMinX + entityMaxX) * 0.5;
        double midZ = (entityMinZ + entityMaxZ) * 0.5;

        acc.tryPoint(entityMinX, entityMinZ);
        acc.tryPoint(entityMaxX, entityMinZ);
        acc.tryPoint(entityMinX, entityMaxZ);
        acc.tryPoint(entityMaxX, entityMaxZ);
        acc.tryPoint(entityMinX, midZ);
        acc.tryPoint(entityMaxX, midZ);
        acc.tryPoint(midX, entityMinZ);
        acc.tryPoint(midX, entityMaxZ);
        acc.tryPoint(midX, midZ);

        acc.tryPointXClamp(entityMinX, entityMaxX, selfX, entityMinZ);
        acc.tryPointXClamp(entityMinX, entityMaxX, selfX, entityMaxZ);
        acc.tryPointZClamp(entityMinX, entityMinZ, entityMaxZ, selfZ);
        acc.tryPointZClamp(entityMaxX, entityMinZ, entityMaxZ, selfZ);
        acc.tryPoint(clamp(selfX, entityMinX, entityMaxX), clamp(selfZ, entityMinZ, entityMaxZ));

        // absMax=1 seam: x = selfX ± 1 and z = selfZ ± 1, clamped to bbox edges where they cross.
        for (double off : new double[]{-1.0, 1.0}) {
            double xCand = selfX + off;
            if (xCand >= entityMinX && xCand <= entityMaxX) {
                acc.tryPoint(xCand, entityMinZ);
                acc.tryPoint(xCand, entityMaxZ);
            }
            double zCand = selfZ + off;
            if (zCand >= entityMinZ && zCand <= entityMaxZ) {
                acc.tryPoint(entityMinX, zCand);
                acc.tryPoint(entityMaxX, zCand);
            }
        }

        // absMax=0.01 seam: just above/below threshold. These give large push magnitude per tiny |d|.
        for (double off : new double[]{-MIN_THRESHOLD, MIN_THRESHOLD}) {
            double xCand = selfX + off;
            if (xCand >= entityMinX && xCand <= entityMaxX) {
                acc.tryPoint(xCand, entityMinZ);
                acc.tryPoint(xCand, entityMaxZ);
            }
            double zCand = selfZ + off;
            if (zCand >= entityMinZ && zCand <= entityMaxZ) {
                acc.tryPoint(entityMinX, zCand);
                acc.tryPoint(entityMaxX, zCand);
            }
        }

        // |dx|=|dz| diagonal seam: intersections of x-selfX = ±(z-selfZ) with bbox edges.
        for (double zFixed : new double[]{entityMinZ, entityMaxZ}) {
            double dz = zFixed - selfZ;
            double xA = selfX + dz;
            double xB = selfX - dz;
            if (xA >= entityMinX && xA <= entityMaxX) acc.tryPoint(xA, zFixed);
            if (xB >= entityMinX && xB <= entityMaxX) acc.tryPoint(xB, zFixed);
        }
        for (double xFixed : new double[]{entityMinX, entityMaxX}) {
            double dx = xFixed - selfX;
            double zA = selfZ + dx;
            double zB = selfZ - dx;
            if (zA >= entityMinZ && zA <= entityMaxZ) acc.tryPoint(xFixed, zA);
            if (zB >= entityMinZ && zB <= entityMaxZ) acc.tryPoint(xFixed, zB);
        }

        // Zero push (absMax < 0.01 region) always reachable if bbox contains a neighborhood of self.
        double minPushX = Math.min(acc.minPushX, 0);
        double maxPushX = Math.max(acc.maxPushX, 0);
        double minPushZ = Math.min(acc.minPushZ, 0);
        double maxPushZ = Math.max(acc.maxPushZ, 0);

        out[0] = minPushX;
        out[1] = maxPushX;
        out[2] = minPushZ;
        out[3] = maxPushZ;
    }

    private static final class SeamAccumulator {
        final double selfX, selfZ;
        double minPushX = Double.POSITIVE_INFINITY;
        double maxPushX = Double.NEGATIVE_INFINITY;
        double minPushZ = Double.POSITIVE_INFINITY;
        double maxPushZ = Double.NEGATIVE_INFINITY;

        SeamAccumulator(double selfX, double selfZ) {
            this.selfX = selfX;
            this.selfZ = selfZ;
        }

        void tryPoint(double x, double z) {
            double dx = x - selfX;
            double dz = z - selfZ;
            double m = absMax(dx, dz);
            double px, pz;
            if (m < MIN_THRESHOLD) {
                px = 0;
                pz = 0;
            } else {
                m = Math.sqrt(m);
                double falloff = Math.min(1.0 / m, 1.0);
                double scale = falloff * PUSH_STRENGTH / m;
                px = -dx * scale;
                pz = -dz * scale;
            }
            if (px < minPushX) minPushX = px;
            if (px > maxPushX) maxPushX = px;
            if (pz < minPushZ) minPushZ = pz;
            if (pz > maxPushZ) maxPushZ = pz;
        }

        void tryPointXClamp(double minX, double maxX, double x, double z) {
            tryPoint(clamp(x, minX, maxX), z);
        }

        void tryPointZClamp(double x, double minZ, double maxZ, double z) {
            tryPoint(x, clamp(z, minZ, maxZ));
        }
    }

    public static double centerX(SimpleCollisionBox box) {
        return (box.minX + box.maxX) * 0.5;
    }

    public static double centerZ(SimpleCollisionBox box) {
        return (box.minZ + box.maxZ) * 0.5;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
