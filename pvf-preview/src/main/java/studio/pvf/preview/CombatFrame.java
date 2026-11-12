package studio.pvf.preview;

import java.util.List;

/** Gameplay attributes of one authored ANI frame. Coordinates are relative to the object origin. */
public record CombatFrame(DamageType damageType, List<Box> hurt, List<Box> attack) {
    static final CombatFrame NORMAL = new CombatFrame(DamageType.NORMAL, List.of(), List.of());

    public CombatFrame {
        hurt = List.copyOf(hurt);
        attack = List.copyOf(attack);
    }

    public enum DamageType { NORMAL, SUPERARMOR, UNBREAKABLE }

    /** Ground plane axes X and Y followed by elevation Z, all in ACT1 world units. */
    public record Box(double x, double y, double z, double width, double depth, double height) {
        Box scaled(double scale) {
            return scale == 1 ? this : new Box(x * scale, y * scale, z * scale,
                    width * scale, depth * scale, height * scale);
        }

        public Box mirrored() {
            return new Box(-x - width, y, z, width, depth, height);
        }
    }
}
